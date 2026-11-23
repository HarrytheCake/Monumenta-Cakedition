package io.github.harrythecake.cakeconfighub.platform;

import java.nio.file.Path;
import java.util.List;

/** What CakeConfighub needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (.minecraft); config map file paths are relative to it. */
	Path gameDir();

	boolean isModLoaded(String modId);

	/**
	 * Every loaded mod's `assets/<modid>/<sub>/` folder (a path inside its jar, or its resources folder in a
	 * development run), sorted by mod id; mods without one are left out. Config maps use `configmaps`.
	 */
	default List<ModAssetFolder> modAssetFolders(String sub) {
		return List.of();
	}

	/** A loaded mod's version as its loader reports it ("minecraft" = the game version), or null. */
	default String modVersion(String modId) {
		return null;
	}

	/**
	 * A text value from a loaded mod's own metadata (Fabric: fabric.mod.json's `custom` block; NeoForge: the mod's
	 * `[modproperties.<modid>]`), or null. Used for `cakeconfighub:reload` (the mod's config reload class).
	 */
	default String modMetadata(String modId, String key) {
		return null;
	}

	/** A mod's `assets/<modid>/<sub>/` folder (a path inside its jar). */
	record ModAssetFolder(String modId, Path folder) {
	}
}
