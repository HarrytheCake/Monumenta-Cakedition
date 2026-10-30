package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Expr;
import io.github.harrythecake.cakeui.expr.Px;
import io.github.harrythecake.cakeui.mixin.SplashTextRendererAccessor;
import io.github.harrythecake.cakeui.mixin.SplashTextResourceSupplierAccessor;
import io.github.harrythecake.cakeui.model.Geometry;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Prop;
import io.github.harrythecake.cakeui.model.PropKinds;
import io.github.harrythecake.cakeui.render.Renderer;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The main menu part: its ports (background, logo, splash, version, buttons) and its elements.
 * Each method returns at once when the active layout doesn't mention the thing (ADR 0006).
 */
public final class TitleMenu {
	private static final Random RANDOM = Random.create();
	private static final Map<ClickableWidget, Node> SKINS = new WeakHashMap<>();
	private static final Map<ClickableWidget, int[]> ORIGINAL = new WeakHashMap<>();
	private static final Map<Node, Node> SPLASH_TEXT = new WeakHashMap<>();
	private static String splash = "";
	private static String version = "";
	private static float fade = 1;

	private TitleMenu() {
	}

	public static Node part() {
		return Ui.part("main_menu");
	}

	public static Node port(String name) {
		Node part = part();
		return part == null ? null : part.ports.get(name);
	}

	static double fade() {
		return fade;
	}

	static String splash() {
		return splash;
	}

	static String version() {
		return version;
	}

	/** Start of TitleScreen.render. */
	public static void beginFrame(Screen screen, int mouseX, int mouseY, float alpha) {
		fade = alpha;
		Renderer r = Ui.renderer();
		if (r != null && part() != null) {
			r.beginFrame(screen.width, screen.height, mouseX, mouseY, true);
		}
	}

	// ---- background ----

	/** True when vanilla should draw its panorama and overlay. */
	public static boolean vanillaBackground() {
		Node p = port("background");
		return p == null || !Ui.renderer().bool(p, "hide", false);
	}

	public static void drawBackground(DrawContext ctx, float alpha) {
		Node p = port("background");
		if (p != null) {
			Ui.renderer().drawElements(ctx, p, alpha * Ui.renderer().number(p, "opacity", 1));
		}
	}

	// ---- moved/scaled vanilla pieces ----

	/** Runs a vanilla draw call with the port's offset and scale; returns false when the port hides it. */
	public static boolean vanillaPiece(String name, DrawContext ctx, double pivotX, double pivotY, Runnable draw) {
		Node p = port(name);
		if (p == null) {
			draw.run();
			return true;
		}
		Renderer r = Ui.renderer();
		if (r.bool(p, "hide", false)) {
			return false;
		}
		Screen screen = MinecraftClient.getInstance().currentScreen;
		double w = screen == null ? 0 : screen.width;
		double h = screen == null ? 0 : screen.height;
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

	public static void drawPortElements(String name, DrawContext ctx, float alpha) {
		Node p = port(name);
		if (p != null) {
			Ui.renderer().drawElements(ctx, p, alpha);
		}
	}

	public static float portOpacity(String name) {
		Node p = port(name);
		return p == null ? 1 : (float) Ui.renderer().number(p, "opacity", 1);
	}

	/** The version line's text: the port's own (which can use {menu.version}), or vanilla's. */
	public static String versionText(String vanilla) {
		version = vanilla;
		Node p = port("version");
		if (p == null || !p.has("text")) {
			return vanilla;
		}
		return Ui.renderer().word(p, "text", vanilla);
	}

	// ---- splash ----

	/** Picks the splash for a new title screen: the port's list (plus vanilla's when mixed), else vanilla's pick. */
	public static SplashTextRenderer pickSplash(SplashTextResourceSupplier supplier) {
		Node p = port("splash");
		SplashTextRenderer vanilla = null;
		if (p != null && p.splashes != null) {
			boolean mix = !p.has("mixVanilla") || Ui.renderer().bool(p, "mixVanilla", true);
			List<String> all = new ArrayList<>(p.splashes);
			if (mix) {
				all.addAll(((SplashTextResourceSupplierAccessor) supplier).cakeui$splashTexts());
			}
			if (!all.isEmpty()) {
				splash = all.get(RANDOM.nextInt(all.size()));
				return new SplashTextRenderer(splash);
			}
		}
		vanilla = supplier.get();
		splash = vanilla == null ? "" : ((SplashTextRendererAccessor) vanilla).cakeui$text();
		return vanilla;
	}

	/** Draws the splash; false = let vanilla draw it. */
	public static boolean drawSplash(DrawContext ctx, float alpha) {
		Node p = port("splash");
		if (p == null) {
			return false;
		}
		Renderer r = Ui.renderer();
		if (r.bool(p, "hide", false)) {
			r.drawElements(ctx, p, alpha);
			return true;
		}
		if (!p.customStyle) {
			return false;
		}
		Node text = SPLASH_TEXT.computeIfAbsent(p, TitleMenu::splashNode);
		r.drawSingle(ctx, text, alpha * r.number(p, "opacity", 1), splash);
		r.drawElements(ctx, p, alpha);
		return true;
	}

	/** A text element made from the splash port, with vanilla's look where the port says nothing. */
	private static Node splashNode(Node port) {
		Node n = new Node("splash", "text", port.scope, port.file, port.line);
		n.props.putAll(port.props);
		def(n, "text", PropKinds.Kind.TEXT, "{menu.splash}");
		def(n, "anchor", PropKinds.Kind.WORD, "top");
		def(n, "origin", PropKinds.Kind.WORD, "center");
		def(n, "x", PropKinds.Kind.LENGTH, new Px(123));
		def(n, "y", PropKinds.Kind.LENGTH, new Px(65));
		def(n, "size", PropKinds.Kind.LENGTH, new Px(14));
		def(n, "color", PropKinds.Kind.COLOR, "#ffff00");
		def(n, "shadow", PropKinds.Kind.BOOL, true);
		def(n, "transform.rotateZ", PropKinds.Kind.NUMBER, -20.0);
		return n;
	}

	private static void def(Node n, String key, PropKinds.Kind kind, Object value) {
		if (!n.props.containsKey(key)) {
			Expr e = value instanceof String s && s.contains("{")
					? io.github.harrythecake.cakeui.expr.Template.compile(s, io.github.harrythecake.cakeui.model.Loader.resolver(n.scope, true, false))
					: Expr.constant(value);
			n.props.put(key, new Prop(key, kind, e, n.line));
		}
	}

	// ---- elements ----

	public static void drawElements(Screen screen, DrawContext ctx, String layer, float alpha) {
		Node part = part();
		if (part == null) {
			return;
		}
		if (layer.equals("back")) {
			applyButtons(screen);
		}
		Ui.renderer().drawPart(ctx, part, layer, alpha);
	}

	public static boolean click(Screen screen, int button) {
		Renderer r = Ui.renderer();
		return r != null && part() != null && r.mouseClicked(screen, button);
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

	/** After the screen's init: remember where vanilla put each button. */
	public static void afterInit(Screen screen) {
		SKINS.clear();
		for (ClickableWidget w : Screens.getButtons(screen)) {
			ORIGINAL.put(w, new int[] {w.getX(), w.getY(), w.getWidth(), w.getHeight()});
		}
		if (part() != null) {
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
	static void applyButtons(Screen screen) {
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
