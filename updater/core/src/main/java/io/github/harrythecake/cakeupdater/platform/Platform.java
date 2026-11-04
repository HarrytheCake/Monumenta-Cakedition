package io.github.harrythecake.cakeupdater.platform;

import java.nio.file.Path;
import java.util.Optional;

/** What Cakedition Updater needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge" (also the Modrinth loader name). */
	String loader();

	/** The game folder (.minecraft). */
	Path gameDir();

	/** The game's config folder (config/). */
	Path configDir();

	boolean isModLoaded(String modId);

	/** A loaded mod's version ("minecraft" = the game's version), if the loader knows it. */
	Optional<String> modVersion(String modId);

	/** The file this mod was loaded from (a jar; a folder in a dev run), to start the updater helper from a copy. */
	Path modFile(String modId);
}
