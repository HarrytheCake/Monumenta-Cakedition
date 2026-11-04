package io.github.harrythecake.cakeui.api;

import net.minecraft.client.gui.GuiGraphics;

/** What a mod's element sees while it draws: its box, its state and its properties' current values. */
public interface ElementContext {
	/** Draw with this; its matrix is at the element's top-left corner (GUI pixels). */
	GuiGraphics drawContext();

	/** The element's size in GUI pixels. */
	double width();

	double height();

	/** 0…1: the element's opacity times its parents'. Apply it to your colours. */
	float opacity();

	boolean hovered();

	boolean pressed();

	/** GUI pixels per design pixel (multiply design sizes by this). */
	double designScale();

	/** A NUMBER or LENGTH property (lengths come back in GUI pixels), eased between states like built-in props. */
	double number(String key, double fallback);

	/** A COLOR property as ARGB, with the element's opacity already applied. */
	int color(String key, int fallback);

	boolean bool(String key, boolean fallback);

	/** A TEXT or WORD property, expressions filled in. */
	String text(String key, String fallback);
}
