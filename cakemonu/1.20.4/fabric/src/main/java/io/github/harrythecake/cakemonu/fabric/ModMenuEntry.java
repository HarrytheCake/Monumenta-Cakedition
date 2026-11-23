package io.github.harrythecake.cakemonu.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakemonu.SettingsScreen;

/** Mod Menu's "configure" button opens the ability list. Mod Menu is optional; {@code /cakemonu} does the same. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
