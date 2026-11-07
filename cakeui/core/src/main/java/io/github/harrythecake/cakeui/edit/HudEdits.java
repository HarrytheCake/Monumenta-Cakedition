package io.github.harrythecake.cakeui.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.github.harrythecake.cakeui.CakeUI;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD edits from the HUD editor. A player's personal edits ({@code config/cakeui/hud.json5}) hold one
 * set per layout, keyed by the layout's name, plus {@link #VANILLA} for when no layout is active; a
 * layout's own edits ({@code <layout>/hud/edits.json5}, written in dev mode) are one set. A set maps
 * port names, and {@link #ELEMENT}{@code name} for the layout's own HUD elements, to edits; in the
 * files they are under "ports" and "elements". Plain JSON (valid JSON5); comments are allowed when read.
 */
public final class HudEdits {
	/** The set used when no layout is active. */
	public static final String VANILLA = "vanilla";
	/** Key prefix of a layout's own HUD element in a set ("@info" = the element "info"). */
	public static final String ELEMENT = "@";
	static final int FORMAT = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** layout key → port → edit. */
	private final Map<String, Map<String, PortEdit>> sets = new LinkedHashMap<>();

	/** The edits of one layout (never null; empty when it has none). */
	public Map<String, PortEdit> set(String layout) {
		Map<String, PortEdit> s = sets.get(layout);
		return s == null ? Map.of() : s;
	}

	/** Replaces one layout's edits (empty edits are dropped). */
	public void put(String layout, Map<String, PortEdit> edits) {
		Map<String, PortEdit> clean = new LinkedHashMap<>();
		for (Map.Entry<String, PortEdit> e : edits.entrySet()) {
			if (e.getValue() != null && !e.getValue().isEmpty()) {
				clean.put(e.getKey(), e.getValue().copy());
			}
		}
		if (clean.isEmpty()) {
			sets.remove(layout);
		} else {
			sets.put(layout, clean);
		}
	}

	/** A deep copy of one layout's edits, for editing. */
	public static Map<String, PortEdit> copy(Map<String, PortEdit> edits) {
		Map<String, PortEdit> out = new LinkedHashMap<>();
		for (Map.Entry<String, PortEdit> e : edits.entrySet()) {
			out.put(e.getKey(), e.getValue().copy());
		}
		return out;
	}

	// ---- file ----

	public static HudEdits load(Path file) {
		HudEdits edits = new HudEdits();
		if (!Files.exists(file)) {
			return edits;
		}
		try {
			edits.read(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
			CakeUI.LOGGER.warn("CakeUI: unreadable {}, HUD edits are off until it is fixed or saved again: {}", file, e.toString());
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// the next save writes a good file anyway
			}
			return new HudEdits();
		}
		return edits;
	}

	/** Parses the file's text (also used by tests). */
	void read(String text) {
		JsonReader reader = new JsonReader(new StringReader(text));
		reader.setLenient(true);
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonObject()) {
			return;
		}
		JsonObject o = root.getAsJsonObject();
		if (o.has("format") && o.get("format").getAsInt() > FORMAT) {
			throw new IllegalStateException("format " + o.get("format").getAsInt() + " is newer than this CakeUI understands");
		}
		if (!o.has("layouts") || !o.get("layouts").isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> l : o.getAsJsonObject("layouts").entrySet()) {
			if (l.getValue().isJsonObject()) {
				put(l.getKey(), readSet(l.getValue().getAsJsonObject()));
			}
		}
	}

	/** One set: "ports" and "elements" (elements get the {@link #ELEMENT} prefix). */
	private static Map<String, PortEdit> readSet(JsonObject set) {
		Map<String, PortEdit> out = new LinkedHashMap<>();
		for (String group : new String[] {"ports", "elements"}) {
			if (!set.has(group) || !set.get(group).isJsonObject()) {
				continue;
			}
			for (Map.Entry<String, JsonElement> p : set.getAsJsonObject(group).entrySet()) {
				if (p.getValue().isJsonObject()) {
					out.put((group.equals("elements") ? ELEMENT : "") + p.getKey(), readPort(p.getValue().getAsJsonObject()));
				}
			}
		}
		return out;
	}

	private static JsonObject writeSet(Map<String, PortEdit> edits) {
		JsonObject ports = new JsonObject();
		JsonObject elements = new JsonObject();
		for (Map.Entry<String, PortEdit> p : edits.entrySet()) {
			if (p.getValue().isEmpty()) {
				continue;
			}
			if (p.getKey().startsWith(ELEMENT)) {
				elements.add(p.getKey().substring(ELEMENT.length()), writePort(p.getValue()));
			} else {
				ports.add(p.getKey(), writePort(p.getValue()));
			}
		}
		JsonObject set = new JsonObject();
		set.add("ports", ports);
		if (elements.size() > 0) {
			set.add("elements", elements);
		}
		return set;
	}

	// ---- a layout's own edits (<layout>/hud/edits.json5) ----

	/** A layout's edits; empty when the file is missing or unreadable (logged). */
	public static Map<String, PortEdit> loadLayoutEdits(Path file) {
		if (!Files.exists(file)) {
			return new LinkedHashMap<>();
		}
		try {
			return parseLayoutEdits(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
			CakeUI.LOGGER.warn("CakeUI: unreadable {}, the layout's HUD edits are off: {}", file, e.toString());
			return new LinkedHashMap<>();
		}
	}

	static Map<String, PortEdit> parseLayoutEdits(String text) {
		JsonReader reader = new JsonReader(new StringReader(text));
		reader.setLenient(true);
		JsonElement root = JsonParser.parseReader(reader);
		if (!root.isJsonObject()) {
			return new LinkedHashMap<>();
		}
		JsonObject o = root.getAsJsonObject();
		if (o.has("format") && o.get("format").getAsInt() > FORMAT) {
			throw new IllegalStateException("format " + o.get("format").getAsInt() + " is newer than this CakeUI understands");
		}
		return readSet(o);
	}

	/** Writes a layout's edits; with none left, the file is deleted. */
	public static void saveLayoutEdits(Path file, Map<String, PortEdit> edits) throws IOException {
		boolean any = edits.values().stream().anyMatch(e -> !e.isEmpty());
		if (!any) {
			Files.deleteIfExists(file);
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonObject set = writeSet(edits);
		for (Map.Entry<String, JsonElement> e : set.entrySet()) {
			root.add(e.getKey(), e.getValue());
		}
		String text = "// This layout's HUD edits, written by CakeUI's HUD editor in dev mode (loaded after layout.json5;\n"
				+ "// a player's own edits in config/cakeui/hud.json5 go on top).\n" + GSON.toJson(root) + "\n";
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * The edits the HUD uses: the layout's edits, then the player's on top. A player's edit replaces each
	 * property it sets (the position as one: mode and its numbers); what it doesn't set follows the layout.
	 */
	public static Map<String, PortEdit> merge(Map<String, PortEdit> layout, Map<String, PortEdit> personal) {
		if (layout.isEmpty()) {
			return personal;
		}
		if (personal.isEmpty()) {
			return layout;
		}
		Map<String, PortEdit> out = copy(layout);
		for (Map.Entry<String, PortEdit> e : personal.entrySet()) {
			PortEdit p = e.getValue();
			PortEdit m = out.computeIfAbsent(e.getKey(), k -> new PortEdit());
			if (p.mode != null) {
				m.mode = p.mode;
				m.ax = p.ax;
				m.ay = p.ay;
				m.x = p.x;
				m.y = p.y;
			}
			if (p.scale != null) {
				m.scale = p.scale;
			}
			if (p.opacity != null) {
				m.opacity = p.opacity;
			}
			if (p.hide != null) {
				m.hide = p.hide;
			}
		}
		return out;
	}

	private static PortEdit readPort(JsonObject o) {
		PortEdit e = new PortEdit();
		if (o.has("mode")) {
			e.mode = PortEdit.Mode.of(o.get("mode").getAsString());
		}
		if (e.mode != null) {
			e.ax = num(o, "ax");
			e.ay = num(o, "ay");
			e.x = num(o, "x");
			e.y = num(o, "y");
		}
		if (o.has("scale")) {
			e.scale = Math.max(0.1, Math.min(8, o.get("scale").getAsDouble()));
		}
		if (o.has("opacity")) {
			e.opacity = (float) Math.max(0, Math.min(1, o.get("opacity").getAsDouble()));
		}
		if (o.has("hide")) {
			e.hide = o.get("hide").getAsBoolean();
		}
		return e;
	}

	private static double num(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsDouble() : 0;
	}

	/** The file's text. */
	String write() {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonObject layouts = new JsonObject();
		for (Map.Entry<String, Map<String, PortEdit>> l : sets.entrySet()) {
			layouts.add(l.getKey(), writeSet(l.getValue()));
		}
		root.add("layouts", layouts);
		return "// CakeUI HUD edits, written by the HUD editor (one set per layout; \"vanilla\" = no layout).\n" + GSON.toJson(root) + "\n";
	}

	private static JsonObject writePort(PortEdit e) {
		JsonObject o = new JsonObject();
		if (e.mode != null) {
			o.addProperty("mode", e.mode.id());
			if (e.mode != PortEdit.Mode.FIXED) {
				o.addProperty("ax", e.ax);
				o.addProperty("ay", e.ay);
			}
			if (e.mode != PortEdit.Mode.PERCENT) {
				o.addProperty("x", e.x);
				o.addProperty("y", e.y);
			}
		}
		if (e.scale != null) {
			o.addProperty("scale", e.scale);
		}
		if (e.opacity != null) {
			o.addProperty("opacity", e.opacity);
		}
		if (e.hide != null) {
			o.addProperty("hide", e.hide);
		}
		return o;
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, write(), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** For tests. */
	static HudEdits parse(String text) {
		HudEdits e = new HudEdits();
		e.read(text);
		return e;
	}
}
