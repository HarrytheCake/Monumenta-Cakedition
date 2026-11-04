package io.github.harrythecake.cakeui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CakeUI: layouts that redraw Minecraft's menus, loading screens, HUD and window. With no layout
 * chosen (the default) nothing is drawn or changed. The client side starts in CakeUIClient (each
 * Minecraft version's common part), called by the loader's entry class.
 */
public final class CakeUI {
	public static final String MOD_ID = "cakeui";
	public static final Logger LOGGER = LoggerFactory.getLogger("CakeUI");

	private CakeUI() {
	}
}
