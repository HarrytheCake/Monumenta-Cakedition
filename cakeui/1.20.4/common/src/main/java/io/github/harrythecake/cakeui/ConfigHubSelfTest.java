package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.configmap.ConfigMap;
import io.github.harrythecake.cakeui.configmap.HubModel;
import io.github.harrythecake.cakeui.configmap.HubSelfTestFiles;
import io.github.harrythecake.cakeui.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Self-test of the config hub (ADR 0011), on the title screen: the pack defaults the preLaunch run applied to the
 * made-up file ({@link HubSelfTestFiles}), the hub list, the mod page with its pack-default mark, a save through the
 * page (value-only, comment kept) and the reload hook. Logs "hub: … (PASS|FAIL)" lines; removes its files at the end.
 */
final class ConfigHubSelfTest {
	private static boolean started;
	private static boolean finished;
	private static int ticks;
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
		CakeUI.LOGGER.info("CakeUI self-test: hub: {} {} ({})", what, detail, ok ? "PASS" : "FAIL");
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
				text -> CakeUI.LOGGER.info("CakeUI self-test: {}", text.getString()));
	}

	static void start(Minecraft client) {
		started = true;
		String text = read(file());
		// the startup run: size 3 was the pack's old value -> 5; name is the player's -> kept; comment kept
		log("pack defaults at startup", text.contains("\"size\": 5") && text.contains("\"name\": \"player\"")
				&& text.contains(HubSelfTestFiles.COMMENT), text.replace('\n', ' ').replace('\t', ' '));
		String record = read(Services.PLATFORM.configDir().resolve("cakeui").resolve("pack-defaults-applied.json5"));
		log("record", record.contains("\"size\": 5") && !record.contains("\"name\""), record.replace('\n', ' '));
		CakeUIApi.get().registerConfigReload("cakeui", () -> reloaded = true);
		hub = new ConfigHubScreen(client.screen);
		client.setScreen(hub);
	}

	static void tick(Minecraft client) {
		ticks++;
		if (ticks == 20) {
			shot(client, "cakeui-selftest-hub.png");
			HubModel.Mod mod = hub.model.mods.stream().filter(m -> m.id.equals(HubSelfTestFiles.MAP_ID)).findFirst().orElse(null);
			log("listed and editable", mod != null && mod.editable(), mod == null ? "missing" : mod.unavailable);
			if (mod == null) {
				finish(client);
				return;
			}
			page = new ConfigHubModScreen(hub, hub.model, mod);
			client.setScreen(page);
		} else if (ticks == 40) {
			shot(client, "cakeui-selftest-hub-mod.png");
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
			shot(client, "cakeui-selftest-hub-saved.png");
		} else if (ticks == 55) {
			finish(client);
		}
	}

	private static void finish(Minecraft client) {
		finished = true;
		try {
			HubSelfTestFiles.remove(Services.PLATFORM.configDir());
		} catch (Exception e) {
			CakeUI.LOGGER.warn("CakeUI self-test: hub files not removed: {}", e.toString());
		}
		client.setScreen(new TitleScreen());
	}
}
