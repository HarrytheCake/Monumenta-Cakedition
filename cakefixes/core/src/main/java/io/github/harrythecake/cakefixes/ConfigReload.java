package io.github.harrythecake.cakefixes;

/**
 * Named in our loader metadata as "cakeconfighub:reload": CakeConfighub calls {@link #cakeconfighubReloadConfig()} right after
 * it wrote config/cakefixes/config.json5 (ADR 0013). No CakeConfighub dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		CakeFixes.reloadConfig();
	}
}
