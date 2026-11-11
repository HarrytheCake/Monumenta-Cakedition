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

	/** A mod's `assets/<modid>/cakeui/` folder (a path inside its jar). */
	record ModLayoutFolder(String modId, Path folder) {
	}
}
