package io.github.harrythecake.cakeui.configmap;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The pack's wanted values for other mods' options ({@code config/cakeui/pack-defaults.json5}, pack-owned) and how
 * they are applied at startup (ADR 0011, glossary "Pack default").
 *
 * <pre>
 * {
 *   format: 1,
 *   // map id (the config map's file name) -> option key (as in the map) -> the pack's value and its earlier ones
 *   "sodium": { "quality.weather_quality": { value: "FAST", history: ["DEFAULT"] } },
 *   "minecraft": { "renderClouds": { value: '"false"' } },
 * }
 * </pre>
 *
 * An option is <b>untouched</b> when the file's value equals the value CakeUI last wrote there (the per-player
 * {@link Record}), the pack's current value, or one in its {@code history}, or when the key isn't in the file: then
 * the pack's value is written. Any other value is the player's and is kept. Players' choices always win.
 */
public final class PackDefaults {
	/** One option's pack value and earlier pack values, as written in the file (map scalars). */
	public record Entry(Object value, List<Object> history, String where) {
	}

	/** map id -> option key -> entry, in file order. */
	public final Map<String, Map<String, Entry>> entries;

	private PackDefaults(Map<String, Map<String, Entry>> entries) {
		this.entries = entries;
	}

	public static final PackDefaults NONE = new PackDefaults(Map.of());

	/** Reads the file's parsed text; problems name file and line. */
	public static PackDefaults read(JValue root, List<String> problems) {
		Map<String, Map<String, Entry>> out = new LinkedHashMap<>();
		if (!(root instanceof JValue.JObject o)) {
			problems.add(root.where() + ": pack defaults are an object { <map id>: { <option key>: { value } } }");
			return NONE;
		}
		if (o.get("format") instanceof JValue.JNumber n && n.value > 1) {
			problems.add(o.get("format").where() + ": format " + (int) n.value + " is newer than this CakeUI (1); not applied");
			return NONE;
		}
		for (Map.Entry<String, JValue> m : o.map.entrySet()) {
			if (m.getKey().equals("format")) {
				continue;
			}
			if (!(m.getValue() instanceof JValue.JObject options)) {
				problems.add(m.getValue().where() + ": \"" + m.getKey() + "\" holds { <option key>: { value } }");
				continue;
			}
			Map<String, Entry> keys = new LinkedHashMap<>();
			for (Map.Entry<String, JValue> k : options.map.entrySet()) {
				Object value;
				List<Object> history = new ArrayList<>();
				if (k.getValue() instanceof JValue.JObject e) {
					value = scalar(e.get("value"));
					if (e.get("history") instanceof JValue.JArray h) {
						for (JValue hv : h.list) {
							if (scalar(hv) != null) {
								history.add(scalar(hv));
							}
						}
					}
				} else {
					value = scalar(k.getValue());  // a bare value: no history
				}
				if (value == null) {
					problems.add(k.getValue().where() + ": " + m.getKey() + " / " + k.getKey() + ": missing \"value\"");
					continue;
				}
				keys.put(k.getKey(), new Entry(value, List.copyOf(history), k.getValue().where()));
			}
			out.put(m.getKey(), keys);
		}
		return new PackDefaults(out);
	}

	private static Object scalar(JValue v) {
		if (v instanceof JValue.JString s) {
			return s.value;
		}
		if (v instanceof JValue.JNumber n) {
			return n.value;
		}
		if (v instanceof JValue.JBool b) {
			return b.value;
		}
		return null;
	}

	/**
	 * The per-player record of what CakeUI last wrote for each pack default
	 * ({@code config/cakeui/pack-defaults-applied.json5}, never shipped).
	 */
	public static final class Record {
		final Map<String, Map<String, Object>> values = new LinkedHashMap<>();

		public static Record read(String text, List<String> problems) {
			Record r = new Record();
			if (text == null || text.isBlank()) {
				return r;
			}
			try {
				if (Json5.parse(text, "pack-defaults-applied.json5") instanceof JValue.JObject o) {
					for (Map.Entry<String, JValue> m : o.map.entrySet()) {
						if (m.getValue() instanceof JValue.JObject keys) {
							for (Map.Entry<String, JValue> k : keys.map.entrySet()) {
								Object v = scalar(k.getValue());
								if (v != null) {
									r.values.computeIfAbsent(m.getKey(), x -> new LinkedHashMap<>()).put(k.getKey(), v);
								}
							}
						}
					}
				}
			} catch (RuntimeException e) {
				problems.add("pack-defaults-applied.json5 is unreadable (" + e.getMessage() + "); starting a new one");
			}
			return r;
		}

		public Object get(String map, String key) {
			Map<String, Object> m = values.get(map);
			return m == null ? null : m.get(key);
		}

		void put(String map, String key, Object typed) {
			values.computeIfAbsent(map, x -> new LinkedHashMap<>()).put(key, typed);
		}

		public String text() {
			StringBuilder sb = new StringBuilder("// Written by CakeUI: the value it last wrote for each pack default (per player, never shipped).\n{\n");
			for (Map.Entry<String, Map<String, Object>> m : values.entrySet()) {
				sb.append("  ").append(ConfigDoc.quoted(m.getKey())).append(": {\n");
				for (Map.Entry<String, Object> k : m.getValue().entrySet()) {
					Object v = k.getValue();
					String lit = v instanceof String s ? ConfigDoc.quoted(s) : v instanceof Number ? ConfigDoc.number(v) : v.toString();
					sb.append("    ").append(ConfigDoc.quoted(k.getKey())).append(": ").append(lit).append(",\n");
				}
				sb.append("  },\n");
			}
			return sb.append("}\n").toString();
		}
	}

	/** What one startup run did; {@link #lines} explain every option that wasn't simply already right. */
	public static final class Report {
		public int written;
		public int kept;
		public int unchanged;
		public final List<String> lines = new ArrayList<>();
		public final List<String> problems = new ArrayList<>();
		public boolean recordChanged;
	}

	/**
	 * Applies every pack default whose map is known, whose mod is loaded and in the map's versions, and whose file
	 * exists; saves each changed file in one step and updates the record only for files that were saved.
	 */
	public Report apply(Map<String, ConfigMap> maps, Record record, Path gameDir, Predicate<String> loaded,
			Function<String, String> versionOf) {
		Report rep = new Report();
		for (Map.Entry<String, Map<String, Entry>> m : entries.entrySet()) {
			String id = m.getKey();
			ConfigMap map = maps.get(id);
			if (map == null) {
				rep.problems.add("pack defaults name \"" + id + "\", but no config map has that id");
				continue;
			}
			String modId = map.loadedId(loaded);
			if (modId == null) {
				continue;  // the mod isn't installed (e.g. an optional mod switched off): nothing to do
			}
			String version = versionOf.apply(modId);
			if (!map.versions.contains(version)) {
				rep.lines.add(id + ": skipped, " + modId + " " + version + " isn't in the map's versions (" + map.versions + ")");
				continue;
			}
			ConfigFile file = ConfigFile.open(map, gameDir);
			if (!file.usable()) {
				rep.lines.add(id + ": skipped, " + file.problem);
				continue;
			}
			Map<String, Object> staged = new LinkedHashMap<>();
			for (Map.Entry<String, Entry> k : m.getValue().entrySet()) {
				ConfigMap.Option option = map.option(k.getKey());
				Entry e = k.getValue();
				if (option == null) {
					rep.problems.add(e.where + ": " + id + " has no option \"" + k.getKey() + "\"");
					continue;
				}
				Object pack = option.typed(e.value);
				if (pack == null || !option.allows(pack)) {
					rep.problems.add(e.where + ": " + id + " / " + k.getKey() + ": the pack value " + e.value + " doesn't fit the option");
					continue;
				}
				boolean present = file.has(option);
				Object current = file.read(option);
				if (present && current == null) {
					rep.problems.add(id + " / " + k.getKey() + ": the file's value can't be read as " + option.kind + "; left alone");
					continue;
				}
				boolean untouched = !present || ConfigDoc.same(current, record.get(id, k.getKey()))
						|| ConfigDoc.same(current, pack)
						|| e.history.stream().map(option::typed).anyMatch(h -> h != null && ConfigDoc.same(current, h));
				if (!untouched) {
					rep.kept++;
					rep.lines.add(id + " / " + k.getKey() + ": kept the player's " + current + " (pack: " + pack + ")");
					continue;
				}
				if (present && ConfigDoc.same(current, pack)) {
					rep.unchanged++;
				} else {
					try {
						file.write(option, pack);
					} catch (RuntimeException ex) {
						rep.problems.add(id + " / " + k.getKey() + ": can't write it: " + ex.getMessage());
						continue;
					}
					rep.written++;
					rep.lines.add(id + " / " + k.getKey() + ": " + (present ? current : "(missing)") + " -> " + pack);
				}
				staged.put(k.getKey(), pack);
			}
			if (!file.save()) {
				rep.problems.add(id + ": " + file.problem);
				continue;
			}
			for (Map.Entry<String, Object> s : staged.entrySet()) {
				if (!ConfigDoc.same(record.get(id, s.getKey()), s.getValue())) {
					record.put(id, s.getKey(), s.getValue());
					rep.recordChanged = true;
				}
			}
		}
		return rep;
	}
}
