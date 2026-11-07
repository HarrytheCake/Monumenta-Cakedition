package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.CrosshairStyles;
import io.github.harrythecake.cakeui.edit.PortEdit;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The crosshair style (HUD editor, E4): called where vanilla draws its crosshair sprite (not the
 * attack indicator after it). True = a style drew instead.
 */
public final class HudCrosshair {
	private HudCrosshair() {
	}

	public static boolean draw(GuiGraphics g, float cx, float cy) {
		if (!HudPart.active()) {
			return false;
		}
		PortEdit e = HudEditor.current().get("crosshair");
		if (e == null || !CrosshairStyles.is(e.element) || CrosshairStyles.shape(e.element).equals("vanilla")) {
			return false;
		}
		HudAdded.rendererFor("crosshair").drawCrosshair(g, e.element, cx, cy);
		return true;
	}
}
