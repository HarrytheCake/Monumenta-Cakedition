package io.github.harrythecake.cakemap;

/**
 * Named in our loader metadata as "cakeui:reload": CakeUI's config hub calls {@link #cakeuiReloadConfig()} right after
 * it wrote config/cakemap/config.json (CakeUI ADR 0011).
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeuiReloadConfig() {
		CakeMap.reloadSettings();
	}
}
