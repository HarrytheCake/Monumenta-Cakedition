package io.github.harrythecake.cakeui;

import org.jetbrains.annotations.Nullable;
import io.github.harrythecake.cakeui.edit.PortEdit;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.model.Geometry;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * What every vanilla screen a layout can redraw has in common: a node with ports (the main menu
 * part, the pause subwindow, …), its elements in a back and a front layer, its buttons port and
 * CakeUI's own button clicks. Each method returns at once when the layout doesn't mention the
 * screen (ADR 0006).
 */
public final class ScreenPorts {
	private static final Map<AbstractWidget, Node> SKINS = new WeakHashMap<>();
	private static final Map<AbstractWidget, int[]> ORIGINAL = new WeakHashMap<>();

	/** The pause menu: the HUD's built-in subwindow `pause` (only the full menu, not F3+Esc's "Game Paused"). */
	public static final ScreenPorts PAUSE = new ScreenPorts("pause", () -> Ui.builtin("hud", "pause"));

	/** The player's inventory (survival; the creative inventory is a different screen). */
	public static final ScreenPorts INVENTORY = new ScreenPorts("inventory", () -> Ui.builtin("hud", "inventory"));

	/** Connecting, loading terrain, preparing a world, and the like (LoadingPart.kind). */
	public static final ScreenPorts LOADING = new ScreenPorts("loading_screen", () -> Ui.part("loading_screen"));

	private final Supplier<Node> node;
	/** The part's id, the editor's key prefix: "main_menu", "pause", "inventory", "loading_screen", "game_launch". */
	private final String part;
	/** Where the layout (or vanilla) put each button, before the player's edits: the editor's inner box. */
	private static final Map<AbstractWidget, int[]> LAYOUT_BOX = new WeakHashMap<>();

	public ScreenPorts(String part, Supplier<Node> node) {
		this.part = part;
		this.node = node;
	}

	public String part() {
		return part;
	}

	// ---- the player's edits (the UI editor, ADR 0010 E6): keys "<part>/<piece>" in the HUD edit files ----

	/** The edit key of a piece of this screen ("main_menu/logo", "pause/button:menu.returnToGame"). */
	public String key(String piece) {
		return part + "/" + piece;
	}

	@Nullable
	PortEdit edit(String piece) {
		Map<String, PortEdit> m = HudEditor.current();
		return m.isEmpty() ? null : m.get(key(piece));
	}

	/** Any edit for this screen (or the editor open): pieces need the slower, edited path. */
	boolean hasEdits() {
		if (HudEditor.editing()) {
			return true;
		}
		String prefix = part + "/";
		for (String k : HudEditor.current().keySet()) {
			if (k.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/** A button's box before the player's edits (the layout's or vanilla's place): {x, y, w, h}. */
	static int[] innerBox(AbstractWidget w) {
		int[] b = LAYOUT_BOX.get(w);
		if (b == null) {
			b = ORIGINAL.get(w);
		}
		return b != null ? b : new int[] {w.getX(), w.getY(), w.getWidth(), w.getHeight()};
	}

	/** The edit key of a button: "button:<id>", or "button:#<index>" when the id is taken by an earlier one. */
	static String buttonKey(Screen screen, AbstractWidget w) {
		List<AbstractWidget> list = buttons(screen);
		String id = id(w);
		for (AbstractWidget o : list) {
			if (o == w) {
				break;
			}
			if (id(o).equals(id)) {
				return "button:#" + list.indexOf(w);
			}
		}
		return "button:" + id;
	}

	/** The ports for a vanilla screen CakeUI can redraw, or null for any other screen. */
	public static ScreenPorts of(Screen screen) {
		if (screen instanceof TitleScreen) {
			return TitleMenu.PORTS;
		}
		if (screen instanceof PauseScreen g && g.showsPauseMenu()) {
			return PAUSE;
		}
		if (screen instanceof InventoryScreen) {
			return INVENTORY;
		}
		if (LoadingPart.kind(screen) != null) {
			return LOADING;
		}
		return null;
	}

	/** The screen's node in the active layout, or null (then the screen stays vanilla). */
	public Node node() {
		return node.get();
	}

	public Node port(String name) {
		Node n = node();
		return n == null ? null : n.ports.get(name);
	}

	/** Start of the screen's render. */
	public void beginFrame(Screen screen, int mouseX, int mouseY) {
		Renderer r = Ui.renderer();
		if (r != null && node() != null) {
			r.beginFrame(screen.width, screen.height, mouseX, mouseY, true);
		}
	}

	// ---- ports ----

	/** True when vanilla should draw the piece of this port (no port, or the port doesn't hide it). */
	public boolean vanilla(String name) {
		Node p = port(name);
		return p == null || !Ui.renderer().bool(p, "hide", false);
	}

	public float portOpacity(String name) {
		Node p = port(name);
		return p == null ? 1 : (float) Ui.renderer().number(p, "opacity", 1);
	}

	public void drawPortElements(String name, GuiGraphics ctx, float alpha) {
		Node p = port(name);
		if (p != null) {
			Ui.renderer().drawElements(ctx, p, alpha);
		}
	}

	/**
	 * A screen's background: vanilla's dim (unless the background port hides it; with its opacity),
	 * the port's elements, then the screen's back layer.
	 */
	public void drawBackground(Screen screen, GuiGraphics ctx, Runnable vanilla) {
		if (vanilla("background")) {
			ctx.setColor(1, 1, 1, portOpacity("background"));
			vanilla.run();
			ctx.setColor(1, 1, 1, 1);
		}
		drawPortElements("background", ctx, 1);
		drawElements(screen, ctx, "back", 1);
	}

	/** A port's x, y (GUI pixels) and scale, for moving a vanilla piece by hand. */
	public double[] offset(String name) {
		Node p = port(name);
		if (p == null) {
			return new double[] {0, 0, 1};
		}
		Renderer r = Ui.renderer();
		double w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		double h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		return new double[] {
			r.length(p, null, "x", Env.AXIS_X, w, h, 0),
			r.length(p, null, "y", Env.AXIS_Y, w, h, 0),
			r.number(p, "scale", 1)};
	}

	/** A text line's port text: the port's own, or vanilla's. */
	public String text(String name, String vanilla) {
		Node p = port(name);
		return p == null || !p.has("text") ? vanilla : Ui.renderer().word(p, "text", vanilla);
	}

	/** A text line's colour: the port's, its alpha times vanilla's (which carries fades); else vanilla's. */
	public int color(String name, int vanilla) {
		Node p = port(name);
		if (p == null || !p.has("color")) {
			return vanilla;
		}
		int c = Ui.renderer().colorValue(p, "color", vanilla | 0xFF000000);
		int vanillaAlpha = (vanilla >>> 24) == 0 ? 255 : vanilla >>> 24;
		int a = Math.round((c >>> 24) * vanillaAlpha / 255.0F);
		return (a << 24) | (c & 0xFFFFFF);
	}

	/** The port's elements inside a box in GUI pixels (the inventory panel). */
	public void drawPortElementsIn(String name, GuiGraphics ctx, int x, int y, int w, int h) {
		Node p = port(name);
		if (p != null) {
			Ui.renderer().drawElementsIn(ctx, p, x, y, w, h, 1);
		}
	}

	/** Runs a vanilla draw call with the port's offset and scale; returns false when the port hides it. */
	public boolean vanillaPiece(String name, GuiGraphics ctx, double pivotX, double pivotY, Runnable draw) {
		Node p = port(name);
		boolean edited = hasEdits();
		if (p == null && !edited) {
			draw.run();
			return true;
		}
		PortEdit e = edited ? edit(name) : null;
		Renderer r = Ui.renderer();
		boolean layoutHides = p != null && r.bool(p, "hide", false);
		if (e != null && e.hide != null ? e.hide : layoutHides) {
			return false;
		}
		// the window's GUI size (= any screen's size; there is no screen under the launch overlay)
		int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		double x = p == null ? 0 : r.length(p, null, "x", Env.AXIS_X, w, h, 0);
		double y = p == null ? 0 : r.length(p, null, "y", Env.AXIS_Y, w, h, 0);
		double s = p == null ? 1 : r.number(p, "scale", 1);
		Placement.Xform outer = e == null || (e.mode == null && e.scale == null) ? Placement.Xform.IDENTITY
				: Placement.transform(e, HudBounds.orDefault(key(name), w, h), w, h);
		ctx.pose().pushPose();
		ctx.pose().translate(outer.tx(), outer.ty(), 0);
		ctx.pose().scale((float) outer.s(), (float) outer.s(), 1);
		String before = edited ? HudBounds.begin(key(name), outer) : null;
		ctx.pose().pushPose();
		ctx.pose().translate(x + pivotX, y + pivotY, 0);
		ctx.pose().scale((float) s, (float) s, 1);
		ctx.pose().translate(-pivotX, -pivotY, 0);
		draw.run();
		ctx.pose().popPose();
		if (edited) {
			HudBounds.hooked(key(name));
			HudBounds.end(before);
		}
		ctx.pose().popPose();
		return true;
	}

	// ---- elements and clicks ----

	/** The screen's elements and subwindows of one layer; "back" also applies the buttons port. */
	public void drawElements(Screen screen, GuiGraphics ctx, String layer, float alpha) {
		Node n = node();
		if (n == null) {
			return;
		}
		if (layer.equals("back")) {
			applyButtons(screen);
		}
		Ui.renderer().drawPart(ctx, n, layer, alpha);
	}

	public boolean click(Screen screen, int button) {
		Renderer r = Ui.renderer();
		return r != null && node() != null && r.mouseClicked(screen, button);
	}

	// ---- buttons ----

	/** The screen's buttons (every widget it draws), in the order it added them: the `#index` of the buttons port. */
	public static List<AbstractWidget> buttons(Screen screen) {
		List<AbstractWidget> out = new java.util.ArrayList<>();
		for (Object r : ((io.github.harrythecake.cakeui.mixin.ScreenAccessor) screen).cakeui$renderables()) {
			if (r instanceof AbstractWidget w) {
				out.add(w);
			}
		}
		return out;
	}

	/** A button's id for the buttons port: its label's translation key, else its text. */
	public static String id(AbstractWidget w) {
		Component message = w.getMessage();
		if (message.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return message.getString();
	}

	/** After the screen's init: remember where vanilla put each button, then apply the buttons port. */
	public void afterInit(Screen screen) {
		SKINS.clear();
		for (AbstractWidget w : buttons(screen)) {
			ORIGINAL.put(w, new int[] {w.getX(), w.getY(), w.getWidth(), w.getHeight()});
			LAYOUT_BOX.remove(w);
		}
		if (node() != null || hasEdits()) {
			applyButtons(screen);
		}
	}

	private static Node override(Map<String, Node> buttons, AbstractWidget w, int index) {
		Node o = buttons.get(id(w));
		if (o == null) {
			o = buttons.get("#" + index);
		}
		return o != null ? o : buttons.get("*");
	}

	/** Moves, resizes, hides, relabels and skins the buttons the layout names. Every frame (positions may move). */
	public void applyButtons(Screen screen) {
		Node port = node() == null ? null : port("buttons");
		boolean edited = hasEdits();
		if (port == null && !edited) {
			return;
		}
		Renderer r = Ui.renderer();
		List<AbstractWidget> list = buttons(screen);
		for (int i = 0; i < list.size(); i++) {
			AbstractWidget w = list.get(i);
			Node o = port == null ? null : override(port.buttons, w, i);
			if (o == null) {
				if (edited) {
					int[] orig = ORIGINAL.computeIfAbsent(w, k -> new int[] {k.getX(), k.getY(), k.getWidth(), k.getHeight()});
					LAYOUT_BOX.put(w, orig);
					applyEdit(screen, w, orig[0], orig[1], orig[2], orig[3], true);
				}
				continue;
			}
			int[] orig = ORIGINAL.computeIfAbsent(w, k -> new int[] {k.getX(), k.getY(), k.getWidth(), k.getHeight()});
			r.env.self = null;
			w.visible = r.bool(o, "visible", true);
			double sw = screen.width;
			double sh = screen.height;
			int bw = (int) Math.round(r.length(o, null, "width", Env.AXIS_X, sw, sh, orig[2]));
			int bh = (int) Math.round(r.length(o, null, "height", Env.AXIS_Y, sw, sh, orig[3]));
			int bx = orig[0];
			int by = orig[1];
			if (o.has("anchor") || o.has("x") || o.has("y")) {
				double[] a = Geometry.anchor(r.word(o, "anchor", "top_left"));
				double[] org = o.has("origin") ? Geometry.anchor(r.word(o, "origin", "top_left")) : a;
				bx = (int) Math.round(Geometry.place(sw, a[0], r.length(o, null, "x", Env.AXIS_X, sw, sh, 0), bw, org[0]));
				by = (int) Math.round(Geometry.place(sh, a[1], r.length(o, null, "y", Env.AXIS_Y, sw, sh, 0), bh, org[1]));
			}
			if (edited) {
				LAYOUT_BOX.put(w, new int[] {bx, by, bw, bh});
				applyEdit(screen, w, bx, by, bw, bh, w.visible);
			} else {
				if (w.getX() != bx || w.getY() != by) {
					w.setPosition(bx, by);
				}
				if (w.getWidth() != bw || w.getHeight() != bh) {
					w.setSize(bw, bh);
				}
			}
			if (o.has("text")) {
				String t = r.word(o, "text", "");
				if (!t.equals(w.getMessage().getString())) {
					w.setMessage(Component.literal(t));
				}
			}
			if (o.customStyle) {
				SKINS.put(w, o);
			} else {
				SKINS.remove(w);
			}
		}
	}

	/** A button at its layout/vanilla box (x, y, w, h), then the player's edit on top: moved, scaled, hidden. */
	private void applyEdit(Screen screen, AbstractWidget w, int x, int y, int bw, int bh, boolean visible) {
		PortEdit e = edit(buttonKey(screen, w));
		boolean show = e != null && e.hide != null ? !e.hide : visible;
		if (e != null && (e.mode != null || e.scale != null)) {
			Placement.Box b = Placement.transform(e, new Placement.Box(x, y, bw, bh), screen.width, screen.height).apply(new Placement.Box(x, y, bw, bh));
			x = (int) Math.round(b.x());
			y = (int) Math.round(b.y());
			bw = Math.max(1, (int) Math.round(b.w()));
			bh = Math.max(1, (int) Math.round(b.h()));
		}
		w.visible = show;
		if (w.getX() != x || w.getY() != y) {
			w.setPosition(x, y);
		}
		if (w.getWidth() != bw || w.getHeight() != bh) {
			w.setSize(bw, bh);
		}
	}

	/** The skin to draw instead of this button's vanilla look, or null. */
	public static Node skin(AbstractWidget w) {
		if (SKINS.isEmpty() || Ui.renderer() == null) {
			return null;
		}
		return SKINS.get(w);
	}

	/** Dev mode: each button's id printed above it. */
	public static void drawButtonIds(Screen screen, GuiGraphics ctx) {
		if (!Ui.devMode() || !Ui.config().showButtonIds) {
			return;
		}
		var tr = Minecraft.getInstance().font;
		List<AbstractWidget> list = buttons(screen);
		ctx.pose().pushPose();
		ctx.pose().translate(0, 0, 400);
		for (int i = 0; i < list.size(); i++) {
			AbstractWidget w = list.get(i);
			String label = id(w) + "  #" + i + (w.visible ? "" : "  (hidden)");
			int x = w.getX();
			int y = Math.max(0, w.getY() - 9);
			ctx.fill(x - 1, y - 1, x + tr.width(label) + 1, y + 8, 0xC0000000);
			ctx.drawString(tr, label, x, y, 0xFF55FFFF, false);
		}
		ctx.pose().popPose();
	}
}
