package io.github.harrythecake.cakeconfighub.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakeconfighub.CakeConfighubClient;

/** Mod Menu's Configure button opens the hub's mod list. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CakeConfighubClient::hubScreen;
	}
}
