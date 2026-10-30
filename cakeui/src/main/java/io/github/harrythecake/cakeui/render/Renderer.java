package io.github.harrythecake.cakeui.render;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Expr;
import io.github.harrythecake.cakeui.expr.Values;
import io.github.harrythecake.cakeui.model.Easing;
import io.github.harrythecake.cakeui.model.Geometry;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Prop;
import io.github.harrythecake.cakeui.model.PropKinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a loaded layout's nodes with a DrawContext. One renderer per loaded layout; it owns the
 * layout's textures, fonts and offscreen buffers. Render thread only.
 */
public final class Renderer {
	public final Layout layout;
	public final Env env = new Env();
	private final Textures textures;
	private final Fonts fonts;
	private final Layers layers = new Layers();
	private final Map<Object, Map<Node, NodeState>> states = new WeakHashMap<>();
	private final Object screenOwner = new Object();
	private double screenW;
	private double screenH;
	private double mouseX;
	private double mouseY;
	private boolean cursor;
	private double now;
	private double lastNow = -1;
	private double dt;
	private NodeState topButton;
	private NodeState pressed;
	private String language = "en_us";

	public Renderer(Layout layout) {
		this.layout = layout;
		this.textures = new Textures(layout.root, layout.problems);
		this.fonts = new Fonts(layout, layout.problems);
		env.translator = this::translate;
	}

	private String translate(String key) {
		String s = layout.translate(language, key);
		if (s != null) {
			return s;
		}
		return net.minecraft.client.resource.language.I18n.hasTranslation(key) ? net.minecraft.client.resource.language.I18n.translate(key) : key;
	}

	// ---- frame ----

	/** Call once per frame before drawing: screen size and mouse in GUI pixels. */
	public void beginFrame(double width, double height, double mx, double my, boolean hasCursor) {
		env.frame++;
		screenW = width;
		screenH = height;
		mouseX = mx;
		mouseY = my;
		cursor = hasCursor;
		now = Util.getMeasuringTimeMs() / 1000.0;
		dt = lastNow < 0 ? 0 : Math.min(0.25, now - lastNow);
		lastNow = now;
		double[] s = layout.designScale(width, height);
		env.rx = s[0];
		env.ry = s[1];
		language = MinecraftClient.getInstance().getLanguageManager().getLanguage();
		topButton = null;
	}

	public double rx() {
		return env.rx;
	}

	/** Mouse in -1..1 from the screen centre (0 without a cursor). */
	public double mouseNX() {
		return cursor && screenW > 0 ? Math.max(-1, Math.min(1, (mouseX - screenW / 2) / (screenW / 2))) : 0;
	}

	public double mouseNY() {
		return cursor && screenH > 0 ? Math.max(-1, Math.min(1, (mouseY - screenH / 2) / (screenH / 2))) : 0;
	}

	/** Per-draw settings passed down the tree. */
	private static final class Ctx {
		DrawContext dc;
		Object owner;
		double alpha;
		Boolean forcedHover;
		Geometry.Mat flat;
		double mx;
		double my;
		double d;
		double vpx;
		double vpy;

		Ctx copy() {
			Ctx c = new Ctx();
			c.dc = dc;
			c.owner = owner;
			c.alpha = alpha;
			c.forcedHover = forcedHover;
			c.flat = flat;
			c.mx = mx;
			c.my = my;
			c.d = d;
			c.vpx = vpx;
			c.vpy = vpy;
			return c;
		}
	}

	private Ctx rootCtx(DrawContext dc, Object owner, double alpha) {
		Ctx c = new Ctx();
		c.dc = dc;
		c.owner = owner;
		c.alpha = alpha;
		c.flat = new Geometry.Mat();
		c.mx = mouseX;
		c.my = mouseY;
		c.d = layout.perspective * env.rx;
		c.vpx = screenW / 2;
		c.vpy = screenH / 2;
		return c;
	}

	/** Draws a part's top-level elements and subwindows of one layer ("back" or "front") over the whole screen. */
	public void drawPart(DrawContext dc, Node part, String layer, double alpha) {
		Ctx c = rootCtx(dc, screenOwner, alpha);
		if (part.has("perspective")) {
			c.d = length(part, null, "perspective", Env.AXIS_NONE, screenW, screenH, c.d);
		}
		List<Node> kids = new ArrayList<>();
		for (Node k : part.children) {
			if (word(k, "layer", "back").equals(layer)) {
				kids.add(k);
			}
		}
		drawChildren(c, part, kids, screenW, screenH, null);
	}

	/** Draws the elements of a port over the whole screen. */
	public void drawElements(DrawContext dc, Node container, double alpha) {
		if (container.children.isEmpty()) {
			return;
		}
		Ctx c = rootCtx(dc, screenOwner, alpha);
		drawChildren(c, container, container.children, screenW, screenH, null);
	}

	/** Draws one node (a custom splash, say) over the whole screen. */
	public void drawSingle(DrawContext dc, Node node, double alpha, String text) {
		Ctx c = rootCtx(dc, screenOwner, alpha);
		NodeState st = state(c.owner, node);
		st.text = text;
		drawNode(c, node, screenW, screenH, 0, 0, null, null);
	}

	/** Draws a button override's skin in the button's box; the vanilla button keeps handling clicks. */
	public void drawSkin(DrawContext dc, Object widget, Node override, int x, int y, int w, int h,
			boolean hovered, boolean active, String text, double alpha) {
		Ctx c = rootCtx(dc, widget, alpha);
		c.forcedHover = hovered;
		MatrixStack m = dc.getMatrices();
		m.push();
		m.translate(x, y, 0);
		c.flat = new Geometry.Mat().translate(x, y, 0);
		for (Node child : override.children) {
			NodeState st = state(widget, child);
			st.text = text;
			st.enabled = active;
		}
		NodeState self = state(widget, override);
		self.hovered = hovered;
		self.enabled = active;
		self.text = text;
		self.width = w;
		self.height = h;
		drawChildren(c, override, override.children, w, h, self);
		m.pop();
	}

	// ---- state ----

	private NodeState state(Object owner, Node n) {
		return states.computeIfAbsent(owner, o -> new HashMap<>()).computeIfAbsent(n, NodeState::new);
	}

	// ---- evaluation ----

	private Object eval(Node n, Prop p, Expr e) {
		try {
			return e.eval(env);
		} catch (RuntimeException ex) {
			layout.problems.add(n.file, p.line, "'" + p.key + "' of " + n.name + ": " + ex.getMessage());
			return null;
		}
	}

	/** A prop's value for the node's current state, eased between states when it has a transition. */
	private double animated(Node n, NodeState st, String key, PropKinds.Kind kind, double def) {
		Prop p = n.prop(key);
		if (p == null) {
			return def;
		}
		Expr e = st == null ? p.base : p.pick(st.hovered, st.pressed, !st.enabled);
		if (e == null) {
			e = p.base;
		}
		if (e == null) {
			return def;
		}
		Object v = eval(n, p, e);
		if (v == null) {
			return def;
		}
		double target = switch (kind) {
			case LENGTH -> Values.length(v, env);
			case COLOR -> Values.color(v);
			default -> Values.num(v);
		};
		if (st == null || !p.hasStates()) {
			return target;
		}
		double duration = n.has("transition.duration") ? Values.num(eval(n, n.prop("transition.duration"), n.prop("transition.duration").base)) : 0;
		NodeState.Anim a = st.anims.computeIfAbsent(key, k -> new NodeState.Anim());
		if (!a.started || duration <= 0) {
			a.started = true;
			a.from = a.to = a.current = target;
			return target;
		}
		if (target != a.to) {
			a.from = a.current;
			a.to = target;
			a.startTime = now;
		}
		double t = (now - a.startTime) / duration;
		double k = Easing.apply(word(n, "transition.easing", "ease"), t);
		if (kind == PropKinds.Kind.COLOR) {
			a.current = Values.mixColor((int) (long) a.from, (int) (long) a.to, k);
		} else {
			a.current = a.from + (a.to - a.from) * k;
		}
		return a.current;
	}

	public double length(Node n, Object stOrNull, String key, int axis, double pw, double ph, double def) {
		env.axis = axis;
		env.parentWidth = pw;
		env.parentHeight = ph;
		return animated(n, (NodeState) stOrNull, key, PropKinds.Kind.LENGTH, def);
	}

	private double num(Node n, NodeState st, String key, double def) {
		env.axis = Env.AXIS_NONE;
		return animated(n, st, key, PropKinds.Kind.NUMBER, def);
	}

	private int color(Node n, NodeState st, String key, int def) {
		env.axis = Env.AXIS_NONE;
		return (int) (long) animated(n, st, key, PropKinds.Kind.COLOR, def & 0xFFFFFFFFL);
	}

	public boolean bool(Node n, String key, boolean def) {
		Prop p = n.prop(key);
		if (p == null || p.base == null) {
			return def;
		}
		Object v = eval(n, p, p.base);
		return v == null ? def : Values.bool(v);
	}

	public double number(Node n, String key, double def) {
		return num(n, null, key, def);
	}

	public String word(Node n, String key, String def) {
		Prop p = n.prop(key);
		if (p == null || p.base == null) {
			return def;
		}
		Object v = eval(n, p, p.base);
		return v == null ? def : Values.str(v);
	}

	private String text(Node n, NodeState st, String key) {
		Prop p = n.prop(key);
		if (p == null) {
			return "";
		}
		Expr e = p.pick(st.hovered, st.pressed, !st.enabled);
		if (e == null) {
			e = p.base;
		}
		Object v = e == null ? null : eval(n, p, e);
		return v == null ? "" : Values.str(v);
	}

	// ---- tree ----

	private void drawChildren(Ctx c, Node container, List<Node> kids, double w, double h, NodeState containerState) {
		if (kids.isEmpty()) {
			return;
		}
		env.self = containerState;
		double pad = container.has("padding") ? length(container, containerState, "padding", Env.AXIS_X, w, h, 0) : 0;
		double iw = Math.max(0, w - 2 * pad);
		double ih = Math.max(0, h - 2 * pad);
		String arrange = container.has("arrange") ? word(container, "arrange", "none") : "none";
		List<Node> ordered = kids;
		boolean anyOrder = false;
		for (Node k : kids) {
			anyOrder |= k.has("order");
		}
		if (anyOrder) {
			ordered = new ArrayList<>(kids);
			Map<Node, Double> order = new HashMap<>();
			for (Node k : ordered) {
				order.put(k, number(k, "order", 0));
			}
			ordered.sort(Comparator.comparingDouble(order::get));
		}
		if (arrange.equals("none")) {
			for (Node k : ordered) {
				drawNode(c, k, iw, ih, pad, pad, null, null);
			}
			return;
		}
		boolean row = arrange.equals("row");
		env.self = containerState;
		double gap = container.has("gap") ? length(container, containerState, "gap", row ? Env.AXIS_X : Env.AXIS_Y, w, h, 0) : 0;
		String align = container.has("align") ? word(container, "align", "start") : "start";
		double cursorPos = 0;
		for (Node k : ordered) {
			double[] size = measure(c, k, iw, ih);
			if (size == null) {
				continue;
			}
			double cross = row ? ih - size[1] : iw - size[0];
			double crossPos = switch (align) {
				case "center" -> cross / 2;
				case "end" -> cross;
				default -> 0;
			};
			if (row) {
				drawNode(c, k, iw, ih, pad, pad, cursorPos, crossPos);
				cursorPos += size[0] + gap;
			} else {
				drawNode(c, k, iw, ih, pad, pad, crossPos, cursorPos);
				cursorPos += size[1] + gap;
			}
		}
	}

	/** {width, height} a child will take, or null when it is hidden. */
	private double[] measure(Ctx c, Node n, double pw, double ph) {
		NodeState st = state(c.owner, n);
		env.self = st;
		if (!bool(n, "visible", true)) {
			return null;
		}
		TextLayout tl = n.type.equals("text") ? textLayout(c, n, st, pw, ph) : null;
		double w = length(n, st, "width", Env.AXIS_X, pw, ph, defaultWidth(n, tl, pw));
		double h = length(n, st, "height", Env.AXIS_Y, pw, ph, defaultHeight(n, tl, ph));
		return new double[] {w, h};
	}

	private double defaultWidth(Node n, TextLayout tl, double pw) {
		if (n.fill) {
			return pw;
		}
		return switch (n.type) {
			case "text" -> tl == null ? 0 : tl.width;
			case "image" -> {
				Textures.Tex t = n.has("src") ? textures.get(word(n, "src", ""), n.file, n.line) : null;
				int frames = Math.max(1, (int) number(n, "frames", 1));
				boolean horizontal = word(n, "frameLayout", "vertical").equals("horizontal");
				yield t == null ? 0 : (horizontal ? t.width() / (double) frames : t.width()) * env.rx;
			}
			case "item" -> 16 * env.rx;
			case "button" -> 200 * env.rx;
			case "bar" -> 100 * env.rx;
			default -> pw;
		};
	}

	private double defaultHeight(Node n, TextLayout tl, double ph) {
		if (n.fill) {
			return ph;
		}
		return switch (n.type) {
			case "text" -> tl == null ? 0 : tl.height;
			case "image" -> {
				Textures.Tex t = n.has("src") ? textures.get(word(n, "src", ""), n.file, n.line) : null;
				int frames = Math.max(1, (int) number(n, "frames", 1));
				boolean horizontal = word(n, "frameLayout", "vertical").equals("horizontal");
				yield t == null ? 0 : (horizontal ? t.height() : t.height() / (double) frames) * env.ry;
			}
			case "item" -> 16 * env.ry;
			case "button" -> 40 * env.ry;
			case "bar" -> 8 * env.ry;
			default -> ph;
		};
	}

	/**
	 * Draws a node inside its parent's box (pw × ph, starting at ox, oy). fixedX/fixedY place it in
	 * a row or column instead of by anchor.
	 */
	private void drawNode(Ctx c, Node n, double pw, double ph, double ox, double oy, Double fixedX, Double fixedY) {
		NodeState st = state(c.owner, n);
		env.self = st;
		if (!bool(n, "visible", true)) {
			st.hovered = st.wasHovered = false;
			return;
		}
		st.enabled = bool(n, "enabled", true);
		TextLayout tl = n.type.equals("text") ? textLayout(c, n, st, pw, ph) : null;
		double w = length(n, st, "width", Env.AXIS_X, pw, ph, defaultWidth(n, tl, pw));
		double h = length(n, st, "height", Env.AXIS_Y, pw, ph, defaultHeight(n, tl, ph));
		st.width = w;
		st.height = h;
		double offX = length(n, st, "x", Env.AXIS_X, pw, ph, 0);
		double offY = length(n, st, "y", Env.AXIS_Y, pw, ph, 0);
		double x;
		double y;
		if (fixedX != null) {
			x = ox + fixedX + offX;
			y = oy + fixedY + offY;
		} else {
			double[] a = Geometry.anchor(word(n, "anchor", "top_left"));
			double[] o = n.has("origin") ? Geometry.anchor(word(n, "origin", "top_left")) : a;
			x = ox + Geometry.place(pw, a[0], offX, w, o[0]);
			y = oy + Geometry.place(ph, a[1], offY, h, o[1]);
		}
		double opacity = num(n, st, "opacity", 1) * c.alpha;
		if (opacity <= 0.003) {
			st.hovered = false;
			return;
		}

		double rotX = num(n, st, "transform.rotateX", 0);
		double rotY = num(n, st, "transform.rotateY", 0);
		double rotZ = num(n, st, "transform.rotateZ", 0);
		double scale = num(n, st, "transform.scale", 1);
		double scaleX = num(n, st, "transform.scaleX", 1) * scale;
		double scaleY = num(n, st, "transform.scaleY", 1) * scale;
		double skewX = num(n, st, "transform.skewX", 0);
		double skewY = num(n, st, "transform.skewY", 0);
		double z = length(n, st, "transform.z", Env.AXIS_NONE, pw, ph, 0);
		double pivotX = num(n, st, "transform.pivotX", 0.5) * w;
		double pivotY = num(n, st, "transform.pivotY", 0.5) * h;

		if (n.has("parallax.move") || n.has("parallax.tilt")) {
			if (st.parallaxFrame != env.frame) {
				double tau = num(n, st, "parallax.smoothing", 0);
				double k = tau <= 0 ? 1 : 1 - Math.exp(-dt / tau);
				if (st.parallaxFrame < 0) {
					k = 1;
				}
				st.smoothX += (mouseNX() - st.smoothX) * k;
				st.smoothY += (mouseNY() - st.smoothY) * k;
				st.parallaxFrame = env.frame;
			}
			double depthK = c.d > z ? c.d / (c.d - z) : 1;
			double move = length(n, st, "parallax.move", Env.AXIS_X, pw, ph, 0) * depthK;
			x += st.smoothX * move;
			y += st.smoothY * move;
			double tilt = num(n, st, "parallax.tilt", 0);
			rotY += st.smoothX * tilt;
			rotX -= st.smoothY * tilt;
		}

		Geometry.Mat local = new Geometry.Mat().translate(x + pivotX, y + pivotY, z);
		boolean is3d = rotX != 0 || rotY != 0 || z != 0;
		if (rotY != 0) {
			local.rotateY(rotY);
		}
		if (rotX != 0) {
			local.rotateX(rotX);
		}
		if (rotZ != 0) {
			local.rotateZ(rotZ);
		}
		if (skewX != 0 || skewY != 0) {
			local.skew(skewX, skewY);
		}
		if (scaleX != 1 || scaleY != 1) {
			local.scale(scaleX, scaleY);
		}
		local.translate(-pivotX, -pivotY, 0);
		Geometry.Mat combined = c.flat.copy().mul(local);

		double[] lm = is3d ? Geometry.unproject(combined, c.mx, c.my, c.d, c.vpx, c.vpy) : combined.invert2d(c.mx, c.my);
		boolean inside = lm != null && lm[0] >= 0 && lm[1] >= 0 && lm[0] < w && lm[1] < h;
		st.hovered = c.forcedHover != null ? c.forcedHover : cursor && inside;
		if (st.hovered && !st.wasHovered && n.has("sounds.hover")) {
			Paint.sound(word(n, "sounds.hover", ""));
		}
		st.wasHovered = st.hovered;
		if (!st.hovered && st.pressed && pressed != st) {
			st.pressed = false;
		}
		if (n.type.equals("button") && st.hovered && st.enabled && c.forcedHover == null) {
			topButton = st;
		}

		MatrixStack m = c.dc.getMatrices();
		if (!is3d) {
			m.push();
			m.multiplyPositionMatrix(toJoml(local));
			Ctx inner = c.copy();
			inner.flat = combined;
			inner.alpha = opacity;
			drawContent(inner, n, st, w, h, opacity, tl);
			if (n.isContainer() && !n.children.isEmpty()) {
				Ctx kids = inner;
				if (n.has("perspective")) {
					kids = inner.copy();
					kids.d = length(n, st, "perspective", Env.AXIS_NONE, pw, ph, c.d);
					double[] center = combined.apply(w / 2, h / 2, 0);
					kids.vpx = center[0];
					kids.vpy = center[1];
				}
				drawChildren(kids, n, n.children, w, h, st);
			}
			m.pop();
			return;
		}

		// 3D: draw flat into a buffer (box at margin, margin, so content may spill past its edges),
		// then draw the whole buffer tilted into place
		double margin = Math.min(screenW, screenH) * 0.1;
		c.dc.draw();
		Framebuffer fb = layers.push();
		m.push();
		m.peek().getPositionMatrix().identity().translate((float) margin, (float) margin, 0);
		m.peek().getNormalMatrix().identity();
		Ctx inner = c.copy();
		// buffer space = the box shifted by the margin, for drawing, hit tests and nested 3D alike
		inner.flat = new Geometry.Mat().translate(margin, margin, 0);
		inner.alpha = 1;
		inner.mx = lm == null ? -1e9 : lm[0] + margin;
		inner.my = lm == null ? -1e9 : lm[1] + margin;
		inner.d = n.has("perspective") ? length(n, st, "perspective", Env.AXIS_NONE, pw, ph, c.d) : c.d;
		inner.vpx = margin + w / 2;
		inner.vpy = margin + h / 2;
		drawContent(inner, n, st, w, h, 1, tl);
		if (n.isContainer() && !n.children.isEmpty()) {
			drawChildren(inner, n, n.children, w, h, st);
		}
		c.dc.draw();
		m.pop();
		layers.pop();
		layers.composite(fb, combined.copy().translate(-margin, -margin, 0), screenW, screenH, screenW, screenH, c.d, c.vpx, c.vpy, opacity);
	}

	private static Matrix4f toJoml(Geometry.Mat m) {
		float[] f = new float[16];
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				f[col * 4 + row] = (float) m.m[row * 4 + col];
			}
		}
		return new Matrix4f().set(f);
	}

	// ---- content ----

	private void drawContent(Ctx c, Node n, NodeState st, double w, double h, double opacity, TextLayout tl) {
		env.self = st;
		Matrix4f m = c.dc.getMatrices().peek().getPositionMatrix();
		switch (n.type) {
			case "text" -> drawText(c, n, st, w, opacity, tl);
			case "rect" -> {
				int c1 = Paint.withAlpha(color(n, st, "color", 0xFFFFFFFF), opacity);
				int c2 = n.has("color2") ? Paint.withAlpha(color(n, st, "color2", 0xFFFFFFFF), opacity) : c1;
				Paint.rect(m, 0, 0, (float) w, (float) h, c1, c2, !word(n, "gradient", "vertical").equals("horizontal"));
				outline(n, st, m, w, h, opacity);
			}
			case "image" -> drawImage(n, st, m, w, h, opacity);
			case "bar" -> drawBar(n, st, m, w, h, opacity);
			case "item" -> drawItem(c, n, w, h);
			default -> {
				if (n.has("background")) {
					int bg = Paint.withAlpha(color(n, st, "background", 0), opacity);
					Paint.rect(m, 0, 0, (float) w, (float) h, bg, bg, true);
				}
				outline(n, st, m, w, h, opacity);
				if (n.background != null) {
					Ctx bc = c.copy();
					bc.alpha = opacity;
					bc.forcedHover = st.hovered;
					NodeState bs = state(c.owner, n.background);
					bs.pressed = st.pressed;
					bs.enabled = st.enabled;
					drawNode(bc, n.background, w, h, 0, 0, null, null);
					env.self = st;
				}
			}
		}
	}

	private void outline(Node n, NodeState st, Matrix4f m, double w, double h, double opacity) {
		if (!n.has("outline")) {
			return;
		}
		int oc = Paint.withAlpha(color(n, st, "outline", 0xFFFFFFFF), opacity);
		float t = (float) length(n, st, "outlineWidth", Env.AXIS_NONE, w, h, 1);
		Paint.outline(m, 0, 0, (float) w, (float) h, t, oc);
	}

	/** A text element's lines, scale and size, computed once per draw. */
	private static final class TextLayout {
		TextRenderer renderer;
		List<OrderedText> lines;
		double scale;
		double lineHeight;
		double width;
		double height;
		double[] lineWidths;
	}

	private TextLayout textLayout(Ctx c, Node n, NodeState st, double pw, double ph) {
		TextLayout tl = new TextLayout();
		String str = text(n, st, "text");
		String font = word(n, "font", "");
		MinecraftClient client = MinecraftClient.getInstance();
		double size = length(n, st, "size", Env.AXIS_Y, pw, ph, 9);
		double base = 9;
		Style style = Style.EMPTY;
		TextRenderer tr = client.textRenderer;
		if (!font.isEmpty()) {
			if (font.contains(":")) {
				Identifier id = Identifier.tryParse(font);
				if (id != null) {
					style = style.withFont(id);
				}
			} else if (fonts.has(font)) {
				base = fonts.baseSize(font);
				double pixelScale = size / base * client.getWindow().getScaleFactor();
				TextRenderer custom = fonts.get(font, pixelScale);
				if (custom != null) {
					tr = custom;
				} else {
					base = 9;
				}
			} else {
				layout.problems.add(n.file, n.prop("font").line, "unknown font '" + font + "' (declare it under fonts in window.json5, or use an id like minecraft:uniform)");
			}
		}
		tl.renderer = tr;
		tl.scale = size / base;
		tl.lineHeight = n.has("lineHeight") ? length(n, st, "lineHeight", Env.AXIS_Y, pw, ph, size) : size * (base == 9 ? 1.0 : 1.15);
		Text t = Text.literal(str).setStyle(style);
		tl.lines = new ArrayList<>();
		if (bool(n, "wrap", false) && n.has("width")) {
			double width = length(n, st, "width", Env.AXIS_X, pw, ph, pw);
			for (String para : str.split("\n", -1)) {
				tl.lines.addAll(tr.wrapLines(Text.literal(para).setStyle(style), Math.max(1, (int) (width / tl.scale))));
			}
		} else {
			for (String para : str.split("\n", -1)) {
				tl.lines.add(Text.literal(para).setStyle(style).asOrderedText());
			}
		}
		if (tl.lines.isEmpty()) {
			tl.lines.add(t.asOrderedText());
		}
		tl.lineWidths = new double[tl.lines.size()];
		double max = 0;
		for (int i = 0; i < tl.lines.size(); i++) {
			tl.lineWidths[i] = tr.getWidth(tl.lines.get(i)) * tl.scale;
			max = Math.max(max, tl.lineWidths[i]);
		}
		tl.width = max;
		tl.height = tl.lineHeight * (tl.lines.size() - 1) + size;
		return tl;
	}

	private void drawText(Ctx c, Node n, NodeState st, double w, double opacity, TextLayout tl) {
		int color = Paint.withAlpha(color(n, st, "color", 0xFFFFFFFF), opacity);
		if (((color >>> 24) & 255) < 5) {
			return;
		}
		boolean shadow = bool(n, "shadow", false);
		String align = word(n, "textAlign", "left");
		MatrixStack m = c.dc.getMatrices();
		for (int i = 0; i < tl.lines.size(); i++) {
			double lx = switch (align) {
				case "center" -> (w - tl.lineWidths[i]) / 2;
				case "right" -> w - tl.lineWidths[i];
				default -> 0;
			};
			m.push();
			m.translate(lx, i * tl.lineHeight, 0);
			m.scale((float) tl.scale, (float) tl.scale, 1);
			c.dc.drawText(tl.renderer, tl.lines.get(i), 0, 0, color, shadow);
			m.pop();
		}
	}

	private void drawImage(Node n, NodeState st, Matrix4f m, double w, double h, double opacity) {
		Textures.Tex t = textures.get(word(n, "src", ""), n.file, n.line);
		if (t == null) {
			return;
		}
		int tint = Paint.withAlpha(color(n, st, "tint", 0xFFFFFFFF), opacity);
		t.texture().setFilter(!bool(n, "pixelated", false), false);
		int frames = Math.max(1, (int) number(n, "frames", 1));
		boolean horizontal = word(n, "frameLayout", "vertical").equals("horizontal");
		int frame = frames <= 1 ? 0 : (int) Math.floor(now * number(n, "fps", 10)) % frames;
		float fu1 = horizontal ? frame / (float) frames : 0;
		float fu2 = horizontal ? (frame + 1) / (float) frames : 1;
		float fv1 = horizontal ? 0 : frame / (float) frames;
		float fv2 = horizontal ? 1 : (frame + 1) / (float) frames;
		double texW = t.width() * (fu2 - fu1);
		double texH = t.height() * (fv2 - fv1);
		BufferBuilder b = Paint.beginTextured(t.id());
		if (n.slice != null) {
			nineSlice(b, m, n, w, h, t, fu1, fv1, fu2, fv2, tint);
		} else {
			switch (word(n, "fit", "stretch")) {
				case "contain" -> {
					double s = Math.min(w / texW, h / texH);
					double dw = texW * s;
					double dh = texH * s;
					float x1 = (float) ((w - dw) / 2);
					float y1 = (float) ((h - dh) / 2);
					Paint.texQuad(b, m, x1, y1, (float) (x1 + dw), (float) (y1 + dh), fu1, fv1, fu2, fv2, tint);
				}
				case "cover" -> {
					double s = Math.max(w / texW, h / texH);
					double cu = (w / s) / texW * (fu2 - fu1);
					double cv = (h / s) / texH * (fv2 - fv1);
					float u1 = (float) (fu1 + ((fu2 - fu1) - cu) / 2);
					float v1 = (float) (fv1 + ((fv2 - fv1) - cv) / 2);
					Paint.texQuad(b, m, 0, 0, (float) w, (float) h, u1, v1, (float) (u1 + cu), (float) (v1 + cv), tint);
				}
				case "tile" -> {
					double tw = texW * env.rx;
					double th = texH * env.ry;
					if (tw > 0.5 && th > 0.5) {
						for (double ty = 0; ty < h; ty += th) {
							for (double tx = 0; tx < w; tx += tw) {
								double cw = Math.min(tw, w - tx);
								double ch = Math.min(th, h - ty);
								Paint.texQuad(b, m, (float) tx, (float) ty, (float) (tx + cw), (float) (ty + ch),
										fu1, fv1, (float) (fu1 + (fu2 - fu1) * cw / tw), (float) (fv1 + (fv2 - fv1) * ch / th), tint);
							}
						}
					}
				}
				default -> Paint.texQuad(b, m, 0, 0, (float) w, (float) h, fu1, fv1, fu2, fv2, tint);
			}
		}
		Paint.end(b);
	}

	private void nineSlice(BufferBuilder b, Matrix4f m, Node n, double w, double h, Textures.Tex t,
			float fu1, float fv1, float fu2, float fv2, int tint) {
		int[] s = n.slice;
		double k = number(n, "sliceScale", 1);
		double l = Math.min(s[0] * k * env.rx, w / 2);
		double r = Math.min(s[2] * k * env.rx, w / 2);
		double top = Math.min(s[1] * k * env.ry, h / 2);
		double bot = Math.min(s[3] * k * env.ry, h / 2);
		float pu = 1f / t.width();
		float pv = 1f / t.height();
		float[] xs = {0, (float) l, (float) (w - r), (float) w};
		float[] ys = {0, (float) top, (float) (h - bot), (float) h};
		float[] us = {fu1, fu1 + s[0] * pu, fu2 - s[2] * pu, fu2};
		float[] vs = {fv1, fv1 + s[1] * pv, fv2 - s[3] * pv, fv2};
		for (int iy = 0; iy < 3; iy++) {
			for (int ix = 0; ix < 3; ix++) {
				Paint.texQuad(b, m, xs[ix], ys[iy], xs[ix + 1], ys[iy + 1], us[ix], vs[iy], us[ix + 1], vs[iy + 1], tint);
			}
		}
	}

	private void drawBar(Node n, NodeState st, Matrix4f m, double w, double h, double opacity) {
		double value = Math.max(0, Math.min(1, num(n, st, "value", 0)));
		if (n.has("background")) {
			int bg = Paint.withAlpha(color(n, st, "background", 0x80000000), opacity);
			Paint.rect(m, 0, 0, (float) w, (float) h, bg, bg, true);
		}
		String dir = word(n, "direction", "right");
		float x1 = 0;
		float y1 = 0;
		float x2 = (float) w;
		float y2 = (float) h;
		float u1 = 0;
		float v1 = 0;
		float u2 = 1;
		float v2 = 1;
		switch (dir) {
			case "left" -> {
				x1 = (float) (w * (1 - value));
				u1 = (float) (1 - value);
			}
			case "up" -> {
				y1 = (float) (h * (1 - value));
				v1 = (float) (1 - value);
			}
			case "down" -> {
				y2 = (float) (h * value);
				v2 = (float) value;
			}
			default -> {
				x2 = (float) (w * value);
				u2 = (float) value;
			}
		}
		if (n.has("image")) {
			Textures.Tex t = textures.get(word(n, "image", ""), n.file, n.line);
			if (t != null) {
				int tint = Paint.withAlpha(color(n, st, "color", 0xFFFFFFFF), opacity);
				BufferBuilder b = Paint.beginTextured(t.id());
				Paint.texQuad(b, m, x1, y1, x2, y2, u1, v1, u2, v2, tint);
				Paint.end(b);
			}
		} else {
			int fc = Paint.withAlpha(color(n, st, "color", 0xFFFFFFFF), opacity);
			Paint.rect(m, x1, y1, x2, y2, fc, fc, true);
		}
		outline(n, st, m, w, h, opacity);
	}

	private void drawItem(Ctx c, Node n, double w, double h) {
		String id = word(n, "item", "");
		Identifier ident = Identifier.tryParse(id);
		if (ident == null || !Registries.ITEM.containsId(ident)) {
			layout.problems.add(n.file, n.line, "unknown item '" + id + "'");
			return;
		}
		ItemStack stack = new ItemStack(Registries.ITEM.get(ident), Math.max(1, (int) number(n, "count", 1)));
		MatrixStack m = c.dc.getMatrices();
		m.push();
		m.scale((float) (w / 16), (float) (h / 16), 1);
		c.dc.drawItem(stack, 0, 0);
		if (stack.getCount() > 1) {
			c.dc.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, 0, 0);
		}
		m.pop();
	}

	// ---- input ----

	/** A click: runs the action of the CakeUI button under the mouse. True when it was ours. */
	public boolean mouseClicked(Screen screen, int button) {
		if (button != 0 || topButton == null) {
			return false;
		}
		NodeState st = topButton;
		st.pressed = true;
		pressed = st;
		Node n = st.node;
		env.self = st;
		Paint.sound(n.has("sounds.click") ? word(n, "sounds.click", "") : "minecraft:ui.button.click");
		if (n.action != null) {
			Actions.run(this, n, st, screen);
		}
		return true;
	}

	public void mouseReleased() {
		if (pressed != null) {
			pressed.pressed = false;
			pressed = null;
		}
	}

	public void close() {
		textures.close();
		fonts.close();
		layers.close();
		states.clear();
	}
}
