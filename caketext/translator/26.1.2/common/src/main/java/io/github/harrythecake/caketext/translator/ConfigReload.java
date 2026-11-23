package io.github.harrythecake.caketext.translator;

/**
 * Named in our loader metadata as "cakeconfighub:reload": CakeConfighub calls {@link #cakeconfighubReloadConfig()} right after
 * it wrote config/caketext/translator.json (ADR 0013). No CakeConfighub dependency.
 */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		TranslationService s = TranslatorMod.service();
		if (s != null) {
			s.setConfig(TranslatorConfig.load());
		}
	}
}
