package io.github.harrythecake.caketext.translator.fabric;

import io.github.harrythecake.caketext.translator.ui.TranslatorScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "configure" button. Mod Menu is optional; {@code /cttranslate} opens the same screen. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return TranslatorScreen::new;
	}
}
