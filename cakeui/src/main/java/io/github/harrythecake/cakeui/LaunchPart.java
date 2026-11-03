package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The game launching screen: Mojang's overlay while resources load, at start and on every resource
 * reload. Ports: background (the red fill), logo, bar. Before the first load finishes Minecraft's
 * own font doesn't exist yet, so text here should use a layout font.
 */
public final class LaunchPart {
	/** Ports and elements of the `game_launch` part. */
	public static final ScreenPorts PORTS = new ScreenPorts(() -> Ui.part("game_launch"));
	private static double progress;
	private static double fade = 1;
	private static boolean reloading;
	private static float barOpacity = 1;

	private LaunchPart() {
	}

	public static Node part() {
		return PORTS.node();
	}

	static double progress() {
		return progress;
	}

	static double fade() {
		return fade;
	}

	static boolean reloading() {
		return reloading;
	}

	/** Start of the overlay's frame (no cursor: the overlay takes no clicks). */
	public static void beginFrame(DrawContext ctx, float loadProgress, boolean reload) {
		progress = loadProgress;
		reloading = reload;
		Renderer r = Ui.renderer();
		if (r != null) {
			r.beginFrame(ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 0, 0, false);
		}
	}

	/** The overlay's background: its port elements and the back layer, at the fill's opacity. */
	public static void drawBack(DrawContext ctx, float alpha) {
		Renderer r = Ui.renderer();
		Node p = PORTS.port("background");
		if (p != null) {
			r.drawElements(ctx, p, alpha * r.number(p, "opacity", 1));
		}
		r.drawPart(ctx, part(), "back", alpha);
	}

	/** The logo's opacity this frame (vanilla's fade times the port's opacity). */
	public static float logoAlpha(float vanilla) {
		fade = vanilla;
		return vanilla * PORTS.portOpacity("logo");
	}

	/** The bar's opacity this frame (it fades out when loading is done). */
	public static void bar(float opacity) {
		barOpacity = opacity;
	}

	/** End of the overlay's frame: logo and bar port elements, then the front layer. */
	public static void drawFront(DrawContext ctx) {
		Renderer r = Ui.renderer();
		PORTS.drawPortElements("logo", ctx, (float) fade);
		PORTS.drawPortElements("bar", ctx, barOpacity);
		r.drawPart(ctx, part(), "front", fade);
		ctx.draw();
		SelfTest.launchFrame(reloading);
	}
}
