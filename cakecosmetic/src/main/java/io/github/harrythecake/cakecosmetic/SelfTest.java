package io.github.harrythecake.cakecosmetic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	private SelfTest() {
	}

	static void init() {
		String mode = System.getProperty("cakecosmetic.selftest", "");
		if (mode.isEmpty()) {
			return;
		}
		world = mode.equals("world");
		CakeCosmetic.LOG.info("CakeCosmetic self-test ({})", world ? "world" : "title");
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			String kind = screen.getClass().getSimpleName();
			if (!worldStarted || screen instanceof TitleScreen || screen instanceof GalleryScreen || screen instanceof RuleEditorScreen) {
				return;
			}
			int[] frames = {0};
			ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
				// the loading screens render without ticking: shoot from their own frames
				if (++frames[0] == 20 && step <= 5 && SHOT_KINDS.add(kind)) {
					ctx.draw();
					shot(client, "loading-" + kind);
				}
			});
		});
	}

	private static void shot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "cakecosmetic-selftest-" + name + ".png", client.getFramebuffer(),
				text -> CakeCosmetic.LOG.info("CakeCosmetic self-test: {}", text.getString()));
	}

	private static void tick(MinecraftClient client) {
		if (client.getOverlay() != null) {
			return;
		}
		ticks++;
		if (!worldStarted) {
			titleSteps(client);
		} else if (client.world != null && client.player != null) {
			worldSteps(client);
		}
	}

	private static void titleSteps(MinecraftClient client) {
		List<Backgrounds.Background> all = Backgrounds.list();
		Screen s = client.currentScreen;
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

	private static void worldSteps(MinecraftClient client) {
		switch (step) {
			case 5 -> {
				if (ticks > 60) {
					next();
					var server = client.getServer();
					var id = client.player.getUuid();
					if (server != null) {
						server.execute(() -> {
							var p = server.getPlayerManager().getPlayer(id);
							if (p != null) {
								p.getInventory().insertStack(new ItemStack(Items.CRAFTING_TABLE));
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
						client.setScreen(new RuleEditorScreen(client.currentScreen, all.get(all.size() - 1)));
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

	private static void openWorld(MinecraftClient client) {
		var loader = client.createIntegratedServerLoader();
		if (client.getLevelStorage().levelExists(WORLD)) {
			loader.start(WORLD, () -> client.setScreen(new TitleScreen()));
		} else {
			loader.createAndStart(WORLD, MinecraftServer.DEMO_LEVEL_INFO, GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, new TitleScreen());
		}
	}

	private static void done(MinecraftClient client) {
		CakeCosmetic.LOG.info("CakeCosmetic self-test: done; backgrounds {}, pickable {}, rules {}",
				Backgrounds.list().size(), Unlocks.pickableBackgrounds(), Unlocks.rules().all().keySet());
		client.scheduleStop();
	}
}
