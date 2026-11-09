package io.github.harrythecake.cakemap.core;

import java.util.UUID;

/**
 * One waypoint. Player-made ones live in {@code cakemap/<server>/waypoints.json}; built-in ones
 * come from {@code assets/<ns>/cakemap/waypoints/<server>.json} in the mod or a resource pack
 * (only their bookmark and hidden flags are saved); temporary ones are never saved.
 */
public final class Waypoint {
	public String id = UUID.randomUUID().toString();
	public String name = "";
	public String kind = WaypointKind.PLAYER.id();
	/** Dimension id, like "monumenta:valley". */
	public String dimension = "";
	public int x;
	public int y;
	public int z;
	/** RGB for a player waypoint's pin, its name label and in-world text; 0 = the default (see WaypointColors). */
	public int color;
	/** Which map shows it: "both", "static" (only over a static map) or "dynamic" (only over the dynamic map). */
	public String maps = BOTH;
	public boolean bookmarked;
	public boolean hidden;
	/** "player", "builtin", "xaero", "compass", "chat". */
	public String source = "player";
	public static final String BOTH = "both";
	public static final String STATIC = "static";
	public static final String DYNAMIC = "dynamic";
	public long created = System.currentTimeMillis();

	/** Not saved: built-in and temporary waypoints are rebuilt each session. */
	public transient boolean builtin;

	public Waypoint() {
	}

	public Waypoint(String name, WaypointKind kind, String dimension, int x, int y, int z) {
		this.name = name;
		this.kind = kind.id();
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public WaypointKind kind() {
		return WaypointKind.parse(kind, WaypointKind.PLAYER);
	}

	public boolean temporary() {
		return kind().temporary;
	}

	/** Shown over the map that is drawn now (static or dynamic)? */
	public boolean onMap(boolean staticShown) {
		if (STATIC.equals(maps)) {
			return staticShown;
		}
		if (DYNAMIC.equals(maps)) {
			return !staticShown;
		}
		return true;
	}

	public double distanceSq(double px, double py, double pz) {
		double dx = x + 0.5 - px;
		double dy = y - py;
		double dz = z + 0.5 - pz;
		return dx * dx + dy * dy + dz * dz;
	}

	public Waypoint copy() {
		Waypoint w = new Waypoint();
		w.id = id;
		w.name = name;
		w.kind = kind;
		w.dimension = dimension;
		w.x = x;
		w.y = y;
		w.z = z;
		w.color = color;
		w.maps = maps;
		w.bookmarked = bookmarked;
		w.hidden = hidden;
		w.source = source;
		w.created = created;
		w.builtin = builtin;
		return w;
	}
}
