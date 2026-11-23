package io.github.harrythecake.caketext.translator;

/**
 * Named in our loader metadata as "cakeui:reload": CakeUI's config hub calls {@link #cakeuiReloadConfig()} right after
 * it wrote config/caketext/translator.json (CakeUI ADR 0011). No CakeUI dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeuiReloadConfig() {
		TranslationService s = TranslatorMod.service();
		if (s != null) {
			s.setConfig(TranslatorConfig.load());
		}
	}
}
