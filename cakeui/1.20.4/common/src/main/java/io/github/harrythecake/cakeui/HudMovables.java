package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.Movable;
import io.github.harrythecake.cakeui.api.MovableSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Things mods place themselves on the HUD (API version 3, {@link Movable}): the HUD editor lists them as
 * pieces with keys {@code ~owner:id}, moves them through the mod, and lets the mod save. Render thread.
 */
public final class HudMovables {
	public static final String PREFIX = "~";
	private static final Map<String, MovableSource> SOURCES = new LinkedHashMap<>();

	private HudMovables() {
	}

	public static synchronized void register(String owner, MovableSource source) {
		if (SOURCES.containsKey(owner)) {
			throw new IllegalStateException("CakeUI: " + owner + " already added its movables");
		}
		SOURCES.put(owner, source);
	}

	/** Every movable now, by editor key. A source that throws is logged and left out. */
	static Map<String, Movable> all() {
		Map<String, Movable> out = new LinkedHashMap<>();
		for (Map.Entry<String, MovableSource> e : SOURCES.entrySet()) {
			try {
				for (Movable m : e.getValue().movables()) {
					out.put(PREFIX + e.getKey() + ":" + m.id(), m);
				}
			} catch (RuntimeException | LinkageError ex) {
				CakeUI.LOGGER.error("CakeUI: the movables of {} failed", e.getKey(), ex);
			}
		}
		return out;
	}

	/** The ids a mod offers (to leave its own HUD piece of the same name out of the editor). */
	static List<String> ids(String owner) {
		List<String> out = new ArrayList<>();
		MovableSource s = SOURCES.get(owner);
		if (s != null) {
			try {
				for (Movable m : s.movables()) {
					out.add(m.id());
				}
			} catch (RuntimeException | LinkageError ignored) {
				// logged by all()
			}
		}
		return out;
	}

	@Nullable
	static Movable get(String key) {
		return all().get(key);
	}

	/** Tests only. */
	static synchronized void clear() {
		SOURCES.clear();
	}
}
