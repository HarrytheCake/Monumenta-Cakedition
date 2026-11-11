package io.github.harrythecake.cakebook.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harrythecake.cakebook.CakeBookClient;

/** Mod Menu's "configure" button opens the book. Mod Menu is optional; /cakebook does the same. */
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CakeBookClient::bookScreen;
	}
}
