package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.model.Geometry;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.render.Renderer;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

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
	private static final Map<ClickableWidget, Node> SKINS = new WeakHashMap<>();
	private static final Map<ClickableWidget, int[]> ORIGINAL = new WeakHashMap<>();

	/** The pause menu: the HUD's built-in subwindow `pause` (only the full menu, not F3+Esc's "Game Paused"). */
	public static final ScreenPorts PAUSE = new ScreenPorts(() -> Ui.builtin("hud", "pause"));

	/** The player's inventory (survival; the creative inventory is a different screen). */
	public static final ScreenPorts INVENTORY = new ScreenPorts(() -> Ui.builtin("hud", "inventory"));

	/** Connecting, loading terrain, preparing a world, and the like (LoadingPart.kind). */
	public static final ScreenPorts LOADING = new ScreenPorts(() -> Ui.part("loading_screen"));

	private final Supplier<Node> node;

	public ScreenPorts(Supplier<Node> node) {
		this.node = node;
	}

	/** The ports for a vanilla screen CakeUI can redraw, or null for any other screen. */
	public static ScreenPorts of(Screen screen) {
		if (screen instanceof TitleScreen) {
			return TitleMenu.PORTS;
		}
		if (screen instanceof GameMenuScreen g && g.shouldShowMenu()) {
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

	public void drawPortElements(String name, DrawContext ctx, float alpha) {
		Node p = port(name);
		if (p != null) {
			Ui.renderer().drawElements(ctx, p, alpha);
		}
	}

	/**
	 * A screen's background: vanilla's dim (unless the background port hides it; with its opacity),
	 * the port's elements, then the screen's back layer.
	 */
	public void drawBackground(Screen screen, DrawContext ctx, Runnable vanilla) {
		if (vanilla("background")) {
			ctx.setShaderColor(1, 1, 1, portOpacity("background"));
			vanilla.run();
			ctx.setShaderColor(1, 1, 1, 1);
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
		double w = MinecraftClient.getInstance().getWindow().getScaledWidth();
		double h = MinecraftClient.getInstance().getWindow().getScaledHeight();
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
	public void drawPortElementsIn(String name, DrawContext ctx, int x, int y, int w, int h) {
		Node p = port(name);
		if (p != null) {
			Ui.renderer().drawElementsIn(ctx, p, x, y, w, h, 1);
		}
	}

	/** Runs a vanilla draw call with the port's offset and scale; returns false when the port hides it. */
	public boolean vanillaPiece(String name, DrawContext ctx, double pivotX, double pivotY, Runnable draw) {
		Node p = port(name);
		if (p == null) {
			draw.run();
			return true;
		}
		Renderer r = Ui.renderer();
		if (r.bool(p, "hide", false)) {
			return false;
		}
		// the window's GUI size (= any screen's size; there is no screen under the launch overlay)
		double w = MinecraftClient.getInstance().getWindow().getScaledWidth();
		double h = MinecraftClient.getInstance().getWindow().getScaledHeight();
		double x = r.length(p, null, "x", Env.AXIS_X, w, h, 0);
		double y = r.length(p, null, "y", Env.AXIS_Y, w, h, 0);
		double s = r.number(p, "scale", 1);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + pivotX, y + pivotY, 0);
		ctx.getMatrices().scale((float) s, (float) s, 1);
		ctx.getMatrices().translate(-pivotX, -pivotY, 0);
		draw.run();
		ctx.getMatrices().pop();
		return true;
	}

	// ---- elements and clicks ----

	/** The screen's elements and subwindows of one layer; "back" also applies the buttons port. */
	public void drawElements(Screen screen, DrawContext ctx, String layer, float alpha) {
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

	/** A button's id for the buttons port: its label's translation key, else its text. */
	public static String id(ClickableWidget w) {
		Text message = w.getMessage();
		if (message.getContent() instanceof TranslatableTextContent t) {
			return t.getKey();
		}
		return message.getString();
	}

	/** After the screen's init: remember where vanilla put each button, then apply the buttons port. */
	public void afterInit(Screen screen) {
		SKINS.clear();
		for (ClickableWidget w : Screens.getButtons(screen)) {
			ORIGINAL.put(w, new int[] {w.getX(), w.getY(), w.getWidth(), w.getHeight()});
		}
		if (node() != null) {
			applyButtons(screen);
		}
	}

	private static Node override(Map<String, Node> buttons, ClickableWidget w, int index) {
		Node o = buttons.get(id(w));
		if (o == null) {
			o = buttons.get("#" + index);
		}
		return o != null ? o : buttons.get("*");
	}

	/** Moves, resizes, hides, relabels and skins the buttons the layout names. Every frame (positions may move). */
	public void applyButtons(Screen screen) {
		Node port = port("buttons");
		if (port == null) {
			return;
		}
		Renderer r = Ui.renderer();
		List<ClickableWidget> list = Screens.getButtons(screen);
		for (int i = 0; i < list.size(); i++) {
			ClickableWidget w = list.get(i);
			Node o = override(port.buttons, w, i);
			if (o == null) {
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
			if (w.getX() != bx || w.getY() != by) {
				w.setPosition(bx, by);
			}
			if (w.getWidth() != bw || w.getHeight() != bh) {
				w.setDimensions(bw, bh);
			}
			if (o.has("text")) {
				String t = r.word(o, "text", "");
				if (!t.equals(w.getMessage().getString())) {
					w.setMessage(Text.literal(t));
				}
			}
			if (o.customStyle) {
				SKINS.put(w, o);
			} else {
				SKINS.remove(w);
			}
		}
	}

	/** The skin to draw instead of this button's vanilla look, or null. */
	public static Node skin(ClickableWidget w) {
		if (SKINS.isEmpty() || Ui.renderer() == null) {
			return null;
		}
		return SKINS.get(w);
	}

	/** Dev mode: each button's id printed above it. */
	public static void drawButtonIds(Screen screen, DrawContext ctx) {
		if (!Ui.devMode() || !Ui.config().showButtonIds) {
			return;
		}
		var tr = MinecraftClient.getInstance().textRenderer;
		List<ClickableWidget> list = Screens.getButtons(screen);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 400);
		for (int i = 0; i < list.size(); i++) {
			ClickableWidget w = list.get(i);
			String label = id(w) + "  #" + i + (w.visible ? "" : "  (hidden)");
			int x = w.getX();
			int y = Math.max(0, w.getY() - 9);
			ctx.fill(x - 1, y - 1, x + tr.getWidth(label) + 1, y + 8, 0xC0000000);
			ctx.drawText(tr, label, x, y, 0xFF55FFFF, false);
		}
		ctx.getMatrices().pop();
	}
}
