package io.github.harrythecake.cakemonu;

import net.minecraft.client.gui.screens.Screen;

/**
 * CakeUI is optional: this class only touches CakeUI's API after {@link CakeMonuPlugin} (loaded by CakeUI
 * itself) said its HUD editor is there, so nothing loads CakeUI classes without CakeUI.
 */
public final class CakeUIBridge {
	/** CakeUI 0.3.0+ (API 3) is installed and took CakeMonu's movables. */
	static boolean editor;

	private CakeUIBridge() {
	}

	public static boolean editorAvailable() {
		return editor;
	}

	/** Opens CakeUI's HUD editor; false when it can't (no CakeUI, not in a world). */
	public static boolean openEditor(Screen parent) {
		return editor && io.github.harrythecake.cakeui.api.CakeUIApi.get().openHudEditor(parent);
	}
}
