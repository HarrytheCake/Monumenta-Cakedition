package io.github.harrythecake.caketext.core.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** What CakeText needs from the mod loader. Each loader part of the core mod implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	boolean isModLoaded(String modId);

	/**
	 * Implementations of {@code type} that mods offer: on Fabric the entrypoint {@code name} in their
	 * {@code fabric.mod.json}, on NeoForge {@code META-INF/services/<type>} in their jar.
	 */
	<T> List<Provided<T>> entrypoints(String name, Class<T> type);

	/** One implementation: who offers it (a mod id, or a module name on NeoForge) and how to make it. */
	record Provided<T>(String owner, Supplier<T> instance) {
	}
}
