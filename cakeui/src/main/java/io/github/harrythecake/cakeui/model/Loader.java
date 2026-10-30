package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Expr;
import io.github.harrythecake.cakeui.expr.ExprException;
import io.github.harrythecake.cakeui.expr.Parser;
import io.github.harrythecake.cakeui.expr.Template;
import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;
import io.github.harrythecake.cakeui.json.Json5Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads a layout folder into a {@link Layout}. Every problem is recorded with file and line; a
 * broken element is skipped and the rest still loads. Only a missing or too-new window.json5
 * makes the whole layout unusable ({@link #fatal}).
 */
public final class Loader {
	public static final List<String> SELF_KEYS = List.of("hovered", "pressed", "active", "width", "height", "text", "id");
	public static final Map<String, List<String>> PORTS = Map.of(
			"main_menu", List.of("background", "logo", "splash", "version", "buttons"),
			"game_launch", List.of("background", "bar", "logo"),
			"loading_screen", List.of("background", "buttons"),
			"hud", List.of());
	private static final Set<String> PORT_KEYS = Set.of("hide", "x", "y", "scale", "opacity", "elements", "order");
	private static final Set<String> SPLASH_STYLE_KEYS = Set.of("anchor", "origin", "color", "size", "font", "transform", "shadow", "text", "parallax");
	private static final Set<String> BUTTON_KEYS = Set.of("anchor", "origin", "x", "y", "width", "height", "visible", "text",
			"skin", "states", "transition", "sounds", "opacity");
	private static final Set<String> WINDOW_KEYS = Set.of("format", "design", "scale", "fonts", "perspective", "icon", "title", "border", "$schema");

	private final Layout layout;
	private final Path root;
	public boolean fatal;

	private Loader(Layout layout) {
		this.layout = layout;
		this.root = layout.root;
	}

	public static Loader load(Path folder) {
		String name = folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
		Loader loader = new Loader(new Layout(name, folder));
		loader.run();
		return loader;
	}

	public Layout layout() {
		return layout;
	}

	private void problem(String file, int line, String message) {
		layout.problems.add(file, line, message);
	}

	private String rel(Path p) {
		return root.relativize(p).toString().replace('\\', '/');
	}

	private void run() {
		if (!Files.isDirectory(root)) {
			problem("", 0, "the layout folder doesn't exist: " + root);
			fatal = true;
			return;
		}
		Path windowFile = root.resolve("window.json5");
		JValue.JObject window = readObject(windowFile, true);
		if (window == null) {
			fatal = true;
			return;
		}
		if (!readWindow(window)) {
			fatal = true;
			return;
		}
		readDeclaration(root.resolve("declaration.json5"), layout.scope);
		readLang();
		try (Stream<Path> entries = Files.list(root)) {
			for (Path p : entries.sorted().toList()) {
				String n = p.getFileName().toString();
				if (Files.isDirectory(p)) {
					if (Layout.PARTS.contains(n)) {
						readPart(n, p);
					} else if (!n.equals("assets") && !n.equals("lang") && !n.startsWith(".")) {
						problem(n + "/", 0, "unknown folder (parts are " + String.join(", ", Layout.PARTS) + ")" + PropKinds.suggest(n, Layout.PARTS));
					}
				} else if (!n.endsWith(".json5") && !n.endsWith(".txt") && !n.endsWith(".md") && !n.startsWith(".")) {
					problem(n, 0, "unexpected file in the layout folder");
				} else if (n.endsWith(".json5") && !n.equals("window.json5") && !n.equals("declaration.json5")) {
					problem(n, 0, "unknown file (expected window.json5 and declaration.json5 here)");
				}
			}
		} catch (IOException e) {
			problem("", 0, "could not list the layout folder: " + e.getMessage());
		}
	}

	// ---- files ----

	private JValue.JObject readObject(Path file, boolean required) {
		String r = rel(file);
		if (!Files.isRegularFile(file)) {
			if (required) {
				problem(r, 0, "missing (every layout needs one)");
			}
			return null;
		}
		try {
			JValue v = Json5.parse(Files.readString(file, StandardCharsets.UTF_8), r);
			if (v instanceof JValue.JObject o) {
				return o;
			}
			problem(r, v.line, "expected an object { … } at the top, found " + v.typeName());
		} catch (Json5Exception e) {
			problem(r, e.line, e.problem);
		} catch (IOException e) {
			problem(r, 0, "could not read: " + e.getMessage());
		}
		return null;
	}

	private boolean readWindow(JValue.JObject w) {
		String file = "window.json5";
		JValue format = w.get("format");
		if (!(format instanceof JValue.JNumber n)) {
			problem(file, w.line, "'format' is missing (write format: " + Layout.FORMAT + ")");
			return false;
		}
		if (n.value > Layout.FORMAT) {
			problem(file, n.line, "this layout needs a newer CakeUI (format " + (int) n.value + ", this CakeUI reads " + Layout.FORMAT + ")");
			return false;
		}
		for (Map.Entry<String, JValue> e : w.map.entrySet()) {
			if (!WINDOW_KEYS.contains(e.getKey())) {
				problem(file, w.keyLine(e.getKey()), "unknown key '" + e.getKey() + "'" + PropKinds.suggest(e.getKey(), WINDOW_KEYS));
			}
		}
		JValue design = w.get("design");
		if (design instanceof JValue.JArray a && a.list.size() == 2 && a.list.get(0) instanceof JValue.JNumber dw && a.list.get(1) instanceof JValue.JNumber dh
				&& dw.value > 0 && dh.value > 0) {
			layout.designWidth = dw.value;
			layout.designHeight = dh.value;
		} else if (design != null) {
			problem(file, design.line, "'design' must be [width, height], e.g. [1920, 1080]");
		}
		JValue scale = w.get("scale");
		if (scale instanceof JValue.JString s) {
			try {
				layout.scaleMode = Layout.ScaleMode.valueOf(s.value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				problem(file, s.line, "'scale' must be fit, fill or stretch");
			}
		} else if (scale != null) {
			problem(file, scale.line, "'scale' must be fit, fill or stretch");
		}
		JValue perspective = w.get("perspective");
		if (perspective instanceof JValue.JNumber p && p.value > 0) {
			layout.perspective = p.value;
		} else if (perspective != null) {
			problem(file, perspective.line, "'perspective' must be a positive number (design pixels)");
		}
		JValue fonts = w.get("fonts");
		if (fonts instanceof JValue.JObject fo) {
			for (Map.Entry<String, JValue> e : fo.map.entrySet()) {
				readFont(e.getKey(), e.getValue());
			}
		} else if (fonts != null) {
			problem(file, fonts.line, "'fonts' must be an object: { title: { file: \"fonts/x.ttf\", size: 11 } }");
		}
		return true;
	}

	private void readFont(String name, JValue v) {
		String file = "window.json5";
		if (v instanceof JValue.JString s) {
			v = new JValue.JObject(new LinkedHashMap<>(Map.of("file", s)), Map.of(), s.file, s.line);
		}
		if (!(v instanceof JValue.JObject o) || !(o.get("file") instanceof JValue.JString f)) {
			problem(file, v.line, "font '" + name + "' needs a file: { file: \"fonts/x.ttf\" }");
			return;
		}
		Path path = root.resolve("assets").resolve(f.value).normalize();
		if (!path.startsWith(root.resolve("assets").normalize()) || !Files.isRegularFile(path)) {
			problem(file, f.line, "font file not found: assets/" + f.value);
			return;
		}
		double size = o.get("size") instanceof JValue.JNumber n ? n.value : 11;
		double oversample = o.get("oversample") instanceof JValue.JNumber n ? n.value : 0;
		double sx = 0;
		double sy = 0;
		if (o.get("shift") instanceof JValue.JArray a && a.list.size() == 2
				&& a.list.get(0) instanceof JValue.JNumber x && a.list.get(1) instanceof JValue.JNumber y) {
			sx = x.value;
			sy = y.value;
		}
		layout.fonts.put(name, new Layout.FontSpec(name, f.value, size, oversample, sx, sy));
	}

	private void readLang() {
		Path dir = root.resolve("lang");
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.sorted().toList()) {
				String n = p.getFileName().toString();
				if (!n.endsWith(".json5") && !n.endsWith(".json")) {
					continue;
				}
				String code = n.substring(0, n.lastIndexOf('.')).toLowerCase(Locale.ROOT);
				JValue.JObject o = readObject(p, false);
				if (o == null) {
					continue;
				}
				Map<String, String> strings = new LinkedHashMap<>();
				for (Map.Entry<String, JValue> e : o.map.entrySet()) {
					if (e.getValue() instanceof JValue.JString s) {
						strings.put(e.getKey(), s.value);
					} else {
						problem(rel(p), o.keyLine(e.getKey()), "'" + e.getKey() + "' must be text");
					}
				}
				layout.lang.put(code, strings);
			}
		} catch (IOException e) {
			problem("lang/", 0, "could not list: " + e.getMessage());
		}
	}

	// ---- declarations ----

	private static boolean isName(String s) {
		if (s.isEmpty() || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_')) {
			return false;
		}
		for (char c : s.toCharArray()) {
			if (!Character.isLetterOrDigit(c) && c != '_') {
				return false;
			}
		}
		return !s.equals("true") && !s.equals("false") && !s.equals("pi");
	}

	void readDeclaration(Path file, Scope scope) {
		JValue.JObject o = readObject(file, false);
		if (o == null) {
			return;
		}
		String r = rel(file);
		Map<Scope.Slot, JValue> pending = new LinkedHashMap<>();
		for (Map.Entry<String, JValue> section : o.map.entrySet()) {
			String key = section.getKey();
			if (key.equals("$schema")) {
				continue;
			}
			if (!key.equals("constants") && !key.equals("variables")) {
				problem(r, o.keyLine(key), "unknown section '" + key + "' (use constants and variables)" + PropKinds.suggest(key, List.of("constants", "variables")));
				continue;
			}
			if (!(section.getValue() instanceof JValue.JObject entries)) {
				problem(r, section.getValue().line, "'" + key + "' must be an object { name: value }");
				continue;
			}
			boolean constants = key.equals("constants");
			for (Map.Entry<String, JValue> e : entries.map.entrySet()) {
				String name = e.getKey();
				int line = entries.keyLine(name);
				JValue v = e.getValue();
				if (!isName(name)) {
					problem(r, line, "'" + name + "' is not a valid name (letters, digits and _; no dots or spaces)");
					continue;
				}
				if (scope.own(name) != null) {
					problem(r, line, "'" + name + "' is declared twice");
					continue;
				}
				if (!(v instanceof JValue.JNumber || v instanceof JValue.JBool || v instanceof JValue.JString)) {
					problem(r, line, "'" + name + "' must be a number, true/false or text, not " + v.typeName());
					continue;
				}
				Object literal = literal(v);
				boolean isExpr = v instanceof JValue.JString s && (s.value.startsWith("=") || s.value.contains("{"));
				Scope.Slot slot;
				if (constants) {
					Scope.Constant c = new Scope.Constant(name, r, line);
					if (!isExpr) {
						c.value = literal;
					}
					slot = c;
				} else if (isExpr) {
					slot = new Scope.Computed(name, r, line);
				} else {
					slot = new Scope.State(name, r, line, literal);
				}
				scope.slots.put(name, slot);
				if (isExpr) {
					pending.put(slot, v);
				}
			}
		}
		for (Map.Entry<Scope.Slot, JValue> e : pending.entrySet()) {
			Scope.Slot slot = e.getKey();
			String text = ((JValue.JString) e.getValue()).value;
			boolean constant = slot instanceof Scope.Constant;
			try {
				Expr expr = Template.compile(text, resolver(scope, false, constant));
				if (slot instanceof Scope.Constant c) {
					c.expr = expr;
				} else {
					((Scope.Computed) slot).expr = expr;
				}
			} catch (ExprException ex) {
				problem(r, slot.line, "'" + slot.name + "': " + withSuggestion(ex, scope, false) + column(ex));
				scope.slots.remove(slot.name);
			}
		}
		Env env = new Env();
		for (Scope.Slot slot : new ArrayList<>(scope.slots.values())) {
			if (slot instanceof Scope.Constant c && c.expr != null && c.expr.isConstant()) {
				try {
					c.get(env);
				} catch (IllegalStateException ex) {
					problem(r, slot.line, ex.getMessage());
				}
			}
		}
	}

	private static Object literal(JValue v) {
		if (v instanceof JValue.JNumber n) {
			return n.value;
		}
		if (v instanceof JValue.JBool b) {
			return b.value;
		}
		if (v instanceof JValue.JString s) {
			return s.value;
		}
		return null;
	}

	/** Name lookup: self.* (elements only), then declarations up the folders, then game values. */
	public static Parser.Resolver resolver(Scope scope, boolean self, boolean constantsOnly) {
		return name -> {
			if (name.startsWith("self.")) {
				String key = name.substring(5);
				if (!self || !SELF_KEYS.contains(key)) {
					return null;
				}
				return env -> {
					if (env.self == null) {
						return 0.0;
					}
					Object v = env.self.get(key);
					return v == null ? 0.0 : v;
				};
			}
			Scope.Slot slot = scope.find(name);
			if (slot != null) {
				if (constantsOnly && !(slot instanceof Scope.Constant)) {
					throw new ExprException("constants can only use other constants, and '" + name + "' is a variable", 1);
				}
				return slot.reader();
			}
			if (GameValues.has(name)) {
				if (constantsOnly) {
					throw new ExprException("constants can't use game values like '" + name + "'; declare it under variables", 1);
				}
				return GameValues.resolve(name);
			}
			return null;
		};
	}

	private static String withSuggestion(ExprException ex, Scope scope, boolean self) {
		String m = ex.getMessage();
		if (m.startsWith("unknown name '")) {
			String wrong = m.substring(14, m.length() - 1);
			Set<String> known = new LinkedHashSet<>(GameValues.names());
			for (Scope s = scope; s != null; s = s.parent) {
				known.addAll(s.slots.keySet());
			}
			if (self) {
				for (String k : SELF_KEYS) {
					known.add("self." + k);
				}
			}
			return m + PropKinds.suggest(wrong, known);
		}
		return m;
	}

	private static String column(ExprException ex) {
		return ex.column > 1 ? " (at character " + ex.column + ")" : "";
	}

	// ---- parts, subwindows, elements ----

	private void readPart(String id, Path dir) {
		Scope scope = new Scope(layout.scope, id);
		readDeclaration(dir.resolve("declaration.json5"), scope);
		Path file = dir.resolve("layout.json5");
		JValue.JObject o = readObject(file, false);
		String r = rel(file);
		Node part = new Node(id, "part", scope, r, o == null ? 0 : o.line);
		if (o != null) {
			fill(part, o, r);
			JValue ports = o.get("ports");
			if (ports instanceof JValue.JObject po) {
				readPorts(part, id, po, r);
			} else if (ports != null) {
				problem(r, ports.line, "'ports' must be an object");
			}
		}
		readSubwindows(part, dir, scope);
		layout.parts.put(id, part);
	}

	private void readSubwindows(Node parent, Path dir, Scope parentScope) {
		try (Stream<Path> entries = Files.list(dir)) {
			for (Path p : entries.sorted().toList()) {
				String n = p.getFileName().toString();
				if (Files.isDirectory(p)) {
					if (n.startsWith("subwindow_") && n.length() > 10) {
						readSubwindow(parent, p, parentScope, n.substring(10));
					} else {
						problem(rel(p) + "/", 0, "unknown folder (subwindows are named subwindow_<name>)");
					}
				} else if (!n.equals("layout.json5") && !n.equals("declaration.json5") && !n.equals("splashes.txt") && !n.startsWith(".")) {
					problem(rel(p), 0, "unexpected file (expected layout.json5, declaration.json5" + (parent.type.equals("part") ? ", splashes.txt" : "") + ")");
				}
			}
		} catch (IOException e) {
			problem(rel(dir), 0, "could not list: " + e.getMessage());
		}
	}

	private void readSubwindow(Node parent, Path dir, Scope parentScope, String name) {
		Scope scope = new Scope(parentScope, rel(dir));
		readDeclaration(dir.resolve("declaration.json5"), scope);
		Path file = dir.resolve("layout.json5");
		String r = rel(file);
		JValue.JObject o = readObject(file, false);
		Node node = new Node(name, "subwindow", scope, r, o == null ? 0 : o.line);
		if (o != null) {
			fill(node, o, r);
		}
		readSubwindows(node, dir, scope);
		parent.children.add(node);
	}

	/** Fills a node's props and children from its object; problems name the file and line. */
	private void fill(Node node, JValue.JObject o, String file) {
		Set<String> allowed = PropKinds.keysFor(node.type);
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			String key = e.getKey();
			JValue v = e.getValue();
			int line = o.keyLine(key);
			if (key.equals("$schema") || key.equals("type") || (node.type.equals("part") && key.equals("ports"))) {
				continue;
			}
			if (!allowed.contains(key)) {
				problem(file, line, "'" + key + "' isn't a property of " + node.type + PropKinds.suggest(key, allowed));
				continue;
			}
			switch (key) {
				case "elements" -> readElements(node, v, file);
				case "states" -> readStates(node, v, file);
				case "action" -> node.action = readAction(node, v, file);
				case "slice" -> node.slice = readSlice(v, file);
				case "background" -> {
					if (v instanceof JValue.JObject bo) {
						node.background = element("background", bo, node.scope, file, line);
						if (node.background != null) {
							node.background.fill = true;
						}
					} else {
						setProp(node, key, v, file, line);
					}
				}
				default -> {
					if (PropKinds.GROUPS.contains(key)) {
						readGroup(node, key, v, file, null);
					} else {
						setProp(node, key, v, file, line);
					}
				}
			}
		}
		if (node.type.equals("image") && !node.has("src")) {
			problem(file, o.line, "image '" + node.name + "' has no src");
		}
		checkWords(node, file);
	}

	private static final Map<String, List<String>> WORDS = Map.of(
			"anchor", PropKinds.ANCHORS,
			"origin", PropKinds.ANCHORS,
			"arrange", List.of("none", "row", "column"),
			"align", List.of("start", "center", "end"),
			"textAlign", List.of("left", "center", "right"),
			"fit", List.of("stretch", "contain", "cover", "tile"),
			"frameLayout", List.of("vertical", "horizontal"),
			"direction", List.of("right", "left", "up", "down"),
			"gradient", List.of("vertical", "horizontal"),
			"layer", List.of("back", "front"));

	/** Fixed-word properties (anchor, fit, …) are checked at load when they are constant. */
	void checkWords(Node node, String file) {
		for (Prop p : node.props.values()) {
			List<String> words = p.key.equals("transition.easing") ? PropKinds.EASINGS : WORDS.get(p.key);
			if (words == null) {
				continue;
			}
			for (Expr e : new Expr[] {p.base, p.hover, p.pressed, p.disabled}) {
				if (e != null && e.isConstant()) {
					String w = io.github.harrythecake.cakeui.expr.Values.str(e.eval(null));
					if (!words.contains(w)) {
						problem(file, p.line, "'" + p.key + "' can't be '" + w + "' (" + String.join(", ", words) + ")" + PropKinds.suggest(w, words));
					}
				}
			}
		}
	}

	private void readElements(Node parent, JValue v, String file) {
		if (!(v instanceof JValue.JObject o)) {
			problem(file, v.line, "'elements' must be an object { name: { type: … } }");
			return;
		}
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			if (!(e.getValue() instanceof JValue.JObject eo)) {
				problem(file, o.keyLine(e.getKey()), "element '" + e.getKey() + "' must be an object { type: … }");
				continue;
			}
			Node child = element(e.getKey(), eo, parent.scope, file, o.keyLine(e.getKey()));
			if (child != null) {
				parent.children.add(child);
			}
		}
	}

	private Node element(String name, JValue.JObject o, Scope scope, String file, int line) {
		String type;
		if (o.get("type") instanceof JValue.JString t) {
			type = t.value;
		} else if (o.get("type") == null && name.equals("background")) {
			type = o.get("src") != null ? "image" : "rect";
		} else {
			problem(file, line, "element '" + name + "' needs a type (" + String.join(", ", PropKinds.ELEMENT_TYPES) + ")");
			return null;
		}
		if (!PropKinds.ELEMENT_TYPES.contains(type)) {
			problem(file, line, "element '" + name + "' has unknown type '" + type + "'" + PropKinds.suggest(type, PropKinds.ELEMENT_TYPES));
			return null;
		}
		Node node = new Node(name, type, scope, file, line);
		fill(node, o, file);
		return node;
	}

	private void readGroup(Node node, String group, JValue v, String file, String state) {
		if (!(v instanceof JValue.JObject o)) {
			problem(file, v.line, "'" + group + "' must be an object");
			return;
		}
		Set<String> keys = PropKinds.groupKeys(group);
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			int line = o.keyLine(e.getKey());
			if (!keys.contains(e.getKey())) {
				problem(file, line, "'" + e.getKey() + "' isn't part of " + group + PropKinds.suggest(e.getKey(), keys));
				continue;
			}
			String flat = group + "." + e.getKey();
			if (state == null) {
				setProp(node, flat, e.getValue(), file, line);
			} else {
				setStateProp(node, state, flat, e.getValue(), file, line);
			}
		}
	}

	private void readStates(Node node, JValue v, String file) {
		if (!(v instanceof JValue.JObject o)) {
			problem(file, v.line, "'states' must be an object { hover: { … }, pressed: { … }, disabled: { … } }");
			return;
		}
		Set<String> allowed = PropKinds.keysFor(node.type);
		for (Map.Entry<String, JValue> s : o.map.entrySet()) {
			String state = s.getKey();
			if (!state.equals("hover") && !state.equals("pressed") && !state.equals("disabled")) {
				problem(file, o.keyLine(state), "unknown state '" + state + "' (hover, pressed, disabled)");
				continue;
			}
			if (!(s.getValue() instanceof JValue.JObject so)) {
				problem(file, s.getValue().line, "state '" + state + "' must be an object");
				continue;
			}
			for (Map.Entry<String, JValue> e : so.map.entrySet()) {
				String key = e.getKey();
				int line = so.keyLine(key);
				if (PropKinds.GROUPS.contains(key)) {
					readGroup(node, key, e.getValue(), file, state);
				} else if (allowed.contains(key) && PropKinds.kind(key) != null) {
					setStateProp(node, state, key, e.getValue(), file, line);
				} else {
					problem(file, line, "'" + key + "' can't change in a state" + PropKinds.suggest(key, allowed));
				}
			}
		}
		node.interactive = true;
	}

	private void setProp(Node node, String key, JValue v, String file, int line) {
		PropKinds.Kind kind = PropKinds.kind(key);
		if (kind == null) {
			problem(file, line, "'" + key + "' can't be set here");
			return;
		}
		Expr e = compileValue(node, key, kind, v, file, line);
		if (e != null) {
			Prop existing = node.props.get(key);
			if (existing != null) {
				existing.base = e;
			} else {
				node.props.put(key, new Prop(key, kind, e, line));
			}
		}
	}

	private void setStateProp(Node node, String state, String key, JValue v, String file, int line) {
		PropKinds.Kind kind = PropKinds.kind(key);
		Expr e = compileValue(node, key, kind, v, file, line);
		if (e == null) {
			return;
		}
		Prop p = node.props.computeIfAbsent(key, k -> new Prop(k, kind, null, line));
		switch (state) {
			case "hover" -> p.hover = e;
			case "pressed" -> p.pressed = e;
			default -> p.disabled = e;
		}
		node.interactive = true;
	}

	private Expr compileValue(Node node, String key, PropKinds.Kind kind, JValue v, String file, int line) {
		try {
			if (v instanceof JValue.JNumber n) {
				return switch (kind) {
					case BOOL -> Expr.constant(n.value != 0);
					case TEXT, WORD -> Expr.constant(io.github.harrythecake.cakeui.expr.Values.format(n.value));
					default -> Expr.constant(n.value);
				};
			}
			if (v instanceof JValue.JBool b) {
				return Expr.constant(b.value);
			}
			if (v instanceof JValue.JString s) {
				String text = s.value;
				switch (kind) {
					case TEXT, WORD:
						return Template.compile(text, resolver(node.scope, true, false));
					case COLOR:
						if (text.startsWith("#")) {
							if (io.github.harrythecake.cakeui.expr.Values.parseColor(text) == null) {
								problem(file, line, "'" + key + "': bad colour '" + text + "' (use #RGB, #RRGGBB or #RRGGBBAA)");
								return null;
							}
							return Expr.constant(text);
						}
						// fall through: an expression giving a colour
					default:
						return Parser.compile(text.startsWith("=") ? text.substring(1) : text, resolver(node.scope, true, false));
				}
			}
			problem(file, line, "'" + key + "' must be " + expected(kind) + ", not " + v.typeName());
		} catch (ExprException ex) {
			problem(file, line, "'" + key + "': " + withSuggestion(ex, node.scope, true) + column(ex));
		}
		return null;
	}

	private static String expected(PropKinds.Kind kind) {
		return switch (kind) {
			case LENGTH -> "a length (12, \"12r\", \"50%\", \"20px\" or an expression)";
			case NUMBER -> "a number or an expression";
			case COLOR -> "a colour (\"#RRGGBB\") or an expression";
			case BOOL -> "true/false or an expression";
			case TEXT, WORD -> "text";
		};
	}

	private int[] readSlice(JValue v, String file) {
		if (v instanceof JValue.JNumber n) {
			int s = (int) n.value;
			return new int[] {s, s, s, s};
		}
		if (v instanceof JValue.JArray a && a.list.size() == 4 && a.list.stream().allMatch(x -> x instanceof JValue.JNumber)) {
			int[] s = new int[4];
			for (int i = 0; i < 4; i++) {
				s[i] = (int) ((JValue.JNumber) a.list.get(i)).value;
			}
			return s;
		}
		problem(file, v.line, "'slice' must be a number or [left, top, right, bottom] in texture pixels");
		return null;
	}

	private Node.Action readAction(Node node, JValue v, String file) {
		if (v instanceof JValue.JString s) {
			if (Node.Action.SCREENS.contains(s.value)) {
				return new Node.Action(s.value, null, null);
			}
			problem(file, s.line, "unknown action '" + s.value + "' (" + String.join(", ", Node.Action.SCREENS)
					+ ", or { connect: … }, { url: … }, { set: { … } }, { toggle: … })" + PropKinds.suggest(s.value, Node.Action.SCREENS));
			return null;
		}
		if (!(v instanceof JValue.JObject o) || o.map.size() != 1) {
			problem(file, v.line, "'action' must be a word like \"multiplayer\" or one of { connect: … }, { url: … }, { set: { … } }, { toggle: … }");
			return null;
		}
		Map.Entry<String, JValue> e = o.map.entrySet().iterator().next();
		switch (e.getKey()) {
			case "connect", "url" -> {
				if (e.getValue() instanceof JValue.JString s && !s.value.isBlank()) {
					if (e.getKey().equals("url") && !(s.value.startsWith("https://") || s.value.startsWith("http://"))) {
						problem(file, s.line, "'url' must start with https:// or http://");
						return null;
					}
					return new Node.Action(e.getKey(), s.value, null);
				}
				problem(file, e.getValue().line, "'" + e.getKey() + "' needs text");
				return null;
			}
			case "toggle" -> {
				if (e.getValue() instanceof JValue.JString s) {
					Scope.State target = ownState(node, s.value, file, s.line);
					return target == null ? null : new Node.Action("set", null, List.of(new Node.Assign(target, null, true)));
				}
				problem(file, e.getValue().line, "'toggle' needs the name of a variable");
				return null;
			}
			case "set" -> {
				if (!(e.getValue() instanceof JValue.JObject so)) {
					problem(file, e.getValue().line, "'set' must be { name: value }");
					return null;
				}
				List<Node.Assign> assigns = new ArrayList<>();
				for (Map.Entry<String, JValue> a : so.map.entrySet()) {
					int line = so.keyLine(a.getKey());
					Scope.State target = ownState(node, a.getKey(), file, line);
					if (target == null) {
						continue;
					}
					Expr value;
					try {
						JValue av = a.getValue();
						if (av instanceof JValue.JString str) {
							value = Template.compile(str.value.startsWith("=") ? str.value : "=" + str.value, resolver(node.scope, true, false));
						} else {
							value = Expr.constant(literal(av));
						}
					} catch (ExprException ex) {
						problem(file, line, "'set " + a.getKey() + "': " + withSuggestion(ex, node.scope, true) + column(ex));
						continue;
					}
					assigns.add(new Node.Assign(target, value, false));
				}
				return new Node.Action("set", null, assigns);
			}
			default -> {
				problem(file, o.keyLine(e.getKey()), "unknown action '" + e.getKey() + "'" + PropKinds.suggest(e.getKey(), List.of("connect", "url", "set", "toggle")));
				return null;
			}
		}
	}

	/** A button may only set the state variables its own folder declares. */
	private Scope.State ownState(Node node, String name, String file, int line) {
		Scope.Slot slot = node.scope.own(name);
		if (slot instanceof Scope.State s) {
			return s;
		}
		if (slot != null) {
			problem(file, line, "'" + name + "' is computed or constant and can't be set; declare it under variables with a plain value");
		} else if (node.scope.find(name) != null) {
			problem(file, line, "'" + name + "' belongs to a parent folder; a folder can only set its own variables");
		} else {
			problem(file, line, "no variable '" + name + "' in this folder's declaration.json5");
		}
		return null;
	}

	// ---- ports ----

	private void readPorts(Node part, String partId, JValue.JObject o, String file) {
		List<String> known = PORTS.getOrDefault(partId, List.of());
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			String name = e.getKey();
			int line = o.keyLine(name);
			if (!known.contains(name)) {
				problem(file, line, "unknown port '" + name + "' in " + partId + (known.isEmpty() ? " (none yet)" : " (" + String.join(", ", known) + ")")
						+ PropKinds.suggest(name, known));
				continue;
			}
			if (!(e.getValue() instanceof JValue.JObject po)) {
				problem(file, line, "port '" + name + "' must be an object");
				continue;
			}
			if (name.equals("buttons")) {
				part.ports.put(name, readButtons(part, po, file, line));
			} else {
				part.ports.put(name, readPort(part, name, po, file, line));
			}
		}
	}

	private Node readPort(Node part, String name, JValue.JObject o, String file, int line) {
		Node port = new Node(name, "port", part.scope, file, line);
		boolean splash = name.equals("splash");
		boolean version = name.equals("version");
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			String key = e.getKey();
			JValue v = e.getValue();
			int kl = o.keyLine(key);
			if (splash && key.equals("list")) {
				port.splashes = readSplashList(part, v, file);
				continue;
			}
			if (splash && key.equals("mixVanilla")) {
				setPortProp(port, key, PropKinds.Kind.BOOL, v, file, kl);
				continue;
			}
			if ((splash && SPLASH_STYLE_KEYS.contains(key)) || (version && (key.equals("text") || key.equals("color")))) {
				if (splash) {
					port.customStyle = true;
				}
				if (PropKinds.GROUPS.contains(key)) {
					readGroup(port, key, v, file, null);
				} else {
					setProp(port, key, v, file, kl);
				}
				continue;
			}
			if (!PORT_KEYS.contains(key)) {
				Set<String> all = new LinkedHashSet<>(PORT_KEYS);
				if (splash) {
					all.addAll(SPLASH_STYLE_KEYS);
					all.add("list");
					all.add("mixVanilla");
				}
				problem(file, kl, "'" + key + "' isn't a setting of the " + name + " port" + PropKinds.suggest(key, all));
				continue;
			}
			switch (key) {
				case "hide" -> setPortProp(port, key, PropKinds.Kind.BOOL, v, file, kl);
				case "scale" -> setPortProp(port, key, PropKinds.Kind.NUMBER, v, file, kl);
				case "elements" -> readElements(port, v, file);
				default -> setProp(port, key, v, file, kl);
			}
		}
		return port;
	}

	private void setPortProp(Node port, String key, PropKinds.Kind kind, JValue v, String file, int line) {
		Expr e = compileValue(port, key, kind, v, file, line);
		if (e != null) {
			port.props.put(key, new Prop(key, kind, e, line));
		}
	}

	private List<String> readSplashList(Node part, JValue v, String file) {
		List<String> list = new ArrayList<>();
		if (v instanceof JValue.JArray a) {
			for (JValue item : a.list) {
				if (item instanceof JValue.JString s) {
					list.add(s.value);
				} else {
					problem(file, item.line, "splash list entries must be text");
				}
			}
			return list;
		}
		if (v instanceof JValue.JString s) {
			Path p = root.resolve(part.name).resolve(s.value).normalize();
			if (!p.startsWith(root.normalize()) || !Files.isRegularFile(p)) {
				problem(file, s.line, "splash file not found: " + part.name + "/" + s.value);
				return list;
			}
			try {
				for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
					String t = l.strip();
					if (!t.isEmpty() && !t.startsWith("#")) {
						list.add(t);
					}
				}
			} catch (IOException ex) {
				problem(file, s.line, "could not read " + s.value + ": " + ex.getMessage());
			}
			return list;
		}
		problem(file, v.line, "'list' must be [\"…\", \"…\"] or a file name like \"splashes.txt\"");
		return list;
	}

	private Node readButtons(Node part, JValue.JObject o, String file, int line) {
		Node port = new Node("buttons", "port", part.scope, file, line);
		for (Map.Entry<String, JValue> e : o.map.entrySet()) {
			String id = e.getKey();
			int kl = o.keyLine(id);
			if (!(e.getValue() instanceof JValue.JObject bo)) {
				problem(file, kl, "button '" + id + "' must be an object");
				continue;
			}
			Node b = new Node(id, "button_override", part.scope, file, kl);
			for (Map.Entry<String, JValue> be : bo.map.entrySet()) {
				String key = be.getKey();
				int bl = bo.keyLine(key);
				if (!BUTTON_KEYS.contains(key)) {
					problem(file, bl, "'" + key + "' isn't a button setting" + PropKinds.suggest(key, BUTTON_KEYS));
					continue;
				}
				switch (key) {
					case "skin" -> {
						b.customStyle = true;
						readElements(b, be.getValue(), file);
					}
					case "states" -> readStates(b, be.getValue(), file);
					case "transition", "sounds" -> readGroup(b, key, be.getValue(), file, null);
					default -> setProp(b, key, be.getValue(), file, bl);
				}
			}
			port.buttons.put(id, b);
		}
		return port;
	}
}
