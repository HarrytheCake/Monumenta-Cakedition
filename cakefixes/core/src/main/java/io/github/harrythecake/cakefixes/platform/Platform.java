package io.github.harrythecake.cakefixes.platform;

import java.nio.file.Path;

/** What CakeFixes needs from the mod loader. Each loader part implements it (META-INF/services). */
public interface Platform {
	/** "fabric" or "neoforge". */
	String loader();

	/** The game's config folder (config/). */
	Path configDir();

	boolean isModLoaded(String modId);

	/**
	 * The runtime name of a field, for reflection: Mojang's name where the game runs with Mojang's names
	 * (NeoForge, Fabric on 26.1+, dev environments), else Fabric's intermediary name mapped by the loader.
	 * {@code owner} and {@code desc} are given in intermediary names, as Fabric's mapping resolver wants.
	 */
	String fieldName(String intermediaryOwner, String intermediaryName, String mojangName, String intermediaryDesc);

	/** The runtime name of a class, like {@link #fieldName}; names use dots. */
	String className(String intermediaryName, String mojangName);
}
