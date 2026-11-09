package io.github.harrythecake.cakemap.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code config/cakemap/config.json}: every player setting. Unknown or missing fields keep their defaults. Pure Java. */
public final class MapSettings {
	public enum Shape { SQUARE, CIRCLE }

	public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	/** OFF: never; AUTO: when the sky above the player is blocked; ON: always. */
	public enum Cave { OFF, AUTO, ON }

	/** STATIC: the shard's pre-rendered map where there is one (else dynamic); DYNAMIC: only what this game has seen. */
	public enum Mode { STATIC, DYNAMIC }

	public enum Marker { ARROW, HEAD }

	/** The round minimap's frame: RING = a thick ring with N on it; LINE = the thin dark line. */
	public enum Frame { RING, LINE }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int format = 1;
	public boolean minimap = true;
	/** GUI pixels. */
	public int size = 110;
	public Shape shape = Shape.SQUARE;
	/** Only for the circle shape. */
	public Frame frame = Frame.RING;
	/** Turn the minimap with the player (false = north up). */
	public boolean rotate = true;
	/** Minimap GUI pixels per block. */
	public double zoom = 1;
	/** Top left: the top right is where vanilla puts status effects and toasts. */
	public Corner corner = Corner.TOP_LEFT;
	public int offsetX = 4;
	public int offsetY = 4;
	public double opacity = 1;
	public boolean coordinates = true;
	public Cave cave = Cave.AUTO;
	public Mode mode = Mode.STATIC;
	public Marker marker = Marker.ARROW;
	public boolean waypointsOnMinimap = true;
	public boolean waypointsInWorld = true;
	/** Built-in waypoints in the world view only when bookmarked (else they'd crowd the screen). */
	public boolean builtinInWorldOnlyBookmarked = true;
	/** The quest compass target becomes a temporary waypoint. */
	public boolean questCompass = true;
	/** Coordinates in chat get a clickable [+] that adds a temporary waypoint. */
	public boolean chatCoordinates = true;
	/** Dying adds a death waypoint (the newest WaypointStore.DEATHS_KEPT are kept). */
	public boolean deathWaypoints = true;
	/** Ask once whether to import Xaero's data when it is found. */
	public boolean xaeroImportOffered;
	/** For pack makers: the export command, ids in lists, the static-map check on every server. */
	public boolean devMode;

	public static MapSettings load(Path file) {
		if (!Files.exists(file)) {
			return new MapSettings();
		}
		try {
			MapSettings s = GSON.fromJson(Files.readString(file), MapSettings.class);
			return s == null ? new MapSettings() : s.fix();
		} catch (IOException | JsonParseException e) {
			return new MapSettings();
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this) + "\n");
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
	}

	/** Keeps values in range; nulls (unknown enum names) back to defaults. */
	public MapSettings fix() {
		MapSettings d = new MapSettings();
		size = Math.max(48, Math.min(256, size));
		zoom = zoom > 0 ? Math.max(0.125, Math.min(8, zoom)) : 1;
		opacity = Math.max(0.1, Math.min(1, opacity));
		offsetX = Math.max(0, Math.min(400, offsetX));
		offsetY = Math.max(0, Math.min(400, offsetY));
		shape = shape == null ? d.shape : shape;
		corner = corner == null ? d.corner : corner;
		cave = cave == null ? d.cave : cave;
		mode = mode == null ? d.mode : mode;
		marker = marker == null ? d.marker : marker;
		frame = frame == null ? d.frame : frame;
		return this;
	}

	/** The minimap zoom steps (GUI pixels per block). */
	public static final double[] ZOOMS = {0.25, 0.5, 1, 2, 4};

	public static double nextZoom(double z, boolean in) {
		if (in) {
			for (double s : ZOOMS) {
				if (s > z + 1e-6) {
					return s;
				}
			}
			return ZOOMS[ZOOMS.length - 1];
		}
		for (int i = ZOOMS.length - 1; i >= 0; i--) {
			if (ZOOMS[i] < z - 1e-6) {
				return ZOOMS[i];
			}
		}
		return ZOOMS[0];
	}
}
