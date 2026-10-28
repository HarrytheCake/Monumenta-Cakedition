package io.github.harrythecake.cakebook;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/cakebook/config.json5. Created with the defaults below on first launch; modpacks can ship their own. */
final class Config {
	boolean pauseMenuButton = true;
	boolean inventoryButton = true;
	String firstBook = "";

	private static final String TEMPLATE = """
			// CakeBook settings. Comments are allowed; trailing commas are not.
			{
			  // A book button next to "Back to Game" in the pause menu.
			  "pauseMenuButton": true,
			  // A book button to the right of the inventory.
			  "inventoryButton": true,
			  // The book opened the first time (its folder name, e.g. "cakedition"). "" = the first in the list.
			  "firstBook": ""
			}
			""";

	static Config load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(CakeBook.MOD_ID).resolve("config.json5");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
			}
			// Gson reads leniently, which allows the // comments.
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Config config = new Gson().fromJson(reader, Config.class);
				if (config == null) {
					return new Config();
				}
				if (config.firstBook == null) {
					config.firstBook = "";
				}
				return config;
			}
		} catch (Exception e) {
			CakeBook.LOGGER.error("Could not read {}, using defaults: {}", file, e.toString());
			return new Config();
		}
	}
}
