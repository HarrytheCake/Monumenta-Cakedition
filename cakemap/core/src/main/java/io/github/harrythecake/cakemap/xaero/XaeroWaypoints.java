package io.github.harrythecake.cakemap.xaero;

import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Xaero's Minimap waypoint files ({@code xaero/minimap/<server>/dim%<ns>$<path>/<world>_<n>.txt}):
 * one line per waypoint, {@code waypoint:name:initials:x:y:z:color:disabled:type:set:…}. Deathpoints
 * (type 1 and 2) are skipped. Pure Java.
 */
public final class XaeroWaypoints {
	/** Xaero's 16 colours (Minecraft's chat colours). */
	static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55,
			0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private XaeroWaypoints() {
	}

	public static List<Waypoint> parse(String text, String dimension) {
		List<Waypoint> out = new ArrayList<>();
		for (String line : text.split("\\R")) {
			if (!line.startsWith("waypoint:")) {
				continue;
			}
			String[] f = line.split(":", -1);
			if (f.length < 9) {
				continue;
			}
			try {
				int type = Integer.parseInt(f[8].trim());
				if (type == 1 || type == 2) {
					continue; // deathpoints
				}
				String name = unescape(f[1]);
				if (name.startsWith("gui.xaero")) {
					continue; // Xaero's own generated names (deathpoints and the like)
				}
				int x = Integer.parseInt(f[3].trim());
				int y = f[4].trim().equals("~") ? 64 : Integer.parseInt(f[4].trim());
				int z = Integer.parseInt(f[5].trim());
				Waypoint w = new Waypoint(name.isEmpty() ? unescape(f[2]) : name, WaypointKind.PLAYER, dimension, x, y, z);
				int c = Integer.parseInt(f[6].trim());
				// Xaero's chat colour → the nearest of CakeMap's swatches
				w.color = c >= 0 && c < COLORS.length ? WaypointColors.nearest(COLORS[c]) : 0;
				w.hidden = Boolean.parseBoolean(f[7].trim());
				w.source = "xaero";
				out.add(w);
			} catch (NumberFormatException e) {
				// a line we don't understand: skip it
			}
		}
		return out;
	}

	/** Xaero writes ':' in names as "§§". */
	static String unescape(String s) {
		return s.replace("§§", ":").trim();
	}
}
