package io.github.harrythecake.cakeui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Xform;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.mixin.InGameHudAccessor;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The HUD part: its ports (every vanilla HUD piece but chat and F3), its own elements and
 * subwindows. Each mentioned port's settings are read once per frame into a {@link Port}, so the
 * many small vanilla draw calls only read fields; with no HUD in the layout, no personal edits and
 * the HUD editor closed, every hook is one boolean check.
 *
 * <p>Personal edits (the HUD editor, ADR 0010) sit on top of the layout: hide and opacity replace the
 * layout's, and a move/scale is an outer transform of the whole port (the vanilla piece and its
 * elements), computed from where the piece was measured ({@link HudBounds}).
 */
public final class HudPart {
	/** One port's settings this frame. */
	public static final class Port {
		@Nullable
		final Node node;
		/** The HUD editor's transform of the whole port (identity when not moved). */
		Xform outer = Xform.IDENTITY;
		/** The HUD editor's opacity also fades the port's own elements (the whole port, Q9). */
		float elementAlpha = 1;
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
		/** pre() pushed the editor's transform. */
		boolean outerOpen;
		@Nullable
		String boundsBefore;

		Port(@Nullable Node node) {
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
	public static void beginFrame(GuiGraphics ctx) {
		Node part = benchOff ? null : part();
		Map<String, PortEdit> edits = benchOff ? Map.of() : HudEditor.current();
		boolean editing = !benchOff && HudEditor.editing();
		active = part != null || !edits.isEmpty() || editing;
		PORTS.clear();
		if (!active) {
			return;
		}
		width = ctx.guiWidth();
		height = ctx.guiHeight();
		if (part != null) {
			Renderer r = Ui.renderer();
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
		if (editing) {
			// every piece is measured while the editor is open
			for (String name : HudEditor.pieces()) {
				PORTS.computeIfAbsent(name, k -> new Port(null));
			}
		}
		for (Map.Entry<String, PortEdit> e : edits.entrySet()) {
			PortEdit edit = e.getValue();
			Port p = PORTS.computeIfAbsent(e.getKey(), k -> new Port(null));
			if (edit.hide != null) {
				p.hide = edit.hide;
			}
			if (edit.opacity != null) {
				p.opacity = edit.opacity;
				p.elementAlpha = edit.opacity;
			}
			if (edit.mode != null || edit.scale != null) {
				p.outer = Placement.transform(edit, HudBounds.orDefault(e.getKey(), width, height), width, height);
			}
		}
	}

	/** End of the HUD: what was measured this frame becomes the known place of each piece. */
	public static void endFrame() {
		if (active) {
			HudBounds.endFrame();
		}
	}

	/** Pushes the editor's transform of the whole port; true when something was pushed. */
	private static boolean pushOuter(GuiGraphics ctx, Port p) {
		if (p.outer.identity()) {
			return false;
		}
		ctx.pose().pushPose();
		ctx.pose().translate(p.outer.tx(), p.outer.ty(), 0);
		ctx.pose().scale((float) p.outer.s(), (float) p.outer.s(), 1);
		return true;
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
	public static void piece(String name, GuiGraphics ctx, double pivotX, double pivotY, boolean elementsLater, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		boolean outer = pushOuter(ctx, p);
		String before = HudBounds.begin(name, p.outer);
		if (!p.hide) {
			boolean moved = p.x != 0 || p.y != 0 || p.scale != 1;
			if (moved) {
				ctx.pose().pushPose();
				ctx.pose().translate(p.x + pivotX, p.y + pivotY, 0);
				ctx.pose().scale((float) p.scale, (float) p.scale, 1);
				ctx.pose().translate(-pivotX, -pivotY, 0);
			}
			if (p.opacity != 1) {
				ctx.setColor(1, 1, 1, p.opacity);
			}
			vanilla.run();
			if (p.opacity != 1) {
				ctx.setColor(1, 1, 1, 1);
			}
			if (moved) {
				ctx.pose().popPose();
			}
		}
		if (!elementsLater) {
			drawPortElements(ctx, p);
		}
		HudBounds.end(before);
		if (outer) {
			ctx.pose().popPose();
		}
	}

	/**
	 * NeoForge's per-layer events, before a vanilla piece: true = hide it (cancel the layer). Else the
	 * piece is moved/scaled around (pivotX, pivotY) and faded until {@link #post}.
	 */
	public static boolean pre(String name, GuiGraphics ctx, double pivotX, double pivotY) {
		Port p = port(name);
		if (p == null) {
			return false;
		}
		p.shown = true;
		if (p.hide) {
			boolean outer = pushOuter(ctx, p);
			drawPortElements(ctx, p);
			if (outer) {
				ctx.pose().popPose();
			}
			return true;
		}
		p.outerOpen = pushOuter(ctx, p);
		p.boundsBefore = HudBounds.begin(name, p.outer);
		p.moved = p.x != 0 || p.y != 0 || p.scale != 1;
		if (p.moved) {
			ctx.pose().pushPose();
			ctx.pose().translate(p.x + pivotX, p.y + pivotY, 0);
			ctx.pose().scale((float) p.scale, (float) p.scale, 1);
			ctx.pose().translate(-pivotX, -pivotY, 0);
		}
		if (p.opacity != 1) {
			ctx.setColor(1, 1, 1, p.opacity);
		}
		p.open = true;
		return false;
	}

	/** After a vanilla piece that {@link #pre} let through: undoes its transform, then the port's elements. */
	public static void post(String name, GuiGraphics ctx) {
		Port p = port(name);
		if (p == null || !p.open) {
			return;
		}
		p.open = false;
		if (p.opacity != 1) {
			ctx.setColor(1, 1, 1, 1);
		}
		if (p.moved) {
			ctx.pose().popPose();
		}
		drawPortElements(ctx, p);
		HudBounds.end(p.boundsBefore);
		if (p.outerOpen) {
			ctx.pose().popPose();
			p.outerOpen = false;
		}
	}

	/** Records where a text port (action bar, title) draws, for the HUD editor; returns what {@link HudBounds#end} needs. */
	@Nullable
	public static String beginText(Port p, String name) {
		return HudBounds.begin(name, p.outer);
	}

	/** Whether the action bar ("actionBar") or the title ("title") shows anything this frame. */
	public static boolean textShowing(String name) {
		return !(name.equals("actionBar") ? actionBar() : title()).isEmpty();
	}

	/** The action bar and title move through vanilla's own translate: offset it and scale from there. */
	public static void translate(String name, PoseStack matrices, float x, float y, float z, Runnable vanilla) {
		Port p = port(name);
		if (p == null) {
			vanilla.run();
			return;
		}
		p.shown = true;
		if (!p.outer.identity()) {
			matrices.translate(p.outer.tx(), p.outer.ty(), 0);
			matrices.scale((float) p.outer.s(), (float) p.outer.s(), 1);
		}
		matrices.translate(x + (float) p.x, y + (float) p.y, z);
		if (p.scale != 1) {
			matrices.scale((float) p.scale, (float) p.scale, 1);
		}
	}

	/** Elements of the ports whose pieces were drawn in many calls (status bars, action bar, title). */
	public static void drawLateElements(GuiGraphics ctx, String... names) {
		for (String name : names) {
			Port p = port(name);
			if (p != null && p.shown && p.node != null && !p.node.children.isEmpty()) {
				boolean outer = pushOuter(ctx, p);
				drawPortElements(ctx, p);
				if (outer) {
					ctx.pose().popPose();
				}
			}
		}
	}

	private static void drawPortElements(GuiGraphics ctx, Port p) {
		if (!p.elementsDrawn && p.node != null && !p.node.children.isEmpty()) {
			p.elementsDrawn = true;
			Ui.renderer().drawElements(ctx, p.node, p.elementAlpha);
		}
	}

	/** The HUD's own elements and subwindows of one layer (not with F1). */
	public static void drawLayer(GuiGraphics ctx, String layer) {
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
