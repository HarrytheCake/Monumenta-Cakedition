package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Development only, off unless the JVM runs with -Dcakecosmetic.selftest (=1: title screen, gallery,
 * preview, rule editor; =world: then a test world, its loading screens, an unlock by advancement
 * (a crafting table earns minecraft:story/root), the toast, the gallery and the rule editor with
 * the world's advancements). Screenshots go to run/screenshots; then it quits.
 */
final class SelfTest {
	private static final String WORLD = "cakecosmetic-selftest";
	private static boolean world;
	private static int step;
	private static int ticks;
	private static boolean worldStarted;
	private static final Set<String> SHOT_KINDS = new HashSet<>();
	private static final Map<Screen, int[]> FRAMES = new WeakHashMap<>();
	private static boolean running;

	private SelfTest() {
	}

	static void init() {
		String mode = System.getProperty("cakecosmetic.selftest", "");
		if (mode.isEmpty()) {
			return;
		}
		running = true;
		world = mode.equals("world");
		CakeCosmetic.LOG.info("CakeCosmetic self-test ({})", world ? "world" : "title");
	}

	static boolean running() {
		return running;
	}

	/** The loading screens of the world part: counted from their own frames. */
	static void afterScreenInit(Screen screen) {
		if (!running || !worldStarted || screen instanceof TitleScreen || screen instanceof GalleryScreen || screen instanceof RuleEditorScreen) {
			return;
		}
		FRAMES.put(screen, new int[] {0});
	}

	static void afterScreenRender(Screen screen, GuiGraphics ctx) {
		int[] frames = FRAMES.get(screen);
		if (frames == null) {
			return;
		}
		String kind = screen.getClass().getSimpleName();
		// the loading screens render without ticking: shoot from their own frames
		if (++frames[0] == 20 && step <= 5 && SHOT_KINDS.add(kind)) {
			ctx.flush();
			shot(Minecraft.getInstance(), "loading-" + kind);
		}
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, "cakecosmetic-selftest-" + name + ".png", client.getMainRenderTarget(),
				text -> CakeCosmetic.LOG.info("CakeCosmetic self-test: {}", text.getString()));
	}

	static void tick(Minecraft client) {
		if (!running) {
			return;
		}
		if (client.getOverlay() != null) {
			return;
		}
		ticks++;
		if (!worldStarted) {
			titleSteps(client);
		} else if (client.level != null && client.player != null) {
			worldSteps(client);
		}
	}

	private static void titleSteps(Minecraft client) {
		List<Backgrounds.Background> all = Backgrounds.list();
		Screen s = client.screen;
		switch (step) {
			case 0 -> {
				if (s instanceof TitleScreen && ticks > 100) {
					shot(client, "title");
					next();
					client.setScreen(GalleryScreen.open(s));
				}
			}
			case 1 -> {
				if (ticks > 60) {
					shot(client, "gallery");
					next();
					if (!all.isEmpty()) {
						client.setScreen(new PreviewScreen(s, all.get(0)));
					}
				}
			}
			case 2 -> {
				if (ticks > 60) {
					shot(client, "preview");
					next();
					if (!all.isEmpty()) {
						client.setScreen(new RuleEditorScreen(GalleryScreen.open(new TitleScreen()), all.get(all.size() - 1)));
					}
				}
			}
			case 3 -> {
				if (ticks > 30) {
					shot(client, "rules-offline");
					next();
					client.setScreen(new TitleScreen());
				}
			}
			case 4 -> {
				if (ticks > 60) {
					shot(client, "title-again");
					next();
					if (world) {
						worldStarted = true;
						openWorld(client);
					} else {
						done(client);
					}
				}
			}
			default -> {
			}
		}
	}

	private static void worldSteps(Minecraft client) {
		switch (step) {
			case 5 -> {
				if (ticks > 60) {
					next();
					var server = client.getSingleplayerServer();
					var id = client.player.getUUID();
					if (server != null) {
						server.execute(() -> {
							var p = server.getPlayerList().getPlayer(id);
							if (p != null) {
								p.getInventory().add(new ItemStack(Items.CRAFTING_TABLE));
							}
						});
					}
				}
			}
			case 6 -> {
				if (ticks == 40) {
					shot(client, "toast");
				}
				if (ticks > 60) {
					next();
					client.setScreen(GalleryScreen.open(null));
				}
			}
			case 7 -> {
				if (ticks > 60) {
					shot(client, "gallery-world");
					next();
					List<Backgrounds.Background> all = Backgrounds.list();
					if (!all.isEmpty()) {
						client.setScreen(new RuleEditorScreen(client.screen, all.get(all.size() - 1)));
					}
				}
			}
			case 8 -> {
				if (ticks > 30) {
					shot(client, "rules-world");
					next();
					client.setScreen(null);
				}
			}
			case 9 -> {
				if (ticks > 20) {
					done(client);
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

	private static void done(Minecraft client) {
		CakeCosmetic.LOG.info("CakeCosmetic self-test: done; backgrounds {}, pickable {}, rules {}",
				Backgrounds.list().size(), Unlocks.pickableBackgrounds(), Unlocks.rules().all().keySet());
		client.stop();
	}
}
