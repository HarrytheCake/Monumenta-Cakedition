package io.github.harrythecake.cakeui.configmap;

import io.github.harrythecake.cakeui.CakeUI;
import io.github.harrythecake.cakeui.Config;
import io.github.harrythecake.cakeui.json.Json5;
import io.github.harrythecake.cakeui.platform.Platform;
import io.github.harrythecake.cakeui.platform.Services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Where config maps and pack defaults live, and the startup run that applies pack defaults (ADR 0011). */
public final class ConfigHub {
	private ConfigHub() {
	}

	/** config/cakeui/pack-defaults.json5: the pack's values (pack-owned). */
	public static Path packDefaultsFile() {
		return Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("pack-defaults.json5");
	}

	/** config/cakeui/pack-defaults-applied.json5: what CakeUI last wrote (per player, never shipped). */
	public static Path recordFile() {
		return Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("pack-defaults-applied.json5");
	}

	/** Every config map: mods' own (CakeUI's jar first among them), then config/cakeui/configmaps/, then the layout's. */
	public static ConfigMaps.Loaded loadMaps(Path layoutFolder) {
		List<String> problems = new ArrayList<>();
		List<ConfigMaps.Source> sources = new ArrayList<>();
		List<Platform.ModLayoutFolder> mods = new ArrayList<>(Services.PLATFORM.modAssetFolders("configmaps"));
		// CakeUI's bundled maps first, so a mod's own map for itself replaces CakeUI's copy
		mods.sort((a, b) -> a.modId().equals(CakeUI.MOD_ID) ? -1 : b.modId().equals(CakeUI.MOD_ID) ? 1 : a.modId().compareTo(b.modId()));
		for (Platform.ModLayoutFolder f : mods) {
			sources.addAll(ConfigMaps.scan(f.folder(), f.modId() + ":assets/" + f.modId() + "/configmaps", problems));
		}
		sources.addAll(ConfigMaps.scan(Services.PLATFORM.configDir().resolve(CakeUI.MOD_ID).resolve("configmaps"), "config/cakeui/configmaps", problems));
		if (layoutFolder != null) {
			sources.addAll(ConfigMaps.scan(layoutFolder.resolve("configmaps"), layoutFolder.getFileName() + "/configmaps", problems));
		}
		ConfigMaps.Loaded loaded = ConfigMaps.load(sources);
		problems.addAll(loaded.problems());
		return new ConfigMaps.Loaded(loaded.maps(), problems);
	}

	/** The pack defaults file, or {@link PackDefaults#NONE} when there is none or it can't be read. */
	public static PackDefaults readPackDefaults(List<String> problems) {
		Path file = packDefaultsFile();
		if (!Files.isRegularFile(file)) {
			return PackDefaults.NONE;
		}
		try {
			return PackDefaults.read(Json5.parse(Files.readString(file, StandardCharsets.UTF_8), "config/cakeui/pack-defaults.json5"), problems);
		} catch (Exception e) {
			problems.add("config/cakeui/pack-defaults.json5: " + e.getMessage());
			return PackDefaults.NONE;
		}
	}

	/** The hub's model: every map with hub on whose mod is loaded, with the pack defaults (problems are logged). */
	public static HubModel buildHub(Path layoutFolder) {
		List<String> problems = new ArrayList<>();
		ConfigMaps.Loaded maps = loadMaps(layoutFolder);
		problems.addAll(maps.problems());
		PackDefaults defaults = readPackDefaults(problems);
		for (String p : problems) {
			CakeUI.LOGGER.warn("CakeUI config hub: {}", p);
		}
		return HubModel.build(maps.maps(), defaults, Services.PLATFORM.gameDir(), Services.PLATFORM::isModLoaded,
				Services.PLATFORM::modVersion);
	}

	/**
	 * Applies the pack defaults (Fabric: from the preLaunch entrypoint, before other mods read their configs). Never
	 * throws: a problem is logged and the game starts as it would have without CakeUI.
	 */
	public static void applyPackDefaults() {
		try {
			Path file = packDefaultsFile();
			if (!Files.isRegularFile(file)) {
				return;  // no pack defaults: nothing to do, and nothing read
			}
			List<String> problems = new ArrayList<>();
			PackDefaults defaults = PackDefaults.read(Json5.parse(Files.readString(file, StandardCharsets.UTF_8),
					"config/cakeui/pack-defaults.json5"), problems);
			Path recordPath = recordFile();
			PackDefaults.Record record = PackDefaults.Record.read(
					Files.isRegularFile(recordPath) ? Files.readString(recordPath, StandardCharsets.UTF_8) : null, problems);
			ConfigMaps.Loaded maps = loadMaps(Config.load().layoutFolder());
			problems.addAll(maps.problems());
			PackDefaults.Report rep = defaults.apply(maps.maps(), record, Services.PLATFORM.gameDir(),
					Services.PLATFORM::isModLoaded, Services.PLATFORM::modVersion);
			problems.addAll(rep.problems);
			if (rep.recordChanged) {
				Path tmp = recordPath.resolveSibling(recordPath.getFileName() + ".cakeui-tmp");
				Files.createDirectories(recordPath.getParent());
				Files.writeString(tmp, record.text(), StandardCharsets.UTF_8);
				Files.move(tmp, recordPath, StandardCopyOption.REPLACE_EXISTING);
			}
			CakeUI.LOGGER.info("CakeUI pack defaults: {} written, {} kept (the player's), {} already right, {} problem(s)",
					rep.written, rep.kept, rep.unchanged, problems.size());
			for (String line : rep.lines) {
				CakeUI.LOGGER.info("CakeUI pack defaults: {}", line);
			}
			for (String p : problems) {
				CakeUI.LOGGER.warn("CakeUI pack defaults: {}", p);
			}
		} catch (Exception e) {
			CakeUI.LOGGER.error("CakeUI pack defaults: not applied: {}", e.toString());
		}
	}
}
