package io.github.harrythecake.cakemap.core;

import java.util.Locale;

/**
 * Folder names for servers and dimensions. Every Monumenta address maps to "monumenta", so maps
 * and waypoints follow the player whichever address they connect with.
 */
public final class WorldKeys {
	public static final String MONUMENTA = "monumenta";

	private WorldKeys() {
	}

	/** A server address ("server.playmonumenta.com:25565") → its folder name. */
	public static String server(String address) {
		String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.equals("playmonumenta.com") || host.endsWith(".playmonumenta.com")) {
			return MONUMENTA;
		}
		return "mp_" + safe(host);
	}

	public static String singleplayer(String worldFolder) {
		return "sp_" + safe(worldFolder);
	}

	/** Xaero's "Multiplayer_server.playmonumenta.com" → our server key, or null for anything else. */
	public static String fromXaeroServerFolder(String folder) {
		if (folder.startsWith("Multiplayer_")) {
			return server(folder.substring("Multiplayer_".length()));
		}
		return null;
	}

	/** A dimension id ("monumenta:valley") → a relative folder ("monumenta/valley"). */
	public static String dimensionFolder(String dimension) {
		int c = dimension.indexOf(':');
		String ns = c < 0 ? "minecraft" : dimension.substring(0, c);
		String path = c < 0 ? dimension : dimension.substring(c + 1);
		return safe(ns) + "/" + safe(path).replace('/', '_');
	}

	/**
	 * Xaero's dimension folder names: world map "monumenta$valley", minimap "dim%monumenta$valley";
	 * "null" = the overworld, "DIM-1" = the nether, "DIM1" = the end.
	 */
	public static String fromXaeroDimension(String folder) {
		String f = folder.startsWith("dim%") ? folder.substring(4) : folder;
		switch (f) {
			case "null", "0":
				return "minecraft:overworld";
			case "DIM-1", "-1":
				return "minecraft:the_nether";
			case "DIM1", "1":
				return "minecraft:the_end";
			default:
				int d = f.indexOf('$');
				return d < 0 ? null : f.substring(0, d) + ":" + f.substring(d + 1);
		}
	}

	/** Personal worlds (plots, guild plots) never go into the static map pack. */
	public static boolean personal(String dimension) {
		String path = dimension.substring(dimension.indexOf(':') + 1);
		return path.startsWith("plot") || path.startsWith("guildplot") || path.equals("plots") || path.matches("[a-z]+\\d+");
	}

	public static String safe(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? Character.toLowerCase(c) : '_');
		}
		return sb.isEmpty() ? "_" : sb.toString();
	}
}
