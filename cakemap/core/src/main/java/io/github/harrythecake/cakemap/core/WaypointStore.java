package io.github.harrythecake.cakemap.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The waypoints of one server: the player's own (saved), built-in ones (from resource packs; only
 * their bookmarks and hidden flags are saved) and temporary ones (memory only). Pure Java.
 */
public final class WaypointStore {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Temporary waypoints this close (blocks, horizontally) to the player are reached and removed. */
	public static final double REACHED = 4;

	private final Path file;
	private final List<Waypoint> own = new ArrayList<>();
	private final List<Waypoint> builtin = new ArrayList<>();
	private final List<Waypoint> temporary = new ArrayList<>();
	private final Set<String> builtinBookmarks = new HashSet<>();
	private final Set<String> builtinHidden = new HashSet<>();
	private final Set<String> hiddenKinds = new HashSet<>();
	/** The id of the one tracked waypoint, or null. */
	private String tracked;
	private int revision;

	public WaypointStore(Path file) {
		this.file = file;
	}

	/** Bumped on every change (screens and textures compare it). */
	public int revision() {
		return revision;
	}

	public synchronized void load() {
		own.clear();
		builtinBookmarks.clear();
		builtinHidden.clear();
		hiddenKinds.clear();
		tracked = null;
		if (file == null || !Files.exists(file)) {
			// a new player: the many built-in traders start hidden (one click in "Kinds" shows them)
			hiddenKinds.add(WaypointKind.TRADER.id());
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.get("waypoints") instanceof JsonArray a) {
				for (JsonElement e : a) {
					Waypoint w = GSON.fromJson(e, Waypoint.class);
					if (w != null && w.dimension != null && !w.kind().temporary) {
						own.add(w);
					}
				}
			}
			readSet(root, "builtinBookmarks", builtinBookmarks);
			readSet(root, "builtinHidden", builtinHidden);
			readSet(root, "hiddenKinds", hiddenKinds);
			if (root.has("tracked") && root.get("tracked").isJsonPrimitive()) {
				tracked = root.get("tracked").getAsString();
			}
		} catch (IOException | RuntimeException e) {
			// a broken file: keep it aside and start fresh rather than lose it on the next save
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more to do
			}
		}
		applyBuiltinFlags();
		revision++;
	}

	private static void readSet(JsonObject root, String key, Set<String> into) {
		if (root.get(key) instanceof JsonArray a) {
			for (JsonElement e : a) {
				into.add(e.getAsString());
			}
		}
	}

	public synchronized void save() {
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("format", 1);
		root.add("waypoints", GSON.toJsonTree(own));
		root.add("builtinBookmarks", GSON.toJsonTree(new ArrayList<>(new java.util.TreeSet<>(builtinBookmarks))));
		root.add("builtinHidden", GSON.toJsonTree(new ArrayList<>(new java.util.TreeSet<>(builtinHidden))));
		root.add("hiddenKinds", GSON.toJsonTree(new ArrayList<>(new java.util.TreeSet<>(hiddenKinds))));
		if (tracked != null && find(tracked) != null && !find(tracked).temporary()) {
			root.addProperty("tracked", tracked);
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root) + "\n");
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("can't write " + file + ": " + e, e);
		}
	}

	/** Built-in waypoints (from resource packs) replace the previous set. */
	public synchronized void setBuiltin(List<Waypoint> list) {
		builtin.clear();
		for (Waypoint w : list) {
			Waypoint c = w.copy();
			c.builtin = true;
			c.source = "builtin";
			builtin.add(c);
		}
		applyBuiltinFlags();
		revision++;
	}

	private void applyBuiltinFlags() {
		for (Waypoint w : builtin) {
			w.bookmarked = builtinBookmarks.contains(w.id);
			w.hidden = builtinHidden.contains(w.id);
		}
	}

	public synchronized void add(Waypoint w) {
		if (w.temporary()) {
			temporary.add(w);
		} else {
			own.add(w);
		}
		revision++;
	}

	/** How many death waypoints a server keeps (the newest; user, 2026-11-07). */
	public static final int DEATHS_KEPT = 5;

	/**
	 * Adds a death waypoint and removes the oldest ones beyond {@code keep} (every dimension of
	 * this server counts). A tracked death that gets removed stops being tracked.
	 * @return the death waypoints removed
	 */
	public synchronized List<Waypoint> addDeath(Waypoint w, int keep) {
		own.add(w);
		List<Waypoint> deaths = new ArrayList<>();
		for (Waypoint o : own) {
			if (o.kind() == WaypointKind.DEATH) {
				deaths.add(o);
			}
		}
		// oldest first; equal times keep their order in the list (the new one is last)
		deaths.sort(java.util.Comparator.comparingLong(o -> o.created));
		List<Waypoint> removed = new ArrayList<>(deaths.subList(0, Math.max(0, deaths.size() - Math.max(1, keep))));
		for (Waypoint o : removed) {
			own.remove(o);
			if (o.id.equals(tracked)) {
				tracked = null;
			}
		}
		revision++;
		return removed;
	}

	/** Replaces a temporary waypoint from the same source (one quest-compass target at a time). */
	public synchronized void replaceTemporary(String source, Waypoint w) {
		temporary.removeIf(t -> t.source.equals(source));
		if (w != null) {
			temporary.add(w);
		}
		revision++;
	}

	public synchronized boolean remove(Waypoint w) {
		boolean r = own.removeIf(o -> o.id.equals(w.id)) | temporary.removeIf(o -> o.id.equals(w.id));
		revision++;
		return r;
	}

	/** Flags of a built-in waypoint are kept by id; a player waypoint's are its own fields. */
	public synchronized void setBookmarked(Waypoint w, boolean b) {
		w.bookmarked = b;
		if (w.builtin) {
			if (b) {
				builtinBookmarks.add(w.id);
			} else {
				builtinBookmarks.remove(w.id);
			}
		}
		revision++;
	}

	public synchronized void setHidden(Waypoint w, boolean h) {
		w.hidden = h;
		if (w.builtin) {
			if (h) {
				builtinHidden.add(w.id);
			} else {
				builtinHidden.remove(w.id);
			}
		}
		revision++;
	}

	public synchronized boolean kindHidden(WaypointKind k) {
		return hiddenKinds.contains(k.id());
	}

	public synchronized void setKindHidden(WaypointKind k, boolean h) {
		if (h) {
			hiddenKinds.add(k.id());
		} else {
			hiddenKinds.remove(k.id());
		}
		revision++;
	}

	public synchronized void changed() {
		revision++;
	}

	/** Every waypoint (own, built-in, temporary), hidden ones included. */
	public synchronized List<Waypoint> all() {
		List<Waypoint> out = new ArrayList<>(own.size() + builtin.size() + temporary.size());
		out.addAll(temporary);
		out.addAll(own);
		out.addAll(builtin);
		return out;
	}

	/** Not hidden and kind not hidden, in a dimension, whichever map is shown. */
	public synchronized List<Waypoint> visible(String dimension) {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : all()) {
			if (w.dimension.equals(dimension) && !w.hidden && !hiddenKinds.contains(w.kind)) {
				out.add(w);
			}
		}
		return out;
	}

	/**
	 * The ones to draw over a map of {@code dimension}: visible, meant for the map that is shown
	 * (static or dynamic), in drawing order (see {@link #layer}). The tracked waypoint is always
	 * included, even when hidden.
	 */
	public synchronized List<Waypoint> shown(String dimension, boolean staticShown) {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : all()) {
			if (!w.dimension.equals(dimension)) {
				continue;
			}
			boolean t = w.id.equals(tracked);
			if (t || (!w.hidden && !hiddenKinds.contains(w.kind) && w.onMap(staticShown))) {
				out.add(w);
			}
		}
		out.sort(java.util.Comparator.comparingInt(this::layer));
		return out;
	}

	/**
	 * Drawing order, bottom to top: ordinary 0, bookmarked 1, temporary 2, the quest compass
	 * target 3, the tracked waypoint 4 (so the one the player follows is never under another icon).
	 */
	public synchronized int layer(Waypoint w) {
		if (w.id.equals(tracked)) {
			return 4;
		}
		if ("compass".equals(w.source)) {
			return 3;
		}
		if (w.temporary()) {
			return 2;
		}
		return w.bookmarked ? 1 : 0;
	}

	// ---- tracking ----

	/** The tracked waypoint, or null (none, or it was deleted / reached). */
	public synchronized Waypoint tracked() {
		return tracked == null ? null : find(tracked);
	}

	public synchronized boolean isTracked(Waypoint w) {
		return w != null && w.id.equals(tracked);
	}

	/** Tracks one waypoint (null = none). */
	public synchronized void track(Waypoint w) {
		tracked = w == null ? null : w.id;
		revision++;
	}

	public synchronized Waypoint find(String id) {
		for (Waypoint w : temporary) {
			if (w.id.equals(id)) {
				return w;
			}
		}
		for (Waypoint w : own) {
			if (w.id.equals(id)) {
				return w;
			}
		}
		for (Waypoint w : builtin) {
			if (w.id.equals(id)) {
				return w;
			}
		}
		return null;
	}

	/** The nearest waypoint of a kind in a dimension to (x, z), not counting {@code except}; null when there is none. */
	public synchronized Waypoint nearest(String dimension, WaypointKind kind, double x, double z, Waypoint except) {
		Waypoint best = null;
		double bestD = Double.MAX_VALUE;
		for (Waypoint w : all()) {
			if (!w.dimension.equals(dimension) || w.kind() != kind || w.hidden || (except != null && w.id.equals(except.id))) {
				continue;
			}
			double dx = w.x + 0.5 - x;
			double dz = w.z + 0.5 - z;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = w;
			}
		}
		return best;
	}

	public synchronized List<Waypoint> own() {
		return Collections.unmodifiableList(new ArrayList<>(own));
	}

	public synchronized List<Waypoint> temporary() {
		return Collections.unmodifiableList(new ArrayList<>(temporary));
	}

	/** Removes temporary waypoints the player has reached. Returns what was removed. */
	public synchronized List<Waypoint> reach(String dimension, double px, double pz) {
		List<Waypoint> gone = new ArrayList<>();
		temporary.removeIf(w -> {
			double dx = w.x + 0.5 - px;
			double dz = w.z + 0.5 - pz;
			boolean r = w.dimension.equals(dimension) && dx * dx + dz * dz <= REACHED * REACHED;
			if (r) {
				gone.add(w);
			}
			return r;
		});
		if (!gone.isEmpty()) {
			revision++;
		}
		return gone;
	}

	public synchronized void clearTemporary() {
		temporary.clear();
		revision++;
	}

	/** Adds player waypoints unless one with the same dimension, position and name exists. Returns how many were new. */
	public synchronized int addAllNew(List<Waypoint> list) {
		Map<String, Waypoint> seen = new LinkedHashMap<>();
		for (Waypoint w : own) {
			seen.put(key(w), w);
		}
		int n = 0;
		for (Waypoint w : list) {
			if (seen.putIfAbsent(key(w), w) == null) {
				own.add(w);
				n++;
			}
		}
		revision++;
		return n;
	}

	/**
	 * Waypoints imported from Xaero earlier (source "xaero") that sit in {@code fromDimension} at the
	 * position and with the name of one in {@code moved} go to that one's dimension; they keep their
	 * id, colour, flags and tracking. If the same waypoint is already there, the old copy is dropped.
	 * Returns how many changed.
	 */
	public synchronized int moveImported(String fromDimension, List<Waypoint> moved) {
		Map<String, String> to = new HashMap<>();
		for (Waypoint m : moved) {
			to.put(fromDimension + "|" + m.x + "|" + m.y + "|" + m.z + "|" + m.name, m.dimension);
		}
		Set<String> existing = new HashSet<>();
		for (Waypoint w : own) {
			existing.add(key(w));
		}
		int n = 0;
		for (Iterator<Waypoint> it = own.iterator(); it.hasNext(); ) {
			Waypoint w = it.next();
			String dim = "xaero".equals(w.source) ? to.get(key(w)) : null;
			if (dim == null) {
				continue;
			}
			String old = key(w);
			w.dimension = dim;
			if (!existing.add(key(w))) {
				w.dimension = fromDimension;
				it.remove();
			}
			existing.remove(old);
			n++;
		}
		revision++;
		return n;
	}

	private static String key(Waypoint w) {
		return w.dimension + "|" + w.x + "|" + w.y + "|" + w.z + "|" + w.name;
	}

	/** Reads a built-in waypoint file: { "dimension id": [ { id, name, kind, x, y, z }, … ] }. */
	public static List<Waypoint> parseBuiltin(String json) throws JsonParseException {
		List<Waypoint> out = new ArrayList<>();
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		for (Map.Entry<String, JsonElement> dim : root.entrySet()) {
			if (dim.getKey().startsWith("_") || !(dim.getValue() instanceof JsonArray a)) {
				continue; // "_comment", "_source" …
			}
			for (JsonElement e : a) {
				JsonObject o = e.getAsJsonObject();
				Waypoint w = new Waypoint();
				w.dimension = dim.getKey();
				w.name = o.has("name") ? o.get("name").getAsString() : "";
				w.kind = WaypointKind.parse(o.has("kind") ? o.get("kind").getAsString() : null, WaypointKind.POI).id();
				w.x = o.get("x").getAsInt();
				w.y = o.has("y") ? o.get("y").getAsInt() : 64;
				w.z = o.get("z").getAsInt();
				w.id = o.has("id") ? o.get("id").getAsString() : "builtin:" + w.dimension + ":" + w.kind + ":" + w.x + "," + w.y + "," + w.z;
				w.builtin = true;
				w.source = "builtin";
				out.add(w);
			}
		}
		return out;
	}
}
