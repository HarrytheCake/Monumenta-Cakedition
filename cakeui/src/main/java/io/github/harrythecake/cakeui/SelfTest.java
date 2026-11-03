package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.mixin.MouseInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;

/**
 * Development only, off unless the JVM runs with -Dcakeui.selftest (Loom's runClient passes it on
 * request). On the title screen it takes screenshots with the mouse in a few places. With
 * -Dcakeui.selftest=world it then opens a test world ("cakeui-selftest" in run/saves) and takes
 * more of the loading screens on the way (one per kind), the pause menu and the inventory. Then it quits.
 */
final class SelfTest {
	/** {seconds, mouse x, mouse y as fractions of the window, or -1 to leave it}. */
	private static final double[][] TITLE = {
		{4.0, 0.5, 0.5},
		{6.0, 0.62, 0.32},
		{8.0, 0.1, 0.9},
	};
	private static final double[][] PAUSE = {
		{3.0, 0.9, 0.9},
		{5.0, 0.5, 0.31},
	};
	/** The second shot points at the first hotbar slot (a sword: its tooltip must cover the layout). */
	private static final double[][] INVENTORY = {
		{3.0, 0.9, 0.2},
		{5.0, 0.33, 0.78},
	};
	private static final String WORLD = "cakeui-selftest";
	private static boolean worldMode;
	private static boolean enabled;
	private static boolean reloaded;
	private static int launchFrames;
	private static boolean launchReloading;
	private static final java.util.Set<String> LAUNCH_SHOTS = new java.util.HashSet<>();
	private static final java.util.Set<String> SHOT_KINDS = new java.util.HashSet<>();
	private static int ticks;
	private static int step;
	private static int worldTicks;
	private static int screenTicks;
	private static int screenStep;
	private static int phase;
	private static boolean worldStarted;

	private SelfTest() {
	}

	static void init() {
		String mode = System.getProperty("cakeui.selftest", "");
		if (mode.isEmpty()) {
			return;
		}
		worldMode = mode.equals("world");
		enabled = true;
		CakeUI.LOGGER.info("CakeUI self-test: title screen{}, then quit", worldMode ? ", pause menu and inventory" : "");
		ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
		if (worldMode) {
			// while a world loads the game renders without ticking: shoot loading screens from their own render event
			ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
				String kind = LoadingPart.kind(screen);
				if (kind == null) {
					return;
				}
				int[] frames = {0};
				ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
					if (++frames[0] == 8 && SHOT_KINDS.add(kind)) {
						ctx.draw();
						ScreenshotRecorder.saveScreenshot(client.runDirectory, "cakeui-selftest-loading-" + kind + ".png", client.getFramebuffer(),
								text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
					}
				});
			});
		}
	}

	private static void tick(MinecraftClient client) {
		if (client.currentScreen instanceof TitleScreen && !worldStarted) {
			titleTick(client);
		} else if (worldStarted && client.world != null && client.player != null) {
			worldTick(client);
		}
	}

	private static void titleTick(MinecraftClient client) {
		ticks++;
		double seconds = ticks / 20.0;
		if (step < TITLE.length) {
			if (shoot(client, TITLE[step], seconds, "cakeui-selftest-" + step + ".png")) {
				step++;
			}
		} else if (seconds >= TITLE[TITLE.length - 1][0] + 2) {
			if (!reloaded) {
				// a resource reload shows the launch overlay again, over the title screen
				reloaded = true;
				client.reloadResources();
				return;
			}
			if (client.getOverlay() != null) {
				return;
			}
			if (worldMode) {
				worldStarted = true;
				openWorld(client);
			} else {
				done(client);
			}
		}
	}

	private static void openWorld(MinecraftClient client) {
		var loader = client.createIntegratedServerLoader();
		if (client.getLevelStorage().levelExists(WORLD)) {
			loader.start(WORLD, () -> client.setScreen(new TitleScreen()));
		} else {
			loader.createAndStart(WORLD, MinecraftServer.DEMO_LEVEL_INFO, GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, new TitleScreen());
		}
	}

	private static void worldTick(MinecraftClient client) {
		worldTicks++;
		// give the world 3 s to show, then a few items (on the server thread), then the pause menu as Esc opens it
		if (worldTicks == 60) {
			var server = client.getServer();
			var id = client.player.getUuid();
			if (server != null) {
				server.execute(() -> {
					var p = server.getPlayerManager().getPlayer(id);
					if (p != null && p.getInventory().isEmpty()) {
						p.getInventory().insertStack(new ItemStack(Items.DIAMOND_SWORD));
						p.getInventory().insertStack(new ItemStack(Items.BREAD, 12));
						p.getInventory().insertStack(new ItemStack(Items.TORCH, 64));
					}
				});
			}
			client.setScreen(new GameMenuScreen(true));
		}
		if (worldTicks < 60) {
			return;
		}
		double[][] steps = phase == 0 ? PAUSE : INVENTORY;
		String name = phase == 0 ? "pause" : "inventory";
		screenTicks++;
		double seconds = screenTicks / 20.0;
		if (screenStep < steps.length) {
			if (shoot(client, steps[screenStep], seconds, "cakeui-selftest-" + name + "-" + screenStep + ".png")) {
				screenStep++;
			}
		} else if (seconds >= steps[steps.length - 1][0] + 2) {
			if (phase == 0) {
				phase = 1;
				screenTicks = 0;
				screenStep = 0;
				client.setScreen(new InventoryScreen(client.player));
			} else {
				done(client);
			}
		}
	}

	/** Each frame of the launch overlay (LaunchPart): one shot at start, one during the reload. */
	static void launchFrame(boolean reloading) {
		if (!enabled) {
			return;
		}
		if (reloading != launchReloading) {
			launchReloading = reloading;
			launchFrames = 0;
		}
		String name = reloading ? "reload" : "start";
		if (++launchFrames == 40 && LAUNCH_SHOTS.add(name)) {
			MinecraftClient client = MinecraftClient.getInstance();
			ScreenshotRecorder.saveScreenshot(client.runDirectory, "cakeui-selftest-launch-" + name + ".png", client.getFramebuffer(),
					text -> CakeUI.LOGGER.info("CakeUI self-test: launch {}", name));
		}
	}

	/** Moves the mouse a second before the shot (so hover transitions settle); true once the shot is taken. */
	private static boolean shoot(MinecraftClient client, double[] s, double seconds, String name) {
		if (seconds >= s[0] - 1.0 && s[1] >= 0) {
			var w = client.getWindow();
			((MouseInvoker) client.mouse).cakeui$onCursorPos(w.getHandle(), s[1] * w.getWidth(), s[2] * w.getHeight());
		}
		if (seconds < s[0]) {
			return false;
		}
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
				text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
		return true;
	}

	private static void done(MinecraftClient client) {
		CakeUI.LOGGER.info("CakeUI self-test: done, {} problem(s): {}", Ui.problems().size(), Ui.problems());
		client.scheduleStop();
	}
}
