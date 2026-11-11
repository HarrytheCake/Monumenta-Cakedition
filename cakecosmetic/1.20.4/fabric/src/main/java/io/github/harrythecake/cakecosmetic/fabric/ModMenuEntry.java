package io.github.harrythecake.cakecosmetic.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakecosmetic.GalleryScreen;

/** Mod Menu's settings button opens the gallery. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return GalleryScreen::open;
	}
}
