package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Expr;
import io.github.harrythecake.cakeui.expr.Px;
import io.github.harrythecake.cakeui.mixin.SplashTextRendererAccessor;
import io.github.harrythecake.cakeui.mixin.SplashTextResourceSupplierAccessor;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Prop;
import io.github.harrythecake.cakeui.model.PropKinds;
import io.github.harrythecake.cakeui.render.Renderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.resource.SplashTextResourceSupplier;
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
	/** The main menu's ports, elements and buttons. */
	public static final ScreenPorts PORTS = new ScreenPorts(() -> Ui.part("main_menu"));
	private static final Map<Node, Node> SPLASH_TEXT = new WeakHashMap<>();
	private static String splash = "";
	private static String version = "";
	private static float fade = 1;

	private TitleMenu() {
	}

	public static Node part() {
		return PORTS.node();
	}

	public static Node port(String name) {
		return PORTS.port(name);
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
		PORTS.beginFrame(screen, mouseX, mouseY);
	}

	// ---- background ----

	/** True when vanilla should draw its panorama and overlay. */
	public static boolean vanillaBackground() {
		return PORTS.vanilla("background");
	}

	public static void drawBackground(DrawContext ctx, float alpha) {
		Node p = port("background");
		if (p != null) {
			Ui.renderer().drawElements(ctx, p, alpha * Ui.renderer().number(p, "opacity", 1));
		}
	}

	// ---- moved/scaled vanilla pieces ----

	public static boolean vanillaPiece(String name, DrawContext ctx, double pivotX, double pivotY, Runnable draw) {
		return PORTS.vanillaPiece(name, ctx, pivotX, pivotY, draw);
	}

	public static void drawPortElements(String name, DrawContext ctx, float alpha) {
		PORTS.drawPortElements(name, ctx, alpha);
	}

	public static float portOpacity(String name) {
		return PORTS.portOpacity(name);
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

	// ---- elements, buttons ----

	public static void drawElements(Screen screen, DrawContext ctx, String layer, float alpha) {
		PORTS.drawElements(screen, ctx, layer, alpha);
	}
}
