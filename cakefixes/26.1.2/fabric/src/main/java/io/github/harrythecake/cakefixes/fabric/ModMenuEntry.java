package io.github.harrythecake.cakefixes.fabric;

import io.github.harrythecake.cakefixes.ConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "configure" button opens the switches. Mod Menu is optional; the config file does the same. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
