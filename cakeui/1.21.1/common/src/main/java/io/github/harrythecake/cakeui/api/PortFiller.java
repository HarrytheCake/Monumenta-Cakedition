package io.github.harrythecake.cakeui.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * What a mod draws in place of a vanilla piece when the active layout doesn't mention that port
 * (see {@link CakeUIApi#fillPort}). With no layout at all, the filler is still used.
 */
@FunctionalInterface
public interface PortFiller {
	/**
	 * Draws over the whole screen ({@code screen.width} x {@code screen.height} GUI pixels) instead
	 * of vanilla. Return false when there is nothing to show: then vanilla draws as usual. Render
	 * thread, every frame: keep it cheap. {@code alpha} is the screen's fade-in (the main menu's
	 * first second), else 1. A filler that throws is logged once and treated as false.
	 */
	boolean fill(GuiGraphics context, Screen screen, float alpha);
}
