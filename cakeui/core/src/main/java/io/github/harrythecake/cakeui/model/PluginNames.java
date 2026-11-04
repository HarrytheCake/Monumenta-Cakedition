package io.github.harrythecake.cakeui.model;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The names other mods registered through the API (button actions, HUD pieces), so the Loader can
 * check layouts without Minecraft classes. CakeUIApi and PluginHud keep the objects themselves.
 */
public final class PluginNames {
	private static final Set<String> ACTIONS = ConcurrentHashMap.newKeySet();
	private static final List<String> HUD_PIECES = new CopyOnWriteArrayList<>();

	private PluginNames() {
	}

	public static void addAction(String name) {
		ACTIONS.add(name);
	}

	public static boolean hasAction(String name) {
		return ACTIONS.contains(name);
	}

	public static Set<String> actionNames() {
		return new TreeSet<>(ACTIONS);
	}

	public static void addHudPiece(String name) {
		if (!HUD_PIECES.contains(name)) {
			HUD_PIECES.add(name);
		}
	}

	/** Every added HUD piece, in the order they were added. */
	public static List<String> hudPieces() {
		return List.copyOf(HUD_PIECES);
	}

	/** Tests only. */
	public static void clearHudPieces() {
		HUD_PIECES.clear();
	}
}
