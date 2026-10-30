package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/cakeui/config.json5, created with the defaults below on first launch; modpacks ship their own. */
public final class Config {
	public String layoutPath = "";
	public boolean devMode = false;
	public boolean showButtonIds = false;

	static final String TEMPLATE = """
			// CakeUI settings.
			{
			  // The layout to use: a window_<name> folder, relative to the game folder,
			  // e.g. "cakeui/layouts/window_example". Empty = no layout: the game looks vanilla.
			  layoutPath: "",
			  // Dev mode: reload the layout when its files change, and list layout problems on screen.
			  devMode: false,
			  // In dev mode, print each button's id next to it (for the buttons port).
			  showButtonIds: false,
			}
			""";

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(CakeUI.MOD_ID).resolve("config.json5");
	}

	public static Config load() {
		Path file = file();
		Config c = new Config();
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
				return c;
			}
			JValue v = Json5.parse(Files.readString(file, StandardCharsets.UTF_8), "config/cakeui/config.json5");
			if (v instanceof JValue.JObject o) {
				if (o.get("layoutPath") instanceof JValue.JString s) {
					c.layoutPath = s.value.trim();
				}
				if (o.get("devMode") instanceof JValue.JBool b) {
					c.devMode = b.value;
				}
				if (o.get("showButtonIds") instanceof JValue.JBool b) {
					c.showButtonIds = b.value;
				}
			}
		} catch (Exception e) {
			CakeUI.LOGGER.error("CakeUI: could not read {}, using defaults: {}", file, e.toString());
		}
		return c;
	}

	/** The layout folder, or null when none is chosen. */
	public Path layoutFolder() {
		if (layoutPath.isEmpty()) {
			return null;
		}
		Path p = Path.of(layoutPath);
		return p.isAbsolute() ? p : FabricLoader.getInstance().getGameDir().resolve(p).normalize();
	}
}
