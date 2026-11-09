package io.github.harrythecake.cakecosmetic;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * config/cakecosmetic/config.json5: the mode and dev mode. Hidden from players on purpose (no
 * settings screen); the mod writes its defaults when the file is missing.
 */
public final class Settings {
	public enum Mode {
		/** Backgrounds with an unlock rule need its advancements, earned on Monumenta. */
		ADVANCEMENT,
		/** Every background can be picked. */
		ALL
	}

	static final String FILE = "config.json5";
	static final String DEFAULT_TEXT = """
			{
			  // "advancement": a background with an unlock rule (rules.json5) needs its advancements, earned on Monumenta.
			  // "all": every background can be picked.
			  mode: "advancement",
			  // true: the gallery shows ids and the unlock-rule editor (for pack makers).
			  devMode: false,
			}
			""";

	public Mode mode = Mode.ADVANCEMENT;
	public boolean devMode;

	/** Reads the text; anything missing or wrong keeps its default. */
	static Settings parse(String text) {
		Settings s = new Settings();
		JValue root;
		try {
			root = Json5.parse(text, FILE);
		} catch (RuntimeException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: {} can't be read, using defaults: {}", FILE, e.getMessage());
			return s;
		}
		if (root instanceof JValue.JObject o) {
			if (o.get("mode") instanceof JValue.JString m) {
				switch (m.value.trim().toLowerCase(Locale.ROOT)) {
					case "all" -> s.mode = Mode.ALL;
					case "advancement" -> s.mode = Mode.ADVANCEMENT;
					default -> CakeCosmetic.LOG.warn("CakeCosmetic: mode '{}' is unknown, using advancement", m.value);
				}
			}
			if (o.get("devMode") instanceof JValue.JBool b) {
				s.devMode = b.value;
			}
		}
		return s;
	}

	static Settings load(Path dir) {
		Path file = dir.resolve(FILE);
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(dir);
				Files.writeString(file, DEFAULT_TEXT);
				return new Settings();
			}
			return parse(Files.readString(file));
		} catch (IOException e) {
			CakeCosmetic.LOG.warn("CakeCosmetic: can't read {}: {}", file, e.toString());
			return new Settings();
		}
	}
}
