package io.github.harrythecake.cakemonu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ability icons the player took out of UMM's bar, keyed by UMM's ability id ({@code AbilityInfo.getOrderId()},
 * "class/ability name" in lower case), so each class keeps its own layout. Stored per player in
 * {@code config/cakemonu/ability-icons.json}:
 * <pre>{ "format": 1, "icons": { "mage/magma shield": { "anchorX": 0.5, "anchorY": 1, "x": -60, "y": -70, "size": 0 } } }</pre>
 */
public final class IconLayout {
	public static final int FORMAT = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Map<String, IconPlacement> icons = new LinkedHashMap<>();

	public IconPlacement get(String id) {
		return icons.get(id);
	}

	public boolean has(String id) {
		return icons.containsKey(id);
	}

	public void put(String id, IconPlacement p) {
		icons.put(id, p);
	}

	public void remove(String id) {
		icons.remove(id);
	}

	public void clear() {
		icons.clear();
	}

	public boolean isEmpty() {
		return icons.isEmpty();
	}

	public Map<String, IconPlacement> all() {
		return Collections.unmodifiableMap(icons);
	}

	public IconLayout copy() {
		IconLayout c = new IconLayout();
		c.icons.putAll(icons);
		return c;
	}

	/** Reads the file; missing = empty. An unreadable file is moved aside to {@code <name>.broken} (logged) and the layout starts empty. */
	public static IconLayout read(Path file) {
		IconLayout layout = new IconLayout();
		if (!Files.isRegularFile(file)) {
			return layout;
		}
		try {
			layout.parse(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			CakeMonu.LOG.warn("CakeMonu: can't read {} ({}); keeping it as {}.broken and starting with every icon in UMM's bar", file, e.toString(),
					file.getFileName());
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// it stays; the next save replaces it
			}
			layout.icons.clear();
		}
		return layout;
	}

	/** For tests and {@link #read}: the file's text. Entries that don't make sense are skipped. */
	public void parse(String text) {
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		JsonElement format = root.get("format");
		if (format != null && format.getAsInt() > FORMAT) {
			throw new IllegalStateException("format " + format.getAsInt() + " is newer than this CakeMonu (" + FORMAT + ")");
		}
		JsonObject list = root.has("icons") ? root.getAsJsonObject("icons") : new JsonObject();
		for (Map.Entry<String, JsonElement> e : list.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject o = e.getValue().getAsJsonObject();
			icons.put(e.getKey(), new IconPlacement(num(o, "anchorX"), num(o, "anchorY"), (int) num(o, "x"), (int) num(o, "y"), (int) num(o, "size")));
		}
	}

	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		JsonObject list = new JsonObject();
		for (Map.Entry<String, IconPlacement> e : icons.entrySet()) {
			IconPlacement p = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("anchorX", p.anchorX);
			o.addProperty("anchorY", p.anchorY);
			o.addProperty("x", p.offsetX);
			o.addProperty("y", p.offsetY);
			o.addProperty("size", p.size);
			list.add(e.getKey(), o);
		}
		root.add("icons", list);
		return GSON.toJson(root) + "\n";
	}

	/** Writes {@code <file>.tmp}, then moves it over the file. */
	public void write(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
	}
}
