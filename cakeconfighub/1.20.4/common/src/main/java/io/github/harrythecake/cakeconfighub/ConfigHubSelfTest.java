package io.github.harrythecake.cakeconfighub;

import io.github.harrythecake.cakeconfighub.api.CakeConfighubApi;
import io.github.harrythecake.cakeconfighub.configmap.ConfigMap;
import io.github.harrythecake.cakeconfighub.configmap.HubModel;
import io.github.harrythecake.cakeconfighub.configmap.HubSelfTestFiles;
import io.github.harrythecake.cakeconfighub.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-test of the config hub (ADR 0011, 0013; only with -Dcakeconfighub.selftest), on the title screen: the pack
 * defaults the preLaunch run applied to the made-up file ({@link HubSelfTestFiles}), the hub list, the mod page with its
 * pack-default mark, a save through the page (value-only, comment kept), the reload hook, a reset, scrolling; with
 * CakeUI installed also CakeUI's settings screen (no "Other mods…" button any more). Logs "hub: … (PASS|FAIL)" lines,
 * removes its files and ends with "CakeConfighub self-test done".
 */
final class ConfigHubSelfTest {
	private static boolean started;
	private static boolean finished;
	private static int ticks;
	private static int waited;
	private static Screen cakeUi;
	private static boolean reloaded;
	private static ConfigHubScreen hub;
	private static ConfigHubModScreen page;

	private ConfigHubSelfTest() {
	}

	static boolean active() {
		return started && !finished;
	}

	static boolean finished() {
		return finished;
	}

	private static void log(String what, boolean ok, Object detail) {
		CakeConfighub.LOGGER.info("CakeConfighub self-test: hub: {} {} ({})", what, detail, ok ? "PASS" : "FAIL");
	}

	private static Path file() {
		return Services.PLATFORM.gameDir().resolve(HubSelfTestFiles.FILE);
	}

	private static String read(Path p) {
		try {
			return Files.readString(p, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	private static void shot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				text -> CakeConfighub.LOGGER.info("CakeConfighub self-test: {}", text.getString()));
	}

	static void start(Minecraft client) {
		started = true;
		String text = read(file());
		// the startup run: size 3 was the pack's old value -> 5; name is the player's -> kept; comment kept
		log("pack defaults at startup", text.contains("\"size\": 5") && text.contains("\"name\": \"player\"")
				&& text.contains(HubSelfTestFiles.COMMENT), text.replace('\n', ' ').replace('\t', ' '));
		String record = read(Services.PLATFORM.configDir().resolve("cakeconfighub").resolve("pack-defaults-applied.json5"));
		log("record", record.contains("\"size\": 5") && !record.contains("\"name\""), record.replace('\n', ' '));
		CakeConfighubApi.registerConfigReload("cakeconfighub", () -> reloaded = true);
		hub = new ConfigHubScreen(client.screen);
		client.setScreen(hub);
	}

	/** Every client tick: starts on the title screen, then runs the steps. */
	static void tick(Minecraft client) {
		if (!CakeConfighub.SELF_TEST || finished) {
			return;
		}
		if (!started) {
			if (client.screen instanceof TitleScreen && client.getOverlay() == null && ++waited >= 60) {
				start(client);
			}
			return;
		}
		ticks++;
		if (ticks == 20) {
			shot(client, "cakeconfighub-selftest.png");
			HubModel.Mod mod = hub.model.mods.stream().filter(m -> m.id.equals(HubSelfTestFiles.MAP_ID)).findFirst().orElse(null);
			log("listed and editable", mod != null && mod.editable(), mod == null ? "missing" : mod.unavailable);
			if (mod == null) {
				finish(client);
				return;
			}
			page = new ConfigHubModScreen(hub, hub.model, mod);
			client.setScreen(page);
		} else if (ticks == 40) {
			shot(client, "cakeconfighub-selftest-mod.png");
			HubModel model = hub.model;
			HubModel.Mod mod = page.mod();
			ConfigMap m = mod.map;
			log("pack default mark", Long.valueOf(5).equals(model.packDefault(mod, m.option("size"))), model.packDefault(mod, m.option("size")));
			model.set(mod, m.option("show.enabled"), true);
			model.set(mod, m.option("show.mode"), "c");
			model.set(mod, m.option("scale"), 1.5);
			model.set(mod, m.option("color"), 0x3366FFL);
			page.save();
			String text = read(file());
			boolean ok = text.contains("\"enabled\": true") && text.contains("\"mode\": \"c\"") && text.contains("\"scale\": 1.5")
					&& text.contains("\"color\": \"#3366FF\"") && text.contains("\"size\": 5") && text.contains(HubSelfTestFiles.COMMENT);
			log("saved value by value", ok, text.replace('\n', ' ').replace('\t', ' '));
			log("reload hook", reloaded, page.message() == null ? "no message" : page.message().getString());
		} else if (ticks == 45) {
			page.mouseScrolled(0, 0, 0, -1);  // the rows below (colour, name) and the scrollbar
			page.mouseScrolled(0, 0, 0, -1);
		} else if (ticks == 50) {
			shot(client, "cakeconfighub-selftest-saved.png");
			// Reset: an option back to its pack default (size 8 -> 5); Reset this mod: every option with a pack default
			// or default (name "player" -> the pack's "pack"); then the unsaved changes are dropped (name "player" again)
			HubModel model = hub.model;
			HubModel.Mod mod = page.mod();
			ConfigMap.Option size = mod.map.option("size");
			ConfigMap.Option name = mod.map.option("name");
			model.set(mod, size, 8L);
			boolean dirty = model.dirty();
			model.reset(mod, size);
			Object afterReset = model.value(mod, size);
			model.resetMod(mod);
			Object afterResetMod = model.value(mod, name);
			model.discard();
			log("reset", dirty && Long.valueOf(5).equals(afterReset) && "pack".equals(afterResetMod)
					&& "player".equals(model.value(mod, name)) && !model.dirty(),
					"size " + afterReset + ", name after Reset this mod " + afterResetMod + ", after discard " + model.value(mod, name));
		} else if (ticks == 55) {
			if (Services.PLATFORM.isModLoaded("cakeui")) {
				cakeUi = cakeUiSettings(client);
			} else {
				finish(client);
			}
		} else if (ticks == 75) {
			shot(client, "cakeconfighub-selftest-cakeui.png");
			boolean other = false;
			for (GuiEventListener w : cakeUi.children()) {
				if (w instanceof AbstractWidget a && a.getMessage().getString().contains("Other mods")) {
					other = true;
				}
			}
			log("CakeUI's settings without \"Other mods…\"", !other, cakeUi.getClass().getName());
			finish(client);
		}
	}

	/** CakeUI's settings screen, by reflection (no dependency); only for check (b) with CakeUI's jar in the run. */
	private static Screen cakeUiSettings(Minecraft client) {
		try {
			Screen s = (Screen) Class.forName("io.github.harrythecake.cakeui.ConfigScreen").getConstructor(Screen.class)
					.newInstance(new TitleScreen());
			client.setScreen(s);
			return s;
		} catch (ReflectiveOperationException | LinkageError e) {
			log("CakeUI's settings opened", false, e.toString());
			return new TitleScreen();
		}
	}

	private static void finish(Minecraft client) {
		finished = true;
		try {
			HubSelfTestFiles.remove(Services.PLATFORM.configDir());
		} catch (Exception e) {
			CakeConfighub.LOGGER.warn("CakeConfighub self-test: hub files not removed: {}", e.toString());
		}
		client.setScreen(new TitleScreen());
		CakeConfighub.LOGGER.info("CakeConfighub self-test done");
	}
}
