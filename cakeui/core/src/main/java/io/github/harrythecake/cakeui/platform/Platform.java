package io.github.harrythecake.cakeui.platform;

import java.nio.file.Path;

/** What CakeUI needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (.minecraft), where layout paths are relative to. */
	Path gameDir();

	boolean isModLoaded(String modId);
}
