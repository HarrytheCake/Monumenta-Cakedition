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
 * A player's personal HUD edits ({@code config/cakeui/hud.json5}): one set of port edits per layout,
 * keyed by the layout's name, plus {@link #VANILLA} for when no layout is active. Written by the HUD
 * editor; plain JSON (valid JSON5), comments are allowed when read.
 */
public final class HudEdits {
	/** The set used when no layout is active. */
	public static final String VANILLA = "vanilla";
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
			if (!l.getValue().isJsonObject() || !l.getValue().getAsJsonObject().has("ports")) {
				continue;
			}
			Map<String, PortEdit> ports = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> p : l.getValue().getAsJsonObject().getAsJsonObject("ports").entrySet()) {
				if (p.getValue().isJsonObject()) {
					ports.put(p.getKey(), readPort(p.getValue().getAsJsonObject()));
				}
			}
			put(l.getKey(), ports);
		}
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
			JsonObject ports = new JsonObject();
			for (Map.Entry<String, PortEdit> p : l.getValue().entrySet()) {
				ports.add(p.getKey(), writePort(p.getValue()));
			}
			JsonObject set = new JsonObject();
			set.add("ports", ports);
			layouts.add(l.getKey(), set);
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
