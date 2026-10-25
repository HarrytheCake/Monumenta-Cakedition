package io.github.harrythecake.cakeupdater;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * config/cakeupdater/config.json5. Created with the defaults below on first launch;
 * modpack creators ship their own copy to point the mod at their pack.
 */
final class Config {
	String chatPrefix = "Cakedition";
	Packwiz packwiz = new Packwiz();
	List<ResourcePack> resourcePacks = new ArrayList<>(List.of(new ResourcePack()));

	static final class Packwiz {
		boolean enabled = true;
		String packUrl = "https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition/packwiz/pack/pack.toml";
	}

	static final class ResourcePack {
		boolean enabled = true;
		String modrinthProject = "Yj53Rs56";
		List<String> gameVersions = new ArrayList<>();
		List<String> versionTypes = new ArrayList<>(List.of("release"));
	}

	private static final String TEMPLATE = """
			// Cakedition Updater settings.
			// Modpack creators: ship your own copy of this file in your pack at config/cakeupdater/config.json5.
			// Comments are allowed; trailing commas are not.
			{
			  // Name used in chat ("[Cakedition] ...") and in the updater window title.
			  "chatPrefix": "Cakedition",

			  // Modpack updates through packwiz-installer.
			  "packwiz": {
			    "enabled": true,
			    // Public URL of your pack.toml (on GitHub: the raw.githubusercontent.com link).
			    // On Windows this and chatPrefix must not contain & ^ | < > (they pass through cmd).
			    "packUrl": "https://raw.githubusercontent.com/HarrytheCake/Monumenta-Cakedition/packwiz/pack/pack.toml"
			  },

			  // Resource packs to keep up to date from Modrinth. Only packs the player has turned on are checked.
			  // Use [] to turn resource pack updates off.
			  "resourcePacks": [
			    {
			      "enabled": true,
			      // Modrinth project ID or slug (the last part of the project's URL), e.g. "monumenta-mmo-resource-pack".
			      "modrinthProject": "Yj53Rs56",
			      // Minecraft versions the update must support. [] = the version the game is running.
			      "gameVersions": [],
			      // Release channels to accept: "release", "beta", "alpha".
			      "versionTypes": ["release"]
			    }
			  ]
			}
			""";

	static Config load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(CakeUpdater.MOD_ID).resolve("config.json5");
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
				if (config.packwiz == null) {
					config.packwiz = new Packwiz();
				}
				if (config.resourcePacks == null) {
					config.resourcePacks = new ArrayList<>();
				}
				config.resourcePacks.removeIf(pack -> pack == null || pack.modrinthProject == null);
				return config;
			}
		} catch (Exception e) {
			CakeUpdater.LOGGER.error("Could not read {}, using defaults: {}", file, e.toString());
			return new Config();
		}
	}
}
