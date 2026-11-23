package io.github.harrythecake.cakemap;

/**
 * Named in our loader metadata as "cakeconfighub:reload": CakeConfighub calls {@link #cakeconfighubReloadConfig()} right after
 * it wrote config/cakemap/config.json (ADR 0013).
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		CakeMap.reloadSettings();
	}
}
