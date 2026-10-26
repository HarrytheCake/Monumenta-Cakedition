package io.github.harrythecake.caketext.core.module;

/**
 * Connects CakeText to another mod. Declare an implementation in your {@code fabric.mod.json}:
 * <pre>
 * "entrypoints": { "caketext": ["com.example.MyCakeTextModule"] }
 * </pre>
 * Implementations must not load the other mod's classes unless {@link #isAvailable()} is true:
 * reach them by reflection, or keep them in a class that's only touched after the check.
 */
public interface CakeTextModule {
	/** Short id shown in the settings, e.g. {@code umm}. */
	String id();

	/** False when the mod this module talks to isn't installed; the module is then skipped. */
	default boolean isAvailable() {
		return true;
	}

	/**
	 * Adds context keys with {@link io.github.harrythecake.caketext.core.context.ContextRegistry#register}.
	 * {@code shard} and {@code area} are recorded with every collected string and can be used in scopes.
	 */
	default void registerContext() {
	}

	/** Extra text sources (the collector calls this): pass text the other mod shows to {@code sink}. */
	default void registerSources(TextSink sink) {
	}
}
