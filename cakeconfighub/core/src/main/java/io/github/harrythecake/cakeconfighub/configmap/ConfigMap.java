package io.github.harrythecake.cakeconfighub.configmap;

import io.github.harrythecake.cakeconfighub.json.JValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A config map: one JSON5 file describing another mod's config file for CakeConfighub (ADR 0011, glossary "Config map").
 *
 * <pre>
 * {
 *   format: 1,
 *   mod: "zoomify",              // mod id ("minecraft" for options.txt); a list when loaders use different ids
 *   versions: ">=2.13 &lt;3",      // mod versions it fits (VersionRange); others are skipped
 *   file: "config/zoomify.json", // relative to the game folder
 *   type: "json",                // json | json5 | toml | options | properties
 *   reload: "auto",              // auto (the mod's reload hook, else after a restart) | restart
 *   hub: true,                   // false = pack defaults only, not on the hub page
 *   title: "Zoomify",            // text or a translation key
 *   sections: [ { title: "…", options: [ { key: "initialZoom", type: "int", min: 1, max: 10, label: "…" } ] } ],
 * }
 * </pre>
 *
 * Option types: bool, int, float (min, max, step), enum (values: scalars or {value, label}), string, colour
 * (colourAs: "string" = "#RRGGBB" / "#AARRGGBB" with alpha: true, or "int"). Option keys are dotted paths
 * ("overlay.overlayTextScale"; "\." for a dot inside a key) or a list of keys. {@code default} is the option's own
 * default for "Reset this mod" where the pack sets none.
 */
public final class ConfigMap {
	public final String source;
	/** The first mod id (for messages); {@link #mods} has every id the mod may have. */
	public final String mod;
	public final List<String> mods;
	public final VersionRange versions;
	public final String file;
	public final Format type;
	public final boolean reloadable;
	public final boolean hub;
	public final String title;
	public final List<Section> sections;

	public record Section(String title, List<Option> options) {
	}

	public record Choice(Object value, String label) {
	}

	public enum Kind {
		BOOL, INT, FLOAT, ENUM, STRING, COLOUR;

		static Kind of(String s) {
			return switch (s) {
				case "bool", "boolean" -> BOOL;
				case "int", "integer" -> INT;
				case "float", "number", "double" -> FLOAT;
				case "enum" -> ENUM;
				case "string", "text" -> STRING;
				case "colour", "color" -> COLOUR;
				default -> null;
			};
		}
	}

	private ConfigMap(String source, List<String> mods, VersionRange versions, String file, Format type, boolean reloadable,
			boolean hub, String title, List<Section> sections) {
		this.source = source;
		this.mods = mods;
		this.mod = mods.get(0);
		this.versions = versions;
		this.file = file;
		this.type = type;
		this.reloadable = reloadable;
		this.hub = hub;
		this.title = title;
		this.sections = sections;
	}

	/** The first of {@link #mods} that is loaded, or null. */
	public String loadedId(java.util.function.Predicate<String> loaded) {
		for (String id : mods) {
			if (loaded.test(id)) {
				return id;
			}
		}
		return null;
	}

	/** Every option, in order. */
	public List<Option> options() {
		List<Option> out = new ArrayList<>();
		for (Section s : sections) {
			out.addAll(s.options);
		}
		return out;
	}

	/** The option with this key (as written in the map), or null. */
	public Option option(String key) {
		for (Section s : sections) {
			for (Option o : s.options) {
				if (o.key.equals(key)) {
					return o;
				}
			}
		}
		return null;
	}

	/** The map, or null when it can't be used; problems name the map's source and line. */
	public record Read(ConfigMap map, List<String> problems) {
	}

	/** Reads a parsed map file; bad options are left out (with a problem), a map without mod/file/type is null. */
	public static Read read(JValue root, String source) {
		List<String> problems = new ArrayList<>();
		if (!(root instanceof JValue.JObject o)) {
			problems.add(source + ": a config map is an object { … }");
			return new Read(null, problems);
		}
		if (o.get("format") instanceof JValue.JNumber n && n.value > 1) {
			problems.add(o.get("format").where() + ": format " + (int) n.value + " is newer than this CakeConfighub (1)");
			return new Read(null, problems);
		}
		List<String> mods = new ArrayList<>();
		if (o.get("mod") instanceof JValue.JArray ids) {
			for (JValue id : ids.list) {
				if (id instanceof JValue.JString s && !s.value.isBlank()) {
					mods.add(s.value);
				}
			}
		} else if (str(o, "mod") != null && !str(o, "mod").isBlank()) {
			mods.add(str(o, "mod"));
		}
		String file = str(o, "file");
		Format type = str(o, "type") == null ? null : Format.of(str(o, "type"));
		if (mods.isEmpty()) {
			problems.add(o.where() + ": missing \"mod\" (the mod id, or a list of ids)");
		}
		if (file == null || file.isBlank() || file.contains("..") || file.startsWith("/") || file.matches("^[A-Za-z]:.*")) {
			problems.add(o.where() + ": \"file\" must be a path inside the game folder, e.g. \"config/zoomify.json\"");
			file = null;
		}
		if (type == null) {
			problems.add(o.where() + ": \"type\" must be one of json, json5, toml, options, properties");
		}
		VersionRange versions = VersionRange.ANY;
		if (str(o, "versions") != null) {
			try {
				versions = VersionRange.parse(str(o, "versions"));
			} catch (IllegalArgumentException e) {
				problems.add(o.get("versions").where() + ": " + e.getMessage());
				return new Read(null, problems);
			}
		}
		if (mods.isEmpty() || file == null || type == null) {
			return new Read(null, problems);
		}
		String reload = str(o, "reload");
		if (reload != null && !reload.equals("auto") && !reload.equals("restart")) {
			problems.add(o.get("reload").where() + ": \"reload\" is auto or restart");
		}
		boolean hub = !(o.get("hub") instanceof JValue.JBool b) || b.value;
		String title = str(o, "title") != null ? str(o, "title") : mods.get(0);
		List<Section> sections = new ArrayList<>();
		if (o.get("sections") instanceof JValue.JArray arr) {
			for (JValue sv : arr.list) {
				if (!(sv instanceof JValue.JObject so)) {
					problems.add(sv.where() + ": a section is an object { title, options }");
					continue;
				}
				sections.add(new Section(str(so, "title") != null ? str(so, "title") : "", options(so.get("options"), type, problems)));
			}
		} else if (o.get("options") != null) {
			sections.add(new Section("", options(o.get("options"), type, problems)));  // a map with one section
		} else {
			problems.add(o.where() + ": no \"sections\" (or \"options\")");
		}
		return new Read(new ConfigMap(source, List.copyOf(mods), versions, file, type, !"restart".equals(reload), hub, title,
				List.copyOf(sections)), problems);
	}

	private static List<Option> options(JValue v, Format type, List<String> problems) {
		List<Option> out = new ArrayList<>();
		if (!(v instanceof JValue.JArray arr)) {
			if (v != null) {
				problems.add(v.where() + ": \"options\" is a list");
			}
			return out;
		}
		for (JValue ov : arr.list) {
			Option opt = Option.read(ov, type, problems);
			if (opt != null) {
				out.add(opt);
			}
		}
		return List.copyOf(out);
	}

	static String str(JValue.JObject o, String key) {
		return o.get(key) instanceof JValue.JString s ? s.value : null;
	}

	/** One mapped option of the file. */
	public static final class Option {
		public final String key;
		public final List<String> path;
		public final Kind kind;
		public final Format format;
		public final String label;
		public final Double min;
		public final Double max;
		public final Double step;
		public final List<Choice> choices;
		/** The typed default (as {@link #read} returns), or null. */
		public final Object fallback;
		public final boolean colourAsInt;
		public final boolean alpha;

		private Option(String key, List<String> path, Kind kind, Format format, String label, Double min, Double max,
				Double step, List<Choice> choices, Object fallback, boolean colourAsInt, boolean alpha) {
			this.key = key;
			this.path = path;
			this.kind = kind;
			this.format = format;
			this.label = label;
			this.min = min;
			this.max = max;
			this.step = step;
			this.choices = choices;
			this.fallback = fallback;
			this.colourAsInt = colourAsInt;
			this.alpha = alpha;
		}

		static Option read(JValue v, Format format, List<String> problems) {
			if (!(v instanceof JValue.JObject o)) {
				problems.add(v.where() + ": an option is an object { key, type, label }");
				return null;
			}
			String key;
			List<String> path;
			if (o.get("key") instanceof JValue.JArray keys) {
				List<String> parts = new ArrayList<>();
				for (JValue k : keys.list) {
					if (!(k instanceof JValue.JString s)) {
						problems.add(k.where() + ": a key list holds texts");
						return null;
					}
					parts.add(s.value);
				}
				if (parts.isEmpty()) {
					problems.add(o.where() + ": empty \"key\"");
					return null;
				}
				path = List.copyOf(parts);
				key = String.join(".", parts);
			} else if (str(o, "key") != null && !str(o, "key").isEmpty()) {
				key = str(o, "key");
				path = format.untyped() ? List.of(key) : ConfigDoc.path(key);
			} else {
				problems.add(o.where() + ": missing \"key\"");
				return null;
			}
			Kind kind = str(o, "type") == null ? null : Kind.of(str(o, "type").toLowerCase(Locale.ROOT));
			if (kind == null) {
				problems.add(o.where() + ": option " + key + ": \"type\" must be bool, int, float, enum, string or colour");
				return null;
			}
			Double min = num(o, "min"), max = num(o, "max"), step = num(o, "step");
			List<Choice> choices = new ArrayList<>();
			if (kind == Kind.ENUM) {
				if (!(o.get("values") instanceof JValue.JArray vals) || vals.list.isEmpty()) {
					problems.add(o.where() + ": option " + key + ": an enum needs \"values\"");
					return null;
				}
				for (JValue c : vals.list) {
					if (c instanceof JValue.JObject co) {
						Object cv = scalar(co.get("value"));
						if (cv == null) {
							problems.add(co.where() + ": option " + key + ": a choice needs a \"value\"");
							continue;
						}
						choices.add(new Choice(cv, str(co, "label") != null ? str(co, "label") : String.valueOf(cv)));
					} else {
						Object cv = scalar(c);
						if (cv == null) {
							problems.add(c.where() + ": option " + key + ": a choice is a text, number, true/false or { value, label }");
							continue;
						}
						choices.add(new Choice(cv, display(cv)));
					}
				}
			}
			String colourAs = str(o, "colourAs") != null ? str(o, "colourAs") : str(o, "colorAs");
			boolean alpha = o.get("alpha") instanceof JValue.JBool b && b.value;
			Option opt = new Option(key, path, kind, format, str(o, "label") != null ? str(o, "label") : key, min, max, step,
					List.copyOf(choices), null, "int".equals(colourAs), alpha);
			Object fallback = o.get("default") == null ? null : opt.read(fileValue(scalar(o.get("default")), format));
			if (o.get("default") != null && fallback == null) {
				problems.add(o.get("default").where() + ": option " + key + ": the default doesn't fit the option's type");
			}
			return fallback == null ? opt : new Option(key, path, kind, format, opt.label, min, max, step, opt.choices,
					fallback, opt.colourAsInt, alpha);
		}

		private static Double num(JValue.JObject o, String k) {
			return o.get(k) instanceof JValue.JNumber n ? n.value : null;
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

		/** A map-file scalar as a file of this format would hold it (line formats hold raw text). */
		private static Object fileValue(Object v, Format format) {
			if (v == null || !format.untyped()) {
				return v;
			}
			return v instanceof Number ? ConfigDoc.number(integral(v)) : String.valueOf(v);
		}

		private static Object integral(Object v) {
			if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
				return d.longValue();
			}
			return v;
		}

		private static String display(Object v) {
			return v instanceof Number ? ConfigDoc.number(integral(v)).replaceAll("\\.0$", "") : String.valueOf(v);
		}

		/** A value written in a map or the pack defaults (a JSON5 scalar) as this option's type, or null. */
		public Object typed(Object mapScalar) {
			return read(fileValue(mapScalar, format));
		}

		/**
		 * A value read from the file ({@link ConfigDoc#get}) as this option's type: Boolean, Long (int, colour),
		 * Double (float), the matching choice's value (enum) or String; null when missing or not readable as that.
		 */
		public Object read(Object raw) {
			if (raw == null || raw == ConfigDoc.OTHER) {
				return null;
			}
			try {
				switch (kind) {
					case BOOL:
						if (raw instanceof Boolean b) {
							return b;
						}
						return "true".equals(raw) ? Boolean.TRUE : "false".equals(raw) ? Boolean.FALSE : null;
					case INT:
						double i = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim());
						return i == Math.rint(i) ? (Object) (long) i : null;
					case FLOAT:
						return raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString().trim());
					case ENUM:
						for (Choice c : choices) {
							if (ConfigDoc.same(c.value, raw) || (format.untyped() && fileValue(c.value, format).equals(raw))) {
								return c.value;
							}
						}
						return null;
					case STRING:
						return raw instanceof String s ? s : null;
					case COLOUR:
						if (raw instanceof Number n) {
							long c = n.longValue() & 0xFFFFFFFFL;
							return alpha ? c : c & 0xFFFFFF;
						}
						String s = raw.toString().trim();
						if (s.startsWith("#")) {
							s = s.substring(1);
						} else if (s.startsWith("0x") || s.startsWith("0X")) {
							s = s.substring(2);
						} else if (colourAsInt) {
							long c = Long.parseLong(s) & 0xFFFFFFFFL;
							return alpha ? c : c & 0xFFFFFF;
						}
						if (s.length() != 6 && s.length() != 8) {
							return null;
						}
						long c = Long.parseLong(s, 16);
						return alpha ? (s.length() == 6 ? 0xFF000000L | c : c) : c & 0xFFFFFF;
					default:
						return null;
				}
			} catch (NumberFormatException e) {
				return null;
			}
		}

		/** A typed value (as {@link #read} returns) in the form {@link ConfigDoc#set} writes for this file. */
		public Object toFile(Object typed) {
			Object v = switch (kind) {
				case BOOL -> (Boolean) typed;
				case INT -> ((Number) typed).longValue();
				case FLOAT -> ((Number) typed).doubleValue();
				case ENUM -> integral(typed);
				case STRING -> (String) typed;
				case COLOUR -> {
					long c = ((Number) typed).longValue() & 0xFFFFFFFFL;
					if (!alpha) {
						c &= 0xFFFFFF;
					}
					if (colourAsInt) {
						yield (long) (int) c;  // signed 32-bit, as Java mods store an int colour
					}
					yield alpha ? String.format("#%08X", c) : String.format("#%06X", c);
				}
			};
			return format.untyped() && v instanceof Boolean b ? b.toString() : v;
		}

		/** Whether a typed value is allowed: in range, one of the choices. */
		public boolean allows(Object typed) {
			if (typed == null) {
				return false;
			}
			return switch (kind) {
				case BOOL -> typed instanceof Boolean;
				case INT, FLOAT -> typed instanceof Number n && (min == null || n.doubleValue() >= min)
						&& (max == null || n.doubleValue() <= max);
				case ENUM -> choices.stream().anyMatch(c -> ConfigDoc.same(c.value, typed));
				case STRING -> typed instanceof String;
				case COLOUR -> typed instanceof Number;
			};
		}
	}
}
