package io.github.harrythecake.cakeui;

import org.joml.Matrix3x2fStack;
import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.mixin.InGameHudAccessor;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The HUD part: its ports (every vanilla HUD piece but chat and F3), its own elements and
 * subwindows. Each mentioned port's settings are read once per frame into a {@link Port}, so the
 * many small vanilla draw calls only read fields; with no HUD in the layout every hook is one
 * boolean check.
 */
public final class HudPart {
	/** One port's settings this frame. */
	public static final class Port {
		final Node node;
		public boolean hide;
		double x;
		double y;
		double scale = 1;
		public float opacity = 1;
		/** Vanilla would have drawn the piece this frame (its replacement elements are drawn then). */
		boolean shown;
		boolean elementsDrawn;
		/** pre() pushed a transform / colour that post() must undo. */
		boolean open;
		boolean moved;

		Port(Node node) {
			this.node = node;
		}
	}

	private static final Map<String, Port> PORTS = new HashMap<>();
	private static boolean active;
	private static int width;
	private static int height;
	/** For the self-test's HUD benchmark: act as if the layout had no HUD. */
	static boolean benchOff;

	private HudPart() {
	}

	public static boolean active() {
		return active;
	}

	@Nullable
	static Node part() {
		return Ui.part("hud");
	}

	/** Start of InGameHud.render: reads every mentioned port once. */
	public static void beginFrame(GuiGraphicsExtractor ctx) {
		Node part = benchOff ? null : part();
		active = part != null;
		PORTS.clear();
		if (!active) {
			return;
		}
		Renderer r = Ui.renderer();
		width = ctx.guiWidth();
		height = ctx.guiHeight();
		r.beginFrame(width, height, 0, 0, false);
		r.env.self = null;
		for (Map.Entry<String, Node> e : part.ports.entrySet()) {
			Node n = e.getValue();
			Port p = new Port(n);
			p.hide = r.bool(n, "hide", false);
			p.x = r.length(n, null, "x", Env.AXIS_X, width, height, 0);
			p.y = r.length(n, null, "y", Env.AXIS_Y, width, height, 0);
			p.scale = r.number(n, "scale", 1);
			p.opacity = (float) r.number(n, "opacity", 1);
			PORTS.put(e.getKey(), p);
		}
	}

	/** The port's settings this frame, or null when the layout doesn't mention it (then the piece stays vanilla). */
	@Nullable
	public static Port port(String name) {
		return active ? PORTS.get(name) : null;
	}

	/**
	 * A vanilla HUD piece: hidden, or drawn moved/scaled around (pivotX, pivotY) and faded; then the
	 * port's elements, unless `elementsLater` (pieces drawn in many calls draw them once at the end).
	 */
	public static void piece(String name, GuiGraphicsExtractor ctx, double pivotX, double pivotY, boolean elementsLater, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		if (!p.hide) {
			boolean moved = p.x != 0 || p.y != 0 || p.scale != 1;
			if (moved) {
				ctx.pose().pushMatrix();
				ctx.pose().translate((float) (p.x + pivotX), (float) (p.y + pivotY));
				ctx.pose().scale((float) p.scale, (float) p.scale);
				ctx.pose().translate((float) (-pivotX), (float) (-pivotY));
			}
			vanilla.run();
			if (moved) {
				ctx.pose().popMatrix();
			}
		}
		if (!elementsLater) {
			drawPortElements(ctx, p);
		}
	}

	/**
	 * NeoForge's per-layer events, before a vanilla piece: true = hide it (cancel the layer). Else the
	 * piece is moved/scaled around (pivotX, pivotY) and faded until {@link #post}.
	 */
	public static boolean pre(String name, GuiGraphicsExtractor ctx, double pivotX, double pivotY) {
		Port p = port(name);
		if (p == null) {
			return false;
		}
		p.shown = true;
		if (p.hide) {
			drawPortElements(ctx, p);
			return true;
		}
		p.moved = p.x != 0 || p.y != 0 || p.scale != 1;
		if (p.moved) {
			ctx.pose().pushMatrix();
			ctx.pose().translate((float) (p.x + pivotX), (float) (p.y + pivotY));
			ctx.pose().scale((float) p.scale, (float) p.scale);
			ctx.pose().translate((float) (-pivotX), (float) (-pivotY));
		}
		p.open = true;
		return false;
	}

	/** After a vanilla piece that {@link #pre} let through: undoes its transform, then the port's elements. */
	public static void post(String name, GuiGraphicsExtractor ctx) {
		Port p = port(name);
		if (p == null || !p.open) {
			return;
		}
		p.open = false;
		if (p.moved) {
			ctx.pose().popMatrix();
		}
		drawPortElements(ctx, p);
	}

	/** Whether the action bar ("actionBar") or the title ("title") shows anything this frame. */
	public static boolean textShowing(String name) {
		return !(name.equals("actionBar") ? actionBar() : title()).isEmpty();
	}

	/** The action bar and title move through vanilla's own translate: offset it and scale from there. */
	public static void translate(String name, Matrix3x2fStack matrices, float x, float y, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		matrices.translate(x + (float) p.x, y + (float) p.y);
		if (p.scale != 1) {
			matrices.scale((float) p.scale, (float) p.scale);
		}
	}

	/** Elements of the ports whose pieces were drawn in many calls (status bars, action bar, title). */
	public static void drawLateElements(GuiGraphicsExtractor ctx, String... names) {
		for (String name : names) {
			Port p = port(name);
			if (p != null && p.shown) {
				drawPortElements(ctx, p);
			}
		}
	}

	private static void drawPortElements(GuiGraphicsExtractor ctx, Port p) {
		if (!p.elementsDrawn && !p.node.children.isEmpty()) {
			p.elementsDrawn = true;
			Ui.renderer().drawElements(ctx, p.node, 1);
		}
	}

	/** The HUD's own elements and subwindows of one layer (not with F1). */
	public static void drawLayer(GuiGraphicsExtractor ctx, String layer) {
		if (!active || Minecraft.getInstance().options.hideGui) {
			return;
		}
		Node part = part();
		if (part != null) {
			Ui.renderer().drawPart(ctx, part, layer, 1);
		}
	}

	// ---- game values ----

	private static InGameHudAccessor hud() {
		Gui hud = Minecraft.getInstance().gui;
		return (InGameHudAccessor) hud;
	}

	static String actionBar() {
		InGameHudAccessor h = hud();
		Component t = h.cakeui$overlayMessage();
		return t == null || h.cakeui$overlayRemaining() <= 0 ? "" : t.getString();
	}

	static String title() {
		InGameHudAccessor h = hud();
		Component t = h.cakeui$title();
		return t == null || h.cakeui$titleRemainTicks() <= 0 ? "" : t.getString();
	}

	static String subtitle() {
		InGameHudAccessor h = hud();
		Component t = h.cakeui$subtitle();
		return t == null || h.cakeui$titleRemainTicks() <= 0 ? "" : t.getString();
	}
}
