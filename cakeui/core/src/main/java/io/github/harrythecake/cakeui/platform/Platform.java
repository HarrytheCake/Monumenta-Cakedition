package io.github.harrythecake.cakeui.platform;

import java.nio.file.Path;
import java.util.List;

/** What CakeUI needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (.minecraft), where layout paths are relative to. */
	Path gameDir();

	boolean isModLoaded(String modId);

	/**
	 * Every loaded mod's default layout folder, `assets/<modid>/cakeui/` inside its jar (or its resources folder in a
	 * development run), sorted by mod id. Mods without one are left out.
	 */
	default List<ModLayoutFolder> modLayoutFolders() {
		return List.of();
	}

	/**
	 * Every loaded mod's `assets/<modid>/<sub>/` folder (a path inside its jar), sorted by mod id; mods without one are
	 * left out. Config maps use `configmaps` (CakeUI's own jar holds the maps for the pack's mods).
	 */
	default List<ModLayoutFolder> modAssetFolders(String sub) {
		return List.of();
	}

	/** A loaded mod's version as its loader reports it ("minecraft" = the game version), or null. */
	default String modVersion(String modId) {
		return null;
	}

	/**
	 * A text value from a loaded mod's own metadata (Fabric: fabric.mod.json's `custom` block; NeoForge: the mod's
	 * `[modproperties.<modid>]`), or null. Used for `cakeui:reload` (the mod's config reload class).
	 */
	default String modMetadata(String modId, String key) {
		return null;
	}

	/** A mod's `assets/<modid>/cakeui/` folder (a path inside its jar). */
	record ModLayoutFolder(String modId, Path folder) {
	}
}
