package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.HudDefaults;
import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.PortEdit;
import java.util.Map;
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

	/** The self-test's stand-in movable (see init). */
	static final double[] DEMO_BOX = {40, 150, 60, 20};
	static boolean demoSaved;

	static void init() {
		String mode = System.getProperty("cakeui.selftest", "");
		if (mode.isEmpty()) {
			return;
		}
		worldMode = mode.equals("world");
		enabled = true;
		if (worldMode) {
			ContainerSelfTest.init(Minecraft.getInstance()); // a stand-in mod's default container layout (API 4)
		}
		// a stand-in for a mod's own movable (API 3): it stores its box itself and says when it was saved
		io.github.harrythecake.cakeui.api.CakeUIApi.get().addMovables("cakeui-selftest", () -> java.util.List.of(new io.github.harrythecake.cakeui.api.Movable() {
			@Override
			public String id() {
				return "demo";
			}

			@Override
			public net.minecraft.network.chat.Component label() {
				return net.minecraft.network.chat.Component.literal("Self-test movable");
			}

			@Override
			public double[] box(int w, int h) {
				return worldMode ? DEMO_BOX.clone() : null;
			}

			@Override
			public void place(double x, double y, double width, double height, int w, int h) {
				DEMO_BOX[0] = Math.round(x);
				DEMO_BOX[1] = Math.round(y);
				DEMO_BOX[2] = Math.round(width);
				DEMO_BOX[3] = Math.round(height);
			}

			@Override
			public boolean resizable() {
				return true;
			}

			@Override
			public void save() {
				demoSaved = true;
			}
		}));
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
			loader.openWorld(WORLD, () -> client.setScreen(new TitleScreen()));
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
		if (phase == 3) {
			// container screens (ContainerSelfTest), then the HUD
			if (ContainerSelfTest.tick(client)) {
				phase = 2;
				screenTicks = 0;
				// the dev window is often in the background: it must not pause itself while the HUD is measured
				pauseOnLostFocus = client.options.pauseOnLostFocus;
				client.options.pauseOnLostFocus = false;
				client.setScreen(null);
			}
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
				phase = 3; // container screens next
				screenTicks = 0;
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
			editorTick(client);
		} else if (screenTicks > 460) {
			editorTick(client);
		}
	}

	/** A screenshot from the self-test (no message). */
	private static final java.util.function.BiConsumer<Minecraft, String> SHOT = (client, name) ->
			Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), text -> { });

	/**
	 * The HUD editor: personal edits (open, move/scale/fade/hide as a drag would, save, check the HUD
	 * follows, clean up), then with a dev-mode layout its own edits (hud/edits.json5, the layout's first
	 * HUD element moved to the bottom-right corner).
	 */
	private static void editorTick(Minecraft client) {
		int t = screenTicks - 460;
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		if (t == 0) {
			client.setScreen(new HudEditorScreen(null));
			HudEditor.setTarget(HudEditor.Target.PERSONAL);
		} else if (t == 30) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-open.png");
			Map<String, PortEdit> edits = HudEditor.editingMap();
			Box hb = HudBounds.box("hotbar");
			CakeUI.LOGGER.info("CakeUI self-test: editor: hotbar measured at {} (vanilla {}), {}", hb, HudDefaults.box("hotbar", w, h),
					hb != null && Math.abs(hb.x() - (w / 2.0 - 91)) <= 2.5 && Math.abs(hb.bottom() - h) <= 1 ? "PASS" : "FAIL");
			boolean greyed = false;
			for (String name : HudEditor.pieces()) {
				greyed |= HudExpect.expected(name) && !HudBounds.hookedRecently(name);
			}
			CakeUI.LOGGER.info("CakeUI self-test: editor: no vanilla piece taken for another mod's ({})", greyed ? "FAIL" : "PASS");
			PortEdit hotbar = new PortEdit();
			Placement.place(hotbar, PortEdit.Mode.ANCHORED, 4, 4, hb == null ? 182 : hb.w(), hb == null ? 22 : hb.h(), w, h);
			edits.put("hotbar", hotbar);
			PortEdit health = new PortEdit();
			health.scale = 1.5;
			edits.put("health", health);
			PortEdit xp = new PortEdit();
			xp.opacity = 0.5f;
			edits.put("experience", xp);
			PortEdit crosshair = new PortEdit();
			crosshair.element = io.github.harrythecake.cakeui.edit.CrosshairStyles.defaults("cross", null);
			crosshair.element.addProperty("color", "#ff4040");
			crosshair.element.addProperty("invert", false);
			edits.put("crosshair", crosshair);
			// E3: an added text (bottom-left) and a bar that never shows (its condition is false)
			PortEdit text = new PortEdit();
			text.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("text", null);
			Placement.place(text, PortEdit.Mode.ANCHORED, 4, h - 60, 40, 9, w, h);
			edits.put("+text-test", text);
			PortEdit bar = new PortEdit();
			bar.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("bar", null);
			bar.element.addProperty("visible", "= false");
			Placement.place(bar, PortEdit.Mode.ANCHORED, 4, h - 70, 80, 5, w, h);
			edits.put("+bar-test", bar);
			// E4: the hotbar as an inventory element (top right) and the held item
			PortEdit inv = new PortEdit();
			inv.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("inventory", null);
			inv.element.addProperty("slots", "hotbar");
			inv.element.addProperty("columns", 3);
			Placement.place(inv, PortEdit.Mode.ANCHORED, w - 60, 60, 56, 56, w, h);
			edits.put("+inventory-test", inv);
			PortEdit held = new PortEdit();
			held.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("item", null);
			held.element.addProperty("item", "slot:mainhand");
			held.scale = 2.0;
			Placement.place(held, PortEdit.Mode.ANCHORED, w - 40, 130, 32, 32, w, h);
			edits.put("+item-test", held);
			// E5: a mod's own movable, moved as a drag would (the editor calls place; Done must make the mod save)
			var demo = HudMovables.get("~cakeui-selftest:demo");
			if (demo != null) {
				demo.place(100, 160, 60, 20, w, h);
			}
			// E4: an animated WebP from config/cakeui/images (the smoke run puts anim.webp there)
			if (java.nio.file.Files.isRegularFile(HudAdded.imagesDir().resolve("anim.webp"))) {
				PortEdit anim = new PortEdit();
				anim.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("image", "anim.webp");
				Placement.place(anim, PortEdit.Mode.ANCHORED, w - 40, 90, 32, 32, w, h);
				edits.put("+image-test", anim);
			}
			// E4: health as "bar + number", XP as a slim bar (presets hide vanilla's piece and add one element)
			for (String[] ps : new String[][] {{"health", "barNumber"}, {"experience", "slim"}}) {
				PortEdit port = new PortEdit();
				port.style = ps[1];
				port.hide = true;
				edits.put(ps[0], port);
				PortEdit el = new PortEdit();
				el.element = io.github.harrythecake.cakeui.edit.StylePresets.element(ps[0], ps[1]);
				Box at = HudBounds.orDefault(ps[0], w, h);
				Placement.place(el, PortEdit.Mode.ANCHORED, at.x(), at.y() - 12, at.w(), at.h(), w, h);
				edits.put(io.github.harrythecake.cakeui.edit.StylePresets.key(ps[0]), el);
			}
		} else if (t == 40) {
			if (client.screen instanceof HudEditorScreen editor) {
				editor.openPanel("+text-test", HudEditor.editingMap().get("+text-test"));
			}
		} else if (t == 46) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-element.png");
		} else if (t == 47) {
			if (client.screen instanceof HudElementScreen panel) {
				client.setScreen(new HudValuesScreen(panel, v -> { }));
			}
		} else if (t == 49) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-values.png");
		} else if (t == 52) {
			if (client.screen instanceof HudValuesScreen list) {
				list.onClose();
			}
			if (client.screen instanceof HudElementScreen panel) {
				panel.onClose();
			}
		} else if (t == 53) {
			if (client.screen instanceof HudEditorScreen editor) {
				editor.openPanel("+inventory-test", HudEditor.editingMap().get("+inventory-test"));
			}
		} else if (t == 56) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-inventory-panel.png");
		} else if (t == 57) {
			if (client.screen instanceof HudElementScreen panel) {
				panel.onClose();
			}
		} else if (t == 59) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-edited.png");
		} else if (t == 60) {
			if (client.screen instanceof HudEditorScreen editor) {
				editor.done();
			} else {
				HudEditor.finish(true);
				client.setScreen(null);
			}
		} else if (t == 80) {
			SHOT.accept(client, "cakeui-selftest-hud-edited.png");
			Map<String, PortEdit> saved = HudEdits.load(HudEditor.file()).set(HudEditor.layoutKey());
			Box inner = HudBounds.box("hotbar");
			Box shown = inner == null || !saved.containsKey("hotbar") ? null : Placement.transform(saved.get("hotbar"), inner, w, h).apply(inner);
			CakeUI.LOGGER.info("CakeUI self-test: editor: movable placed by its mod at {} and saved on Done: {} ({})", java.util.Arrays.toString(DEMO_BOX),
					demoSaved, demoSaved && DEMO_BOX[0] == 100 && DEMO_BOX[1] == 160 ? "PASS" : "FAIL");
			boolean animFile = java.nio.file.Files.isRegularFile(HudAdded.imagesDir().resolve("anim.webp"));
			if (animFile) {
				Box ab = HudBounds.box("+image-test");
				boolean animOk = HudBounds.live("+image-test") && ab != null && Math.abs(ab.w() - 32) <= 1 && HudAdded.personalRenderer().layout.problems.isEmpty();
				CakeUI.LOGGER.info("CakeUI self-test: editor: animated WebP drawn at {}, problems {} ({})", ab, HudAdded.personalRenderer().layout.problems.list(), animOk ? "PASS" : "FAIL");
			}
			boolean ok = saved.size() == (animFile ? 11 : 10) && shown != null && Math.abs(shown.x() - 4) <= 1 && Math.abs(shown.y() - 4) <= 1
					&& Math.abs(inner.bottom() - h) <= 1;
			Box textInner = HudBounds.box("+text-test");
			Box textShown = textInner == null ? null : Placement.transform(saved.get("+text-test"), textInner, w, h).apply(textInner);
			boolean textOk = textShown != null && HudBounds.live("+text-test") && Math.abs(textShown.x() - 4) <= 1 && Math.abs(textShown.y() - (h - 60)) <= 1;
			boolean presets = HudBounds.live(io.github.harrythecake.cakeui.edit.StylePresets.key("health")) && !HudBounds.live("health");
			CakeUI.LOGGER.info("CakeUI self-test: editor: health preset drawn, vanilla health hidden ({})", presets ? "PASS" : "FAIL");
			Box cross = HudBounds.box("crosshair");
			boolean crossOk = cross != null && Math.abs(cross.cx() - w / 2.0) <= 1 && Math.abs(cross.cy() - h / 2.0) <= 1 && Math.abs(cross.w() - 12) <= 1;
			CakeUI.LOGGER.info("CakeUI self-test: editor: cross crosshair drawn at {} (12 x 12 at the centre) ({})", cross, crossOk ? "PASS" : "FAIL");
			Box invBox = HudBounds.box("+inventory-test");
			boolean invOk = invBox != null && Math.abs(invBox.w() - 56) <= 4 && Math.abs(invBox.h() - 56) <= 4 && HudBounds.live("+item-test");
			CakeUI.LOGGER.info("CakeUI self-test: editor: inventory element {} (3 x 3 slots of 18 + 1 gap), held item drawn ({})", invBox, invOk ? "PASS" : "FAIL");
			CakeUI.LOGGER.info("CakeUI self-test: editor: added text drawn at {}, the bar with a false condition {} ({})", textShown,
					HudBounds.live("+bar-test") ? "drawn" : "not drawn", textOk && !HudBounds.live("+bar-test") ? "PASS" : "FAIL");
			CakeUI.LOGGER.info("CakeUI self-test: editor: saved {} edit(s) for {}, hotbar inner {} shown {} ({})", saved.size(),
					HudEditor.layoutKey(), inner, shown, ok ? "PASS" : "FAIL");
		} else if (t == 90) {
			// leave no personal edits behind
			HudEditor.start();
			HudEditor.setTarget(HudEditor.Target.PERSONAL);
			HudEditor.editingMap().clear();
			HudEditor.finish(true);
		} else if (t == 92) {
			// E6: the same editor on the main menu
			HudEditorScreen.openFor(new net.minecraft.client.gui.screens.TitleScreen());
			HudEditor.setTarget(HudEditor.Target.PERSONAL); // the player's own edits (dev mode starts on the layout's)
		} else if (t == 110) {
			Map<String, PortEdit> edits = HudEditor.editingMap();
			net.minecraft.client.gui.components.AbstractWidget sp = null;
			if (client.screen instanceof HudEditorScreen ed && ed.targetForTest() != null) {
				for (var b : ScreenPorts.buttons(ed.targetForTest())) {
					if (ScreenPorts.id(b).equals("menu.singleplayer")) {
						sp = b;
					}
				}
			}
			if (sp != null) {
				PortEdit e = new PortEdit();
				Placement.place(e, PortEdit.Mode.ANCHORED, 4, 4, sp.getWidth(), sp.getHeight(), w, h);
				edits.put("main_menu/button:menu.singleplayer", e);
			}
			PortEdit logo = new PortEdit();
			logo.hide = true;
			edits.put("main_menu/logo", logo);
			CakeUI.LOGGER.info("CakeUI self-test: editor: main menu open in the editor, singleplayer button found {} ({})", sp != null, sp != null ? "PASS" : "FAIL");
		} else if (t == 125) {
			SHOT.accept(client, "cakeui-selftest-uieditor-mainmenu.png");
		} else if (t == 130) {
			if (client.screen instanceof HudEditorScreen ed) {
				ed.done();
			}
		} else if (t == 140) {
			SHOT.accept(client, "cakeui-selftest-mainmenu-edited.png");
			net.minecraft.client.gui.components.AbstractWidget sp = null;
			if (client.screen != null) {
				for (var b : ScreenPorts.buttons(client.screen)) {
					if (ScreenPorts.id(b).equals("menu.singleplayer")) {
						sp = b;
					}
				}
			}
			boolean ok = client.screen instanceof net.minecraft.client.gui.screens.TitleScreen && sp != null && sp.getX() == 4 && sp.getY() == 4
					&& !HudBounds.live("main_menu/logo");
			CakeUI.LOGGER.info("CakeUI self-test: editor: main menu after Done: singleplayer at {} {}, logo drawn {} ({})", sp == null ? -1 : sp.getX(),
					sp == null ? -1 : sp.getY(), HudBounds.live("main_menu/logo"), ok ? "PASS" : "FAIL");
		} else if (t == 145) {
			HudEditor.start();
			HudEditor.setTarget(HudEditor.Target.PERSONAL);
			HudEditor.editingMap().keySet().removeIf(k -> k.startsWith("main_menu/"));
			HudEditor.finish(true);
			client.setScreen(null);
		} else if (t == 150) {
			// E6: the pause menu: "Back to Game" moved to the top-left corner
			HudEditorScreen.openFor(new net.minecraft.client.gui.screens.PauseScreen(true));
			HudEditor.setTarget(HudEditor.Target.PERSONAL);
		} else if (t == 160) {
			net.minecraft.client.gui.components.AbstractWidget back = null;
			if (client.screen instanceof HudEditorScreen ed && ed.targetForTest() != null) {
				for (var b : ScreenPorts.buttons(ed.targetForTest())) {
					if (ScreenPorts.id(b).equals("menu.returnToGame")) {
						back = b;
					}
				}
			}
			if (back != null) {
				PortEdit e = new PortEdit();
				Placement.place(e, PortEdit.Mode.ANCHORED, 4, 4, back.getWidth(), back.getHeight(), w, h);
				HudEditor.editingMap().put("pause/button:menu.returnToGame", e);
			}
		} else if (t == 165) {
			SHOT.accept(client, "cakeui-selftest-uieditor-pause.png");
		} else if (t == 168) {
			if (client.screen instanceof HudEditorScreen ed) {
				ed.done();
			}
		} else if (t == 172) {
			net.minecraft.client.gui.components.AbstractWidget back = null;
			if (client.screen != null) {
				for (var b : ScreenPorts.buttons(client.screen)) {
					if (ScreenPorts.id(b).equals("menu.returnToGame")) {
						back = b;
					}
				}
			}
			boolean ok = client.screen instanceof net.minecraft.client.gui.screens.PauseScreen && back != null && back.getX() == 4 && back.getY() == 4;
			CakeUI.LOGGER.info("CakeUI self-test: editor: pause menu after Done: Back to Game at {} {} ({})", back == null ? -1 : back.getX(),
					back == null ? -1 : back.getY(), ok ? "PASS" : "FAIL");
			HudEditor.start();
			HudEditor.setTarget(HudEditor.Target.PERSONAL);
			HudEditor.editingMap().keySet().removeIf(k -> k.startsWith("pause/"));
			HudEditor.finish(true);
			client.setScreen(null);
		} else if (t == 180) {
			if (!HudEditor.layoutTargetPossible() || HudPart.part() == null || HudPart.part().children.isEmpty()) {
				client.options.pauseOnLostFocus = pauseOnLostFocus;
				done(client);
			}
		} else if (t == 190) {
			client.setScreen(new HudEditorScreen(null));
			CakeUI.LOGGER.info("CakeUI self-test: editor: dev mode starts on the layout's edits ({})",
					HudEditor.target() == HudEditor.Target.LAYOUT ? "PASS" : "FAIL");
		} else if (t == 220) {
			Map<String, PortEdit> edits = HudEditor.editingMap();
			PortEdit hotbar = new PortEdit();
			hotbar.scale = 0.75;
			edits.put("hotbar", hotbar);
			String element = HudEdits.ELEMENT + HudPart.part().children.get(0).name;
			Box ib = HudBounds.box(element);
			if (ib != null) {
				PortEdit e = new PortEdit();
				Placement.place(e, PortEdit.Mode.ANCHORED, w - ib.w() - 4, h - ib.h() - 4, ib.w(), ib.h(), w, h);
				edits.put(element, e);
			}
			CakeUI.LOGGER.info("CakeUI self-test: editor: layout element {} measured at {} ({})", element, ib, ib != null ? "PASS" : "FAIL");
		} else if (t == 240) {
			SHOT.accept(client, "cakeui-selftest-hudeditor-layout.png");
		} else if (t == 250) {
			HudEditor.finish(true);
			client.setScreen(null);
		} else if (t == 280) {
			SHOT.accept(client, "cakeui-selftest-hud-layout-edited.png");
			java.nio.file.Path file = HudEditor.layoutEditsFile();
			Map<String, PortEdit> saved = file == null ? Map.of() : HudEdits.loadLayoutEdits(file);
			String element = HudEdits.ELEMENT + HudPart.part().children.get(0).name;
			Box inner = HudBounds.box(element);
			Box shown = inner == null || !saved.containsKey(element) ? null
					: Placement.transform(HudEditor.current().get(element), inner, w, h).apply(inner);
			boolean ok = saved.size() == 2 && shown != null && Math.abs(shown.right() - (w - 4)) <= 1 && Math.abs(shown.bottom() - (h - 4)) <= 1;
			CakeUI.LOGGER.info("CakeUI self-test: editor: layout edits {} saved {}, {} shown {} ({})", file, saved.size(), element, shown, ok ? "PASS" : "FAIL");
		} else if (t == 290) {
			try {
				java.nio.file.Files.deleteIfExists(HudEditor.layoutEditsFile());
			} catch (java.io.IOException e) {
				CakeUI.LOGGER.warn("CakeUI self-test: could not remove the layout's edits", e);
			}
			HudEditor.reload();
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
