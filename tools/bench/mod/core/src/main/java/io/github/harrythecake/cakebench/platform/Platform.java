package io.github.harrythecake.cakebench.platform;

import java.nio.file.Path;
import java.util.Map;

/** What CakeBench needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	/** The game folder (.minecraft or the dev run folder). */
	Path gameDir();

	boolean isModLoaded(String modId);

	/** Every loaded mod, id → version, sorted by id; without the loader's own parts, Java and Minecraft. */
	Map<String, String> mods();

	/**
	 * Runs a command through the loader's client-side command dispatcher (spark's {@code sparkc}, our own
	 * {@code cakebench}). It never reaches the server: a command the client dispatcher doesn't know just fails here.
	 *
	 * @return null when the command ran, else why it didn't (nothing was sent anywhere)
	 */
	String runClientCommand(String command);
}
