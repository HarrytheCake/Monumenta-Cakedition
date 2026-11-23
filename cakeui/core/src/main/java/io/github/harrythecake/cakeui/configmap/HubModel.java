package io.github.harrythecake.cakeui.configmap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What the config hub page shows and saves (glossary "Config hub"), without any Minecraft class: one entry per loaded
 * mod that has a map with {@code hub} on, its current values, the player's unsaved changes, and the pack defaults.
 * Saving writes each changed file once; it never touches the pack-defaults record (a player's choice is never
 * "untouched").
 */
public final class HubModel {
	/** One mod's section. {@link #unavailable} says why it's greyed out (null = editable). */
	public static final class Mod {
		public final String id;
		/** The mod's id as the loader knows it (one of the map's ids): for the version and the reload hook. */
		public final String modId;
		public final ConfigMap map;
		public final ConfigFile file;
		public final String version;
		public final String unavailable;
		final Map<String, Object> pending = new LinkedHashMap<>();

		Mod(String id, String modId, ConfigMap map, ConfigFile file, String version, String unavailable) {
			this.id = id;
			this.modId = modId;
			this.map = map;
			this.file = file;
			this.version = version;
			this.unavailable = unavailable;
		}

		public boolean editable() {
			return unavailable == null;
		}

		public boolean dirty() {
			return !pending.isEmpty();
		}
	}

	/** One mod's save: whether its file was written, and the problem if not. */
	public record Saved(Mod mod, boolean ok, String problem) {
	}

	public final List<Mod> mods;
	private final PackDefaults defaults;

	private HubModel(List<Mod> mods, PackDefaults defaults) {
		this.mods = mods;
		this.defaults = defaults;
	}

	/** Every map with {@code hub} on whose mod is loaded, by title; unsupported versions and unusable files greyed out. */
	public static HubModel build(Map<String, ConfigMap> maps, PackDefaults defaults, Path gameDir, Predicate<String> loaded,
			Function<String, String> versionOf) {
		List<Mod> mods = new ArrayList<>();
		for (Map.Entry<String, ConfigMap> e : maps.entrySet()) {
			ConfigMap map = e.getValue();
			String modId = map.loadedId(loaded);
			if (!map.hub || modId == null || map.options().isEmpty()) {
				continue;
			}
			String version = versionOf.apply(modId);
			if (!map.versions.contains(version)) {
				mods.add(new Mod(e.getKey(), modId, map, null, version, "version"));
				continue;
			}
			ConfigFile file = ConfigFile.open(map, gameDir);
			mods.add(new Mod(e.getKey(), modId, map, file, version, file.usable() ? null : file.problem));
		}
		mods.sort(Comparator.comparing((Mod m) -> m.map.title.toLowerCase(java.util.Locale.ROOT)).thenComparing(m -> m.id));
		return new HubModel(List.copyOf(mods), defaults == null ? PackDefaults.NONE : defaults);
	}

	/** The value shown: the unsaved change if any, else the file's (null = missing or unreadable). */
	public Object value(Mod m, ConfigMap.Option o) {
		if (m.pending.containsKey(o.key)) {
			return m.pending.get(o.key);
		}
		return m.file == null ? null : m.file.read(o);
	}

	/** Records a change (not saved yet); a value the option doesn't allow is ignored. Returns whether it was taken. */
	public boolean set(Mod m, ConfigMap.Option o, Object typed) {
		if (!m.editable() || !o.allows(typed)) {
			return false;
		}
		Object saved = m.file.read(o);
		if (saved != null && ConfigDoc.same(saved, typed)) {
			m.pending.remove(o.key);
		} else {
			m.pending.put(o.key, typed);
		}
		return true;
	}

	/** The pack's value for this option (typed), or null when the pack sets none. */
	public Object packDefault(Mod m, ConfigMap.Option o) {
		Map<String, PackDefaults.Entry> keys = defaults.entries.get(m.id);
		PackDefaults.Entry e = keys == null ? null : keys.get(o.key);
		if (e == null) {
			return null;
		}
		Object typed = o.typed(e.value());
		return typed != null && o.allows(typed) ? typed : null;
	}

	/** What "Reset" sets: the pack default, else the map's own default, else null (nothing to reset to). */
	public Object resetValue(Mod m, ConfigMap.Option o) {
		Object pack = packDefault(m, o);
		return pack != null ? pack : o.fallback;
	}

	/** Sets the option to its reset value (if it has one). */
	public void reset(Mod m, ConfigMap.Option o) {
		Object v = resetValue(m, o);
		if (v != null) {
			set(m, o, v);
		}
	}

	/** "Reset this mod": every option with a reset value. */
	public void resetMod(Mod m) {
		for (ConfigMap.Option o : m.map.options()) {
			reset(m, o);
		}
	}

	public boolean dirty() {
		return mods.stream().anyMatch(Mod::dirty);
	}

	/** Drops every unsaved change. */
	public void discard() {
		mods.forEach(m -> m.pending.clear());
	}

	/** Writes every mod with changes (each file once); a mod whose file couldn't be written keeps its changes. */
	public List<Saved> save() {
		List<Saved> out = new ArrayList<>();
		for (Mod m : mods) {
			if (!m.dirty()) {
				continue;
			}
			try {
				for (ConfigMap.Option o : m.map.options()) {
					if (m.pending.containsKey(o.key)) {
						m.file.write(o, m.pending.get(o.key));
					}
				}
			} catch (RuntimeException e) {
				out.add(new Saved(m, false, e.getMessage()));
				continue;
			}
			if (m.file.save()) {
				m.pending.clear();
				out.add(new Saved(m, true, null));
			} else {
				out.add(new Saved(m, false, m.file.problem));
			}
		}
		return out;
	}
}
