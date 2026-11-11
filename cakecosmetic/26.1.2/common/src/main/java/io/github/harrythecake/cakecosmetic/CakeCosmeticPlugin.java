package io.github.harrythecake.cakecosmetic;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.CakeUIPlugin;

/**
 * CakeCosmetic's hook into CakeUI (ADR 0008): it fills the main menu's and the loading screens'
 * background whenever the active layout doesn't redraw them, and tells layouts what is shown.
 */
public final class CakeCosmeticPlugin implements CakeUIPlugin {
	@Override
	public void register(CakeUIApi api) {
		api.fillPort("main_menu/background", CakeCosmetic.MOD_ID, MenuBackground::fill);
		api.fillPort("loading_screen/background", CakeCosmetic.MOD_ID, LoadingBackground::fill);
		api.registerValue("cakecosmetic.menu", MenuBackground::current);
		api.registerValue("cakecosmetic.loading", LoadingBackground::current);
	}
}
