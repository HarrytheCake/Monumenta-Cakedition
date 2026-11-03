package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.mixin.InGameHudAccessor;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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
	public static void beginFrame(DrawContext ctx) {
		Node part = benchOff ? null : part();
		active = part != null;
		PORTS.clear();
		if (!active) {
			return;
		}
		Renderer r = Ui.renderer();
		width = ctx.getScaledWindowWidth();
		height = ctx.getScaledWindowHeight();
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
	public static void piece(String name, DrawContext ctx, double pivotX, double pivotY, boolean elementsLater, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		if (!p.hide) {
			boolean moved = p.x != 0 || p.y != 0 || p.scale != 1;
			if (moved) {
				ctx.getMatrices().push();
				ctx.getMatrices().translate(p.x + pivotX, p.y + pivotY, 0);
				ctx.getMatrices().scale((float) p.scale, (float) p.scale, 1);
				ctx.getMatrices().translate(-pivotX, -pivotY, 0);
			}
			if (p.opacity != 1) {
				ctx.setShaderColor(1, 1, 1, p.opacity);
			}
			vanilla.run();
			if (p.opacity != 1) {
				ctx.setShaderColor(1, 1, 1, 1);
			}
			if (moved) {
				ctx.getMatrices().pop();
			}
		}
		if (!elementsLater) {
			drawPortElements(ctx, p);
		}
	}

	/** The action bar and title move through vanilla's own translate: offset it and scale from there. */
	public static void translate(String name, MatrixStack matrices, float x, float y, float z, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		matrices.translate(x + (float) p.x, y + (float) p.y, z);
		if (p.scale != 1) {
			matrices.scale((float) p.scale, (float) p.scale, 1);
		}
	}

	/** Elements of the ports whose pieces were drawn in many calls (status bars, action bar, title). */
	public static void drawLateElements(DrawContext ctx, String... names) {
		for (String name : names) {
			Port p = port(name);
			if (p != null && p.shown) {
				drawPortElements(ctx, p);
			}
		}
	}

	private static void drawPortElements(DrawContext ctx, Port p) {
		if (!p.elementsDrawn && !p.node.children.isEmpty()) {
			p.elementsDrawn = true;
			Ui.renderer().drawElements(ctx, p.node, 1);
		}
	}

	/** The HUD's own elements and subwindows of one layer (not with F1). */
	public static void drawLayer(DrawContext ctx, String layer) {
		if (!active || MinecraftClient.getInstance().options.hudHidden) {
			return;
		}
		Node part = part();
		if (part != null) {
			Ui.renderer().drawPart(ctx, part, layer, 1);
		}
	}

	// ---- game values ----

	private static InGameHudAccessor hud() {
		InGameHud hud = MinecraftClient.getInstance().inGameHud;
		return (InGameHudAccessor) hud;
	}

	static String actionBar() {
		InGameHudAccessor h = hud();
		Text t = h.cakeui$overlayMessage();
		return t == null || h.cakeui$overlayRemaining() <= 0 ? "" : t.getString();
	}

	static String title() {
		InGameHudAccessor h = hud();
		Text t = h.cakeui$title();
		return t == null || h.cakeui$titleRemainTicks() <= 0 ? "" : t.getString();
	}

	static String subtitle() {
		InGameHudAccessor h = hud();
		Text t = h.cakeui$subtitle();
		return t == null || h.cakeui$titleRemainTicks() <= 0 ? "" : t.getString();
	}
}
