package io.github.harrythecake.cakebook.platform;

import java.nio.file.Path;

/** What CakeBook needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (holds cakebook/books/). */
	Path gameDir();

	boolean isModLoaded(String modId);
}
