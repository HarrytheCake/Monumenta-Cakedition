package io.github.harrythecake.caketext.collector.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "configure" button. Mod Menu is optional; {@code /ctcollect} opens the same screen. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CollectorScreen::new;
	}
}
