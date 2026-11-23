package io.github.harrythecake.cakeconfighub.api;

import io.github.harrythecake.cakeconfighub.configmap.ConfigReloads;

/**
 * CakeConfighub's API for other mods (optional: a mod without a dependency names a reload class in its metadata
 * instead, key {@code cakeconfighub:reload} with {@code public static void cakeconfighubReloadConfig()}).
 */
public final class CakeConfighubApi {
	/** 1: {@link #registerConfigReload}. */
	public static final int API_VERSION = 1;

	private CakeConfighubApi() {
	}

	/**
	 * Lets the hub reload a mod's config right after it saved that mod's file (called on the render thread). Replaces
	 * the mod's {@code cakeconfighub:reload} metadata hook, if it has one.
	 */
	public static void registerConfigReload(String modId, Runnable reload) {
		ConfigReloads.register(modId, reload);
	}
}
