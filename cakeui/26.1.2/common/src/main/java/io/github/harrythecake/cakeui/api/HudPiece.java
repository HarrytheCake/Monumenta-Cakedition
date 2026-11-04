package io.github.harrythecake.cakeui.api;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD piece a mod adds (see {@link CakeUIApi#addHudPiece}). CakeUI draws it every HUD frame
 * (never with F1) and treats it like a vanilla piece: a layout's "hud" part can name it as a port
 * to hide, move, scale or fade it and to draw elements with it. With no layout it is drawn as is.
 */
public interface HudPiece {
	/**
	 * Draws the piece where it belongs on a screen of {@code screenWidth} x {@code screenHeight} GUI
	 * pixels. Render thread, every frame: keep it cheap. A piece that throws is logged once and then
	 * skipped until the game restarts.
	 */
	void draw(GuiGraphicsExtractor context, int screenWidth, int screenHeight, float tickDelta);

	/** The point a layout's "scale" grows the piece from (GUI pixels); its centre is a good choice. */
	default double pivotX(int screenWidth) {
		return screenWidth / 2.0;
	}

	default double pivotY(int screenHeight) {
		return screenHeight / 2.0;
	}
}
