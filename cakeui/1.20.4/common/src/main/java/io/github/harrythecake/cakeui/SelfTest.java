package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.mixin.MouseInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development only, off unless the JVM runs with -Dcakeui.selftest (Loom's runClient passes it on
 * request). On the title screen it takes screenshots with the mouse in a few places. With
 * -Dcakeui.selftest=world it then opens a test world ("cakeui-selftest" in run/saves) and takes
 * more of the loading screens on the way (one per kind), the pause menu, the inventory and the HUD, and
 * times the HUD with and without the layout. Then it quits.
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
	private static int configTicks;
	private static int launchFrames;
	private static boolean launchReloading;
	private static final java.util.Set<String> LAUNCH_SHOTS = new java.util.HashSet<>();
	private static final java.util.Set<String> SHOT_KINDS = new java.util.HashSet<>();
	private static int loadingFrames;
	private static int ticks;
	private static int step;
	private static int worldTicks;
	private static int screenTicks;
	private static int screenStep;
	private static int phase;
	private static boolean pauseOnLostFocus = true;
	private static double[] hudOn = {0, 0};
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
		if (Boolean.getBoolean("cakeui.selftest.fill")) {
			// a stand-in plugin: gradients behind the main menu (purple) and loading screens (teal)
			io.github.harrythecake.cakeui.api.CakeUIApi api = io.github.harrythecake.cakeui.api.CakeUIApi.get();
			api.fillPort(PluginPorts.MAIN_MENU_BACKGROUND, "cakeui-selftest", (ctx, screen, alpha) -> {
				int a = Math.round(alpha * 255) << 24;
				ctx.fillGradient(0, 0, screen.width, screen.height, a | 0x6a2c91, a | 0x1b0b2e);
				return true;
			});
			api.fillPort(PluginPorts.LOADING_BACKGROUND, "cakeui-selftest", (ctx, screen, alpha) -> {
				ctx.fillGradient(0, 0, screen.width, screen.height, 0xff1f8a7a, 0xff06231f);
				return true;
			});
			CakeUI.LOGGER.info("CakeUI self-test: test fillers on {}", PluginPorts.FILLABLE);
		}
	}

	/** While a world loads the game renders without ticking: loading screens are shot from their own render (one per kind). */
	static void screenInit(Screen screen) {
		loadingFrames = 0;
	}

	static void afterScreenRender(Screen screen, GuiGraphics ctx) {
		if (!enabled || !worldMode) {
			return;
		}
		String kind = LoadingPart.kind(screen);
		if (kind != null && ++loadingFrames == 8 && SHOT_KINDS.add(kind)) {
			Minecraft client = Minecraft.getInstance();
			ctx.flush();
			Screenshot.grab(client.gameDirectory, "cakeui-selftest-loading-" + kind + ".png", client.getMainRenderTarget(),
					text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
		}
	}

	static void tick(Minecraft client) {
		if (!enabled) {
			return;
		}
		if (client.screen instanceof ConfigScreen) {
			// the settings screen (Mod Menu's button opens it): one shot, then back to the title screen
			if (++configTicks == 40) {
				Screenshot.grab(client.gameDirectory, "cakeui-selftest-config.png", client.getMainRenderTarget(),
						text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
			} else if (configTicks == 50) {
				client.screen.onClose();
			}
			return;
		}
		if (client.screen instanceof TitleScreen && !worldStarted) {
			titleTick(client);
		} else if (worldStarted && client.level != null && client.player != null) {
			worldTick(client);
		}
	}

	private static void titleTick(Minecraft client) {
		ticks++;
		double seconds = ticks / 20.0;
		if (step < TITLE.length) {
			if (shoot(client, TITLE[step], seconds, "cakeui-selftest-" + step + ".png")) {
				step++;
			}
		} else if (seconds >= TITLE[TITLE.length - 1][0] + 2) {
			if (configTicks == 0) {
				client.setScreen(new ConfigScreen(client.screen));
				return;
			}
			if (!reloaded) {
				// a resource reload shows the launch overlay again, over the title screen
				reloaded = true;
				client.reloadResourcePacks();
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

	private static void openWorld(Minecraft client) {
		var loader = client.createWorldOpenFlows();
		if (client.getLevelSource().levelExists(WORLD)) {
			loader.checkForBackupAndLoad(WORLD, () -> client.setScreen(new TitleScreen()));
		} else {
			loader.createFreshLevel(WORLD, MinecraftServer.DEMO_SETTINGS, WorldOptions.DEMO_OPTIONS, WorldPresets::createNormalWorldDimensions, new TitleScreen());
		}
	}

	private static void worldTick(Minecraft client) {
		worldTicks++;
		// give the world 3 s to show, then a few items (on the server thread), then the pause menu as Esc opens it
		if (worldTicks == 60) {
			var server = client.getSingleplayerServer();
			var id = client.player.getUUID();
			if (server != null) {
				server.execute(() -> {
					var p = server.getPlayerList().getPlayer(id);
					if (p != null && p.getInventory().isEmpty()) {
						p.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
						p.getInventory().add(new ItemStack(Items.BREAD, 12));
						p.getInventory().add(new ItemStack(Items.TORCH, 64));
					}
					if (p != null && p.experienceLevel == 0) {
						p.giveExperienceLevels(7);
						p.giveExperiencePoints(40);
					}
				});
			}
			client.setScreen(new PauseScreen(true));
		}
		if (worldTicks < 60) {
			return;
		}
		if (phase == 2) {
			hudTick(client);
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
				phase = 2;
				screenTicks = 0;
				// the dev window is often in the background: it must not pause itself while the HUD is measured
				pauseOnLostFocus = client.options.pauseOnLostFocus;
				client.options.pauseOnLostFocus = false;
				client.setScreen(null);
			}
		}
	}

	/** HUD: a screenshot, then InGameHud.render timed for 10 s with the layout's HUD and 10 s with it off. */
	private static void hudTick(Minecraft client) {
		screenTicks++;
		if (screenTicks == 10) {
			client.gui.setOverlayMessage(Component.literal("Action bar from the self-test"), false);
			client.gui.setTimes(5, 60, 10);
			client.gui.setTitle(Component.literal("Title"));
			client.gui.setSubtitle(Component.literal("and its subtitle"));
		} else if (screenTicks == 40) {
			Screenshot.grab(client.gameDirectory, "cakeui-selftest-hud.png", client.getMainRenderTarget(),
					text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
		} else if (screenTicks == 60) {
			HudTiming.enabled = true;
			HudTiming.reset();
		} else if (screenTicks == 260) {
			hudOn = HudTiming.result();
			HudPart.benchOff = true;
			HudTiming.reset();
		} else if (screenTicks == 460) {
			double[] off = HudTiming.result();
			HudPart.benchOff = false;
			HudTiming.enabled = false;
			CakeUI.LOGGER.info("CakeUI self-test: HUD bench: with the layout's HUD {} ms/frame ({} frames), without {} ms/frame ({} frames), CakeUI adds {} ms",
					String.format("%.4f", hudOn[0]), (long) hudOn[1], String.format("%.4f", off[0]), (long) off[1], String.format("%.4f", hudOn[0] - off[0]));
			client.options.pauseOnLostFocus = pauseOnLostFocus;
			done(client);
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
			Minecraft client = Minecraft.getInstance();
			Screenshot.grab(client.gameDirectory, "cakeui-selftest-launch-" + name + ".png", client.getMainRenderTarget(),
					text -> CakeUI.LOGGER.info("CakeUI self-test: launch {}", name));
		}
	}

	/** Moves the mouse a second before the shot (so hover transitions settle); true once the shot is taken. */
	private static boolean shoot(Minecraft client, double[] s, double seconds, String name) {
		if (seconds >= s[0] - 1.0 && s[1] >= 0) {
			var w = client.getWindow();
			((MouseInvoker) client.mouseHandler).cakeui$onCursorPos(w.getWindow(), s[1] * w.getScreenWidth(), s[2] * w.getScreenHeight());
		}
		if (seconds < s[0]) {
			return false;
		}
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
		return true;
	}

	private static void done(Minecraft client) {
		CakeUI.LOGGER.info("CakeUI self-test: done, {} problem(s): {}", Ui.problems().size(), Ui.problems());
		client.stop();
	}
}
