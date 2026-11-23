package io.github.harrythecake.cakeupdater;

/**
 * Named in our loader metadata as "cakeconfighub:reload": CakeConfighub calls {@link #cakeconfighubReloadConfig()} right after
 * it wrote config/cakeupdater/config.json5 (ADR 0013). No CakeConfighub dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		CakeUpdater.reloadConfig();
	}
}
