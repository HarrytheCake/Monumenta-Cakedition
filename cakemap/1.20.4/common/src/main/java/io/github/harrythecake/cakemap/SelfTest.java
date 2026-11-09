package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Path;

/**
 * Development only, off unless the JVM runs with -Dcakemap.selftest:
 * <ul>
 * <li>{@code =world}: a test world; screenshots of the minimap (square, circle, rotating, cave),
 * the world map, waypoint list and editor, settings, a chat line with coordinates and a compass
 * target; then it quits.</li>
 * <li>{@code =export}: at the title screen, imports the Xaero world maps under -Dcakemap.import
 * (an xaero folder) for the server -Dcakemap.export (default monumenta) and exports that server's
 * static-map pack to cakemap-export/; then it quits.</li>
 * </ul>
 * Screenshots go to run/screenshots.
 */
final class SelfTest {
	private static final String WORLD = "cakemap-selftest";
	private static String mode = "";
	private static int step;
	private static int ticks;
	private static boolean worldStarted;
	private static Thread job;
	private static XaeroImport.Progress progress;
	private static Thread exportJob;

	private SelfTest() {
	}

	static void init() {
		mode = System.getProperty("cakemap.selftest", "");
		if (mode.isEmpty()) {
			return;
		}
		CakeMap.LOG.info("CakeMap self-test ({})", mode);
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, "cakemap-selftest-" + name + ".png", client.getMainRenderTarget(),
				text -> CakeMap.LOG.info("CakeMap self-test: {}", text.getString()));
	}

	static void tick(Minecraft client) {
		if (mode.isEmpty()) {
			return;
		}
		if (client.getOverlay() != null) {
			return;
		}
		ticks++;
		if (mode.equals("export")) {
			exportSteps(client);
			return;
		}
		if (!worldStarted) {
			if (client.screen instanceof TitleScreen && ticks > 60) {
				worldStarted = true;
				CakeMap.settings().devMode = !mode.equals("compat");
				openWorld(client);
				next();
			}
			return;
		}
		if (client.level != null && client.player != null) {
			if (mode.equals("compat")) {
				compatSteps(client);
			} else {
				worldSteps(client);
			}
		}
	}

	private static void exportSteps(Minecraft client) {
		String server = System.getProperty("cakemap.export", "").isEmpty() ? "monumenta" : System.getProperty("cakemap.export");
		switch (step) {
			case 0 -> {
				if (client.screen instanceof TitleScreen && ticks > 40) {
					next();
					String src = System.getProperty("cakemap.import", "");
					if (src.isEmpty()) {
						step = 2;
						return;
					}
					Path xaero = XaeroImport.xaeroDir(Path.of(src));
					progress = new XaeroImport.Progress();
					job = new Thread(() -> {
						try {
							int w = XaeroImport.importWaypoints(xaero.resolve("minimap"), xaero.resolve("world-map"), CakeMap.dataRoot(), Waypoint.BOTH, null)
									+ XaeroImport.importWaypoints(XaeroImport.gameDir(xaero).resolve(XaeroImport.LEGACY_WAYPOINTS), xaero.resolve("world-map"), CakeMap.dataRoot(),
											Waypoint.BOTH, null);
							CakeMap.LOG.info("CakeMap self-test: imported {} waypoints", w);
							XaeroImport.importMaps(xaero.resolve("world-map"), CakeMap.dataRoot(), progress, server);
						} catch (Exception e) {
							CakeMap.LOG.error("CakeMap self-test: import failed", e);
						}
						progress.finished = true;
					}, "CakeMap self-test import");
					job.start();
				}
			}
			case 1 -> {
				if (ticks % 200 == 0) {
					CakeMap.LOG.info("CakeMap self-test: import {}/{}", progress.done.get(), progress.total);
				}
				if (progress.finished) {
					CakeMap.LOG.info("CakeMap self-test: imported {} regions ({} failed)", progress.regions, progress.failed);
					next();
				}
			}
			case 2 -> {
				next();
				job = StaticExport.run(server, CakeMap.dataRoot(), Services.PLATFORM.gameDir().resolve("cakemap-export"),
						msg -> CakeMap.LOG.info("CakeMap self-test: {}", msg));
			}
			case 3 -> {
				if (!job.isAlive()) {
					next();
					CakeMap.LOG.info("CakeMap self-test: done");
					client.stop();
				}
			}
			default -> {
			}
		}
	}

	/** In a full modpack: the minimap, the world map and the waypoint list in a test world; nothing is changed or saved. */
	private static void compatSteps(Minecraft client) {
		switch (step) {
			case 1 -> {
				MapSession session = CakeMap.session();
				if (ticks == 250 && session != null && client.player != null) {
					// one icon of every kind (SVG drawing next to the whole pack), one tracked, one bookmarked
					int x = client.player.getBlockX();
					int y = client.player.getBlockY();
					int z = client.player.getBlockZ();
					WaypointKind[] kinds = WaypointKind.values();
					for (int i = 0; i < kinds.length; i++) {
						double a = i * Math.PI * 2 / kinds.length;
						Waypoint w = new Waypoint("Mock " + kinds[i].id(), kinds[i], session.dimension(), x + (int) (Math.cos(a) * 30), y,
								z + (int) (Math.sin(a) * 30));
						session.waypoints.add(w);
						if (i == 0) {
							session.waypoints.track(w);
						}
						if (i == 1) {
							session.waypoints.setBookmarked(w, true);
						}
					}
				}
				if (ticks > 300) {
					shot(client, "compat-hud");
					client.setScreen(new WorldMapScreen(null));
					next();
				}
			}
			case 2 -> {
				if (ticks > 80) {
					shot(client, "compat-worldmap");
					client.setScreen(new WaypointListScreen(null));
					next();
				}
			}
			case 3 -> {
				if (ticks > 30) {
					shot(client, "compat-waypoints");
					client.setScreen(null);
					MapSession session = CakeMap.session();
					CakeMap.LOG.info("CakeMap self-test (compat): done; regions known {}, waypoints {}", session == null ? -1 : session.store().knownRegions().size(),
							session == null ? -1 : session.waypoints.all().size());
					client.stop();
					next();
				}
			}
			default -> {
			}
		}
	}

	private static void worldSteps(Minecraft client) {
		MapSettings s = CakeMap.settings();
		MapSession session = CakeMap.session();
		if (session == null) {
			return;
		}
		switch (step) {
			case 1 -> {
				if (ticks > 200) {
					// a few waypoints around the player, one bookmarked far away (shown at the minimap's edge)
					int x = client.player.getBlockX();
					int y = client.player.getBlockY();
					int z = client.player.getBlockZ();
					String d = session.dimension();
					session.waypoints.add(new Waypoint("Mock Dungeon", WaypointKind.DUNGEON, d, x + 20, y, z - 15));
					session.waypoints.add(new Waypoint("Mock Town", WaypointKind.TOWN, d, x - 25, y, z + 10));
					Waypoint far = new Waypoint("Far Boss", WaypointKind.BOSS, d, x + 400, y, z + 300);
					session.waypoints.add(far);
					session.waypoints.setBookmarked(far, true);
					session.waypoints.add(new Waypoint("Charms", WaypointKind.CHARM_TRADER, d, x + 5, y, z + 25));
					session.waypoints.add(new Waypoint("Quest Giver", WaypointKind.QUEST, d, x - 10, y, z - 30));
					session.waypoints.add(new Waypoint("Main Quest Giver", WaypointKind.QUEST_MAIN, d, x - 30, y, z - 5));
					Waypoint red = new Waypoint("Red Pin", WaypointKind.PLAYER, d, x + 30, y, z + 5);
					red.color = io.github.harrythecake.cakemap.core.WaypointColors.PALETTE[1];
					session.waypoints.add(red);
					session.waypoints.add(new Waypoint("Plain Pin", WaypointKind.PLAYER, d, x + 12, y, z + 34));
					session.waypoints.add(new Waypoint("Trader", WaypointKind.TRADER, d, x - 18, y, z + 28));
					// a POI right where the compass target will be (step 4): the target must be drawn on top
					session.waypoints.add(new Waypoint("Mock POI", WaypointKind.POI, d, x + 60, y, z - 40));
					Waypoint tracked = new Waypoint("Tracked Dungeon", WaypointKind.DUNGEON, d, x - 200, y, z + 150);
					session.waypoints.add(tracked);
					session.waypoints.track(tracked);
					s.rotate = false;
					s.shape = MapSettings.Shape.SQUARE;
					next();
				}
			}
			case 2 -> {
				if (ticks > 60) {
					shot(client, "hud-square");
					s.shape = MapSettings.Shape.CIRCLE;
					s.rotate = true;
					s.size = 140;
					client.player.setYRot(35);
					next();
				}
			}
			case 3 -> {
				if (ticks == 31) {
					shot(client, "hud-circle-rotating");
					// a real death: the server kills the player, the death packet adds a death waypoint
					s.rotate = false;
					var server = client.getSingleplayerServer();
					if (server != null) {
						var id = client.player.getUUID();
						server.execute(() -> {
							var p = server.getPlayerList().getPlayer(id);
							if (p != null) {
								p.kill();
							}
						});
					}
				} else if (ticks == 80) {
					long deaths = session.waypoints.own().stream().filter(w -> w.kind() == WaypointKind.DEATH).count();
					CakeMap.LOG.info("CakeMap self-test: death waypoints after one death: {} ({})", deaths, deaths == 1 ? "PASS" : "FAIL");
					shot(client, "death-screen");
					client.player.respawn();
					client.setScreen(null);
				} else if (ticks == 160) {
					// respawned: still one death waypoint (once per life)
					long deaths = session.waypoints.own().stream().filter(w -> w.kind() == WaypointKind.DEATH).count();
					CakeMap.LOG.info("CakeMap self-test: death waypoints after respawn: {} ({})", deaths, deaths == 1 ? "PASS" : "FAIL");
					if (CakeMapPlugin.hudEditor) {
						// CakeUI 3+: the minimap is a movable; dropped near the bottom-right it takes that corner (then put back)
						MapSettings ms = CakeMap.settings();
						MapSettings.Corner c0 = ms.corner;
						int ox = ms.offsetX;
						int oy = ms.offsetY;
						int sz = ms.size;
						int sw = client.getWindow().getGuiScaledWidth();
						int sh = client.getWindow().getGuiScaledHeight();
						Minimap.MOVABLE.place(sw - 100 - 6, sh - 100 - (ms.coordinates ? 11 : 0) - 8, 100, 111, sw, sh);
						boolean ok = ms.corner == MapSettings.Corner.BOTTOM_RIGHT && ms.offsetX == 6 && ms.offsetY == 8 && ms.size == 100;
						boolean listed = false;
						try {
							java.lang.reflect.Method all = Class.forName("io.github.harrythecake.cakeui.HudMovables").getDeclaredMethod("all");
							all.setAccessible(true);
							listed = ((java.util.Map<?, ?>) all.invoke(null)).containsKey("~cakemap:minimap");
						} catch (ReflectiveOperationException e) {
							// stays false
						}
						CakeMap.LOG.info("CakeMap self-test: minimap movable listed {}, placed bottom-right {} {} {} ({})", listed, ms.corner, ms.offsetX, ms.offsetY,
								ok && listed ? "PASS" : "FAIL");
						ms.corner = c0;
						ms.offsetX = ox;
						ms.offsetY = oy;
						ms.size = sz;
					}
					session.waypoints.own().stream().filter(w -> w.kind() == WaypointKind.DEATH).findFirst()
							.ifPresent(w -> CakeMap.LOG.info("CakeMap self-test: {} at {} {} {} in {}", w.name, w.x, w.y, w.z, w.dimension));
					// a second (mock) death next to the respawn point, so the shot shows the icon close up
					Waypoint near = new Waypoint("Death (mock)", WaypointKind.DEATH, session.dimension(),
							client.player.getBlockX() + 15, client.player.getBlockY(), client.player.getBlockZ() + 15);
					session.waypoints.addDeath(near, io.github.harrythecake.cakemap.core.WaypointStore.DEATHS_KEPT);
				} else if (ticks == 200) {
					shot(client, "hud-circle-north");
					s.frame = MapSettings.Frame.LINE;
				} else if (ticks > 230) {
					shot(client, "hud-circle-thin");
					s.frame = MapSettings.Frame.RING;
					s.cave = MapSettings.Cave.ON;
					s.shape = MapSettings.Shape.SQUARE;
					s.rotate = false;
					s.size = 110;
					next();
				}
			}
			case 4 -> {
				if (ticks > 120) {
					shot(client, "hud-cave");
					s.cave = MapSettings.Cave.AUTO;
					var server = client.getSingleplayerServer();
					BlockPos target = client.player.blockPosition().offset(60, 0, -40);
					if (server != null) {
						var id = client.player.getUUID();
						server.execute(() -> {
							var p = server.getPlayerList().getPlayer(id);
							if (p != null) {
								p.sendSystemMessage(Component.literal("Mock_Pl4yer says: meet me at (" + (target.getX() - 30) + ", 70, " + (target.getZ() + 50) + ")"));
								p.connection.send(new ClientboundSetDefaultSpawnPositionPacket(target, 0));
							}
						});
					}
					next();
				}
			}
			case 5 -> {
				if (ticks > 40) {
					client.setScreen(new ChatScreen(""));
					next();
				}
			}
			case 6 -> {
				if (ticks > 20) {
					shot(client, "chat-and-compass");
					CakeMap.LOG.info("CakeMap self-test: temporary waypoints {}", session.waypoints.temporary().size());
					// what a click on the chat [+] does: the command goes through the click path (on NeoForge 1.20.4 only
					// CakeMap's own mixin keeps it from the server); the client command must add a waypoint
					int before = session.waypoints.temporary().size();
					String cmd = "cakemap temp 7 70 7 Click test";
					client.player.connection.sendUnsignedCommand(cmd);
					int added = session.waypoints.temporary().size() - before;
					CakeMap.LOG.info("CakeMap self-test: chat [+] click runs /cakemap temp on the client: {} added ({})", added, added == 1 ? "PASS" : "FAIL");
					client.setScreen(new WorldMapScreen(null));
					next();
				}
			}
			case 7 -> {
				if (ticks > 80) {
					shot(client, "worldmap");
					if (client.screen instanceof WorldMapScreen w) {
						w.view().scale = 2;
						session.waypoints.all().stream().filter(q -> q.name.equals("Quest Giver")).findFirst().ifPresent(w::openIconMenuForTest);
					}
					next();
				}
			}
			case 8 -> {
				if (ticks > 20) {
					shot(client, "worldmap-popup");
					if (client.screen instanceof WorldMapScreen w) {
						session.waypoints.all().stream().filter(q -> q.name.equals("Quest Giver")).findFirst().ifPresent(w::nearestTownForTest);
					}
					next();
				}
			}
			case 9 -> {
				if (ticks > 10) {
					shot(client, "worldmap-nearest-town");
					if (client.screen instanceof WorldMapScreen w) {
						w.view().scale = 0.25;
					}
					next();
				}
			}
			case 10 -> {
				if (ticks > 60) {
					shot(client, "worldmap-zoomed-out");
					client.setScreen(new WaypointListScreen(null));
					next();
				}
			}
			case 11 -> {
				if (ticks > 20) {
					shot(client, "waypoints");
					client.setScreen(new WaypointEditScreen(null, session.waypoints.own().stream().filter(q -> q.name.equals("Red Pin")).findFirst()
							.orElse(session.waypoints.own().get(0)), false));
					next();
				}
			}
			case 12 -> {
				if (ticks > 20) {
					shot(client, "edit");
					client.setScreen(new SettingsScreen(null));
					next();
				}
			}
			case 13 -> {
				if (ticks > 20) {
					shot(client, "settings");
					client.setScreen(new WaypointListScreen.KindsScreen(null));
					next();
				}
			}
			case 14 -> {
				if (ticks > 20) {
					shot(client, "kinds");
					client.setScreen(null);
					client.player.setXRot(10);
					next();
				}
			}
			case 15 -> {
				if (ticks > 30) {
					shot(client, "in-world");
					next();
				}
			}
			case 16 -> {
				if (ticks > 10) {
					CakeMap.LOG.info("CakeMap self-test: dimension {}, waypoints {}, regions known {}", session.dimension(),
							session.waypoints.all().size(), session.store().knownRegions().size());
					// the static pipeline end to end: export this world, load it back as a resource pack
					session.store().tick(0, 0, true);
					exportJob = StaticExport.run(session.server, CakeMap.dataRoot(), client.gameDirectory.toPath().resolve("cakemap-export"),
							msg -> CakeMap.LOG.info("CakeMap self-test: {}", msg));
					next();
				}
			}
			case 17 -> {
				if (!exportJob.isAlive()) {
					try {
						// a new name each run: last run's pack is still open while it is enabled
						String exported = "CakeMap-static-" + session.server + ".zip";
						String zip = "CakeMap-static-selftest-" + System.currentTimeMillis() + ".zip";
						java.nio.file.Path packs = client.gameDirectory.toPath().resolve("resourcepacks");
						java.nio.file.Files.createDirectories(packs);
						java.nio.file.Files.copy(client.gameDirectory.toPath().resolve("cakemap-export").resolve(exported), packs.resolve(zip));
						var rpm = client.getResourcePackRepository();
						rpm.reload();
						java.util.List<String> on = new java.util.ArrayList<>(rpm.getSelectedIds());
						on.removeIf(n -> n.startsWith("file/CakeMap-static-"));
						on.add("file/" + zip);
						rpm.setSelected(on);
						client.options.updateResourcePacks(rpm);
					} catch (java.io.IOException e) {
						CakeMap.LOG.error("CakeMap self-test: can't install the exported pack", e);
					}
					next();
				}
			}
			case 18 -> {
				if (ticks > 100) {
					StaticMaps.Shard shard = StaticMaps.shard(session.server, session.dimension());
					CakeMap.LOG.info("CakeMap self-test: static shard {} ({} tiles, {} samples), revealed {}, match {}/{} → shown {}",
							shard == null ? "MISSING" : shard.dimension(), shard == null ? 0 : shard.tiles().size(), shard == null ? 0 : shard.samples().size(),
							session.revealed(session.dimension()), session.check(session.dimension()).agree(), session.check(session.dimension()).total(),
							session.showStatic(session.dimension()));
					s.mode = MapSettings.Mode.STATIC;
					client.setScreen(new WorldMapScreen(null));
					next();
				}
			}
			case 19 -> {
				if (ticks > 80) {
					shot(client, "worldmap-static");
					client.setScreen(null);
					next();
				}
			}
			case 20 -> {
				if (ticks > 10) {
					CakeMap.LOG.info("CakeMap self-test: done");
					client.stop();
					next();
				}
			}
			default -> {
			}
		}
	}

	private static void next() {
		step++;
		ticks = 0;
	}

	private static void openWorld(Minecraft client) {
		var loader = client.createWorldOpenFlows();
		if (client.getLevelSource().levelExists(WORLD)) {
			loader.checkForBackupAndLoad(WORLD, () -> client.setScreen(new TitleScreen()));
		} else {
			loader.createFreshLevel(WORLD, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions, new TitleScreen());
		}
	}
}
