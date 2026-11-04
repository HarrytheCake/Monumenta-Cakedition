package io.github.harrythecake.cakeupdater.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakeupdater.BackupsScreen;

/** Mod Menu's "configure" button opens the backups. Mod Menu is optional; {@code /cakeupdate backups} does the same. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BackupsScreen::new;
	}
}
