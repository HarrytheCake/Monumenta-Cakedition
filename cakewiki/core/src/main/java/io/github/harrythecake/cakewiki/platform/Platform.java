package io.github.harrythecake.cakewiki.platform;

import java.nio.file.Path;

/** What CakeWiki needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game folder (cakewiki/cache/ lives in it). */
	Path gameDir();

	/** The game's config folder (config/). */
	Path configDir();

	boolean isModLoaded(String modId);

	/** This mod's version as the loader reads it from the metadata, or "dev". */
	String modVersion(String modId);
}
