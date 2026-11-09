package io.github.harrythecake.cakemap.xaero;

import io.github.harrythecake.cakemap.core.MapSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of Xaero's Minimap settings ({@code config/xaerominimap.txt}) and key bindings
 * ({@code options.txt}) that have a CakeMap counterpart. Pure Java.
 */
public final class XaeroSettings {
	/** Xaero key option → CakeMap key binding name (without "key.cakemap."). */
	public static final Map<String, String> KEYS = Map.of(
			"key_gui.xaero_open_map", "world_map",
			"key_gui.xaero_waypoints_key", "waypoints",
			"key_gui.xaero_new_waypoint", "new_waypoint",
			"key_gui.xaero_minimap_settings", "settings",
			"key_gui.xaero_instant_waypoint", "temporary_waypoint",
			"key_gui.xaero_toggle_manual_cave_mode", "cave_mode",
			"key_gui.xaero_enlarge_map", "enlarge",
			"key_gui.xaero_zoom_in", "zoom_in",
			"key_gui.xaero_zoom_out", "zoom_out",
			"key_gui.xaero_toggle_map", "toggle_minimap");

	private XaeroSettings() {
	}

	static Map<String, String> lines(String text) {
		Map<String, String> m = new HashMap<>();
		for (String line : text.split("\\R")) {
			if (line.startsWith("//") || line.startsWith("#") || line.startsWith("module;")) {
				continue;
			}
			int c = line.indexOf(':');
			if (c > 0) {
				m.putIfAbsent(line.substring(0, c), line.substring(c + 1).trim());
			}
		}
		return m;
	}

	/** Applies what Xaero's settings say to {@code s}; returns the names of what changed. */
	public static List<String> apply(String text, MapSettings s) {
		List<String> changed = new java.util.ArrayList<>();
		Map<String, String> m = lines(text);
		if (m.containsKey("minimap")) {
			s.minimap = Boolean.parseBoolean(m.get("minimap"));
			changed.add("minimap");
		}
		if (m.containsKey("minimapShape")) {
			s.shape = "1".equals(m.get("minimapShape")) ? MapSettings.Shape.CIRCLE : MapSettings.Shape.SQUARE;
			changed.add("shape");
		}
		if (m.containsKey("lockNorth")) {
			s.rotate = !Boolean.parseBoolean(m.get("lockNorth"));
			changed.add("rotate");
		}
		if (m.containsKey("caveMaps")) {
			// Xaero: 0 = off, anything else = automatic
			s.cave = "0".equals(m.get("caveMaps")) ? MapSettings.Cave.OFF : MapSettings.Cave.AUTO;
			changed.add("cave");
		}
		if (m.containsKey("minimapOpacity")) {
			try {
				s.opacity = Math.max(0.1, Math.min(1, Double.parseDouble(m.get("minimapOpacity")) / 100.0));
				changed.add("opacity");
			} catch (NumberFormatException ignored) {
				// keep ours
			}
		}
		if (m.containsKey("showWaypoints")) {
			s.waypointsOnMinimap = Boolean.parseBoolean(m.get("showWaypoints"));
			changed.add("waypointsOnMinimap");
		}
		if (m.containsKey("showIngameWaypoints")) {
			s.waypointsInWorld = Boolean.parseBoolean(m.get("showIngameWaypoints"));
			changed.add("waypointsInWorld");
		}
		if (m.containsKey("minimapSize")) {
			try {
				int size = Integer.parseInt(m.get("minimapSize"));
				if (size > 0) {
					s.size = Math.max(48, Math.min(256, size));
					changed.add("size");
				}
			} catch (NumberFormatException ignored) {
				// 0 = Xaero's automatic size: keep ours
			}
		}
		for (String line : text.split("\\R")) {
			if (line.startsWith("module;id=xaerominimap:minimap;")) {
				Map<String, String> mod = new HashMap<>();
				for (String part : line.split(";")) {
					int eq = part.indexOf('=');
					if (eq > 0) {
						mod.put(part.substring(0, eq), part.substring(eq + 1));
					}
				}
				boolean right = Boolean.parseBoolean(mod.get("fromRight"));
				boolean bottom = Boolean.parseBoolean(mod.get("fromBottom"));
				s.corner = bottom ? (right ? MapSettings.Corner.BOTTOM_RIGHT : MapSettings.Corner.BOTTOM_LEFT)
						: (right ? MapSettings.Corner.TOP_RIGHT : MapSettings.Corner.TOP_LEFT);
				try {
					s.offsetX = Math.max(0, Math.min(400, Integer.parseInt(mod.getOrDefault("x", "4"))));
					s.offsetY = Math.max(0, Math.min(400, Integer.parseInt(mod.getOrDefault("y", "4"))));
				} catch (NumberFormatException ignored) {
					// keep ours
				}
				changed.add("position");
			}
		}
		return changed;
	}

	/** options.txt key lines for Xaero's keys → CakeMap key name → Minecraft key code ("key.keyboard.m"). */
	public static Map<String, String> keys(String optionsTxt) {
		Map<String, String> out = new HashMap<>();
		for (String line : optionsTxt.split("\\R")) {
			int c = line.indexOf(':');
			if (c <= 0) {
				continue;
			}
			String ours = KEYS.get(line.substring(0, c));
			if (ours != null) {
				out.put(ours, line.substring(c + 1).trim());
			}
		}
		return out;
	}
}
