package io.github.harrythecake.cakemap.platform;

import java.nio.file.Path;

/** What CakeMap needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (.minecraft). */
	Path gameDir();
}
