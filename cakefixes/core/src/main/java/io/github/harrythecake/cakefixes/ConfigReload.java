package io.github.harrythecake.cakefixes;

/**
 * Named in our loader metadata as "cakeui:reload": CakeUI's config hub calls {@link #cakeuiReloadConfig()} right after
 * it wrote config/cakefixes/config.json5 (CakeUI ADR 0011). No CakeUI dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeuiReloadConfig() {
		CakeFixes.reloadConfig();
	}
}
