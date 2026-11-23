package io.github.harrythecake.cakechat;

/**
 * Named in our loader metadata as "cakeconfighub:reload": CakeConfighub calls {@link #cakeconfighubReloadConfig()} right after
 * it wrote config/cakechat/config.json (ADR 0013). No CakeConfighub dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		CakeChat.reloadConfig();
	}
}
