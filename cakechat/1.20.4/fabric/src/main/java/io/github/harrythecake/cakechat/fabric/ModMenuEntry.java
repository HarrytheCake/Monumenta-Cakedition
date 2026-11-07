package io.github.harrythecake.cakechat.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakechat.SettingsScreen;

/** Mod Menu's "configure" button opens the settings. Mod Menu is optional; {@code /cakechat} does the same. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::create;
	}
}
