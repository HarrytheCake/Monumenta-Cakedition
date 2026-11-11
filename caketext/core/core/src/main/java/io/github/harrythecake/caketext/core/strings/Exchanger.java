package io.github.harrythecake.caketext.core.strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts between our strings files ({@code .json}/{@code .json.gz}, see {@link StringsFile}) and Gettext PO, the
 * format Crowdin syncs. Both directions keep everything that matters:
 * <ul>
 *   <li>{@code src} ↔ {@code msgid}, {@code dst} ↔ {@code msgstr}</li>
 *   <li>{@code scope} ↔ {@code msgctxt} as {@code key=value;key=value} (sorted), so the same English with different
 *       scopes stays separate strings in Crowdin</li>
 *   <li>{@code note}, {@code count}/{@code first}/{@code last}, {@code contexts} and {@code engine} ↔ {@code #.}
 *       comments, which Crowdin shows translators as context. They're written in a fixed form and read back.</li>
 * </ul>
 *
 * <h2>Crowdin layout</h2>
 * {@link #writeCrowdin} writes one English source file per surface plus a {@code crowdin.yml}:
 * <pre>
 * &lt;dir&gt;/crowdin.yml
 * &lt;dir&gt;/en_us/chat.pot, tooltip.pot, nameplate.pot…
 * </pre>
 * Crowdin returns {@code translations/zh_cn/chat.po}…, which the translator reads as they are.
 * A string goes in the file of the surface it was first seen on, so it never moves between files.
 *
 * <p>Command line (also {@code ./gradlew :core:exchange --args="…"}):
 * {@code Exchanger <files or folders…> [--out <folder>]}: {@code .json}/{@code .json.gz} become PO files,
 * {@code .po}/{@code .pot} become {@code .json}.
 */
public final class Exchanger {
	private static final String NOTE = "note: ";
	private static final String SEEN = "seen ";
	private static final String WHERE = "where: ";
	private static final String ENGINE = "engine: ";
	private static final String PLACEHOLDERS = "placeholders: ";
	private static final Pattern SEEN_LINE = Pattern.compile("seen (\\d+) times?(?:, first ([^,\\s]+))?(?:, last ([^,\\s]+))?");
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(?:c\\d+|~\\d*|n\\d*)}");
	private static final String[] WHERE_KEYS = {"surface", "server", "dimension", "shard", "area"};

	private Exchanger() {
	}

	/** Can the translator (or this class) read it? {@code .json}, {@code .json.gz}, {@code .po}. */
	public static boolean isReadable(Path path) {
		String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return n.endsWith(".json") || n.endsWith(".json.gz") || n.endsWith(".po");
	}

	/** Any supported file as a strings file. */
	public static StringsFile read(Path path) throws IOException {
		String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return n.endsWith(".po") || n.endsWith(".pot") ? fromPo(PoFile.read(path)) : StringsFile.read(path);
	}

	// ---- ours → PO ----

	/**
	 * A strings file as PO. {@code template} = a {@code .pot} (Crowdin source: no translations, no Language);
	 * {@code positions} = keep block positions in the "where" comments.
	 */
	public static PoFile toPo(StringsFile file, List<StringEntry> entries, boolean template, boolean positions) {
		PoFile po = new PoFile();
		po.header.put("Project-Id-Version", "CakeText" + (file.server == null ? "" : " " + file.server));
		po.header.put("POT-Creation-Date", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mmZ")));
		if (!template && file.lang != null) {
			po.header.put("Language", file.lang);
		}
		po.header.put("MIME-Version", "1.0");
		po.header.put("Content-Type", "text/plain; charset=UTF-8");
		po.header.put("Content-Transfer-Encoding", "8bit");
		po.header.put("X-Generator", "CakeText");
		po.header.put("X-CakeText-Source", file.source == null ? "en_us" : file.source);
		if (file.server != null) {
			po.header.put("X-CakeText-Server", file.server);
		}
		for (StringEntry e : entries) {
			PoFile.Entry p = new PoFile.Entry();
			p.context = contextOf(e.scope);
			p.id = e.src;
			p.str = template || e.dst == null ? "" : e.dst;
			if (e.note != null && !e.note.isBlank()) {
				p.comments.add(NOTE + e.note.replace("\n", " "));
			}
			if (e.count != null) {
				p.comments.add(SEEN + e.count + (e.count == 1 ? " time" : " times")
						+ (e.first == null ? "" : ", first " + e.first) + (e.last == null ? "" : ", last " + e.last));
			}
			if (e.contexts != null) {
				for (Map<String, Object> c : e.contexts) {
					p.comments.add(WHERE + where(c, positions));
				}
			}
			if (e.engine != null && !e.engine.equals("human")) {
				p.comments.add(ENGINE + e.engine);
			}
			if (PLACEHOLDER.matcher(e.src).find()) {
				p.comments.add(PLACEHOLDERS + "{~} = a number, {n} = a player name, {c1} {c2}… = colour changes. Keep them; you may reorder them.");
			}
			po.entries.add(p);
		}
		return po;
	}

	/** {@code surface | server | dimension | shard | area | x y z}, "-" for missing. */
	private static String where(Map<String, Object> c, boolean positions) {
		StringBuilder sb = new StringBuilder();
		for (String key : WHERE_KEYS) {
			Object v = c.get(key);
			sb.append(sb.isEmpty() ? "" : " | ").append(v == null ? "-" : v);
		}
		Object pos = c.get("pos");
		sb.append(" | ");
		if (positions && pos instanceof List<?> list && list.size() == 3) {
			sb.append(num(list.get(0))).append(' ').append(num(list.get(1))).append(' ').append(num(list.get(2)));
		} else if (positions && pos instanceof int[] a && a.length == 3) {
			sb.append(a[0]).append(' ').append(a[1]).append(' ').append(a[2]);
		} else {
			sb.append('-');
		}
		return sb.toString();
	}

	private static String num(Object o) {
		return o instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(o);
	}

	/** Scope → msgctxt: {@code key=value} pairs, sorted, joined with {@code ;}. Null for no scope. */
	static String contextOf(Map<String, String> scope) {
		if (scope == null || scope.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		new TreeMap<>(scope).forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ";").append(k).append('=').append(v));
		return sb.toString();
	}

	static Map<String, String> scopeOf(String context) {
		if (context == null || context.isBlank()) {
			return null;
		}
		Map<String, String> scope = new LinkedHashMap<>();
		for (String part : context.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				scope.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip());
			} else if (!part.isBlank()) {
				// a context written by hand without keys: keep it, it never matches a game context
				scope.put("context", part.strip());
			}
		}
		return scope.isEmpty() ? null : scope;
	}

	// ---- PO → ours ----

	public static StringsFile fromPo(PoFile po) {
		StringsFile file = new StringsFile();
		String lang = po.header.get("Language");
		file.lang = lang == null || lang.isBlank() ? null : lang.replace('-', '_').toLowerCase(Locale.ROOT);
		file.source = po.header.getOrDefault("X-CakeText-Source", "en_us");
		file.server = po.header.get("X-CakeText-Server");
		for (PoFile.Entry p : po.entries) {
			StringEntry e = new StringEntry(p.id, p.flags.contains("fuzzy") ? "" : p.str);
			e.scope = scopeOf(p.context);
			List<Map<String, Object>> contexts = new ArrayList<>();
			for (String c : p.comments) {
				if (c.startsWith(NOTE)) {
					e.note = c.substring(NOTE.length());
				} else if (c.startsWith(ENGINE)) {
					e.engine = c.substring(ENGINE.length()).strip();
				} else if (c.startsWith(WHERE)) {
					contexts.add(parseWhere(c.substring(WHERE.length())));
				} else if (c.startsWith(SEEN)) {
					Matcher m = SEEN_LINE.matcher(c);
					if (m.lookingAt()) {
						e.count = Long.parseLong(m.group(1));
						e.first = m.group(2);
						e.last = m.group(3);
					}
				}
			}
			e.contexts = contexts.isEmpty() ? null : contexts;
			file.entries.add(e);
		}
		return file;
	}

	private static Map<String, Object> parseWhere(String line) {
		String[] parts = line.split("\\|", -1);
		Map<String, Object> c = new LinkedHashMap<>();
		for (int i = 0; i < WHERE_KEYS.length && i < parts.length; i++) {
			String v = parts[i].strip();
			if (!v.equals("-") && !v.isEmpty()) {
				c.put(WHERE_KEYS[i], v);
			}
		}
		if (parts.length > WHERE_KEYS.length) {
			String[] xyz = parts[WHERE_KEYS.length].strip().split("\\s+");
			if (xyz.length == 3) {
				try {
					c.put("pos", List.of(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
				} catch (NumberFormatException ignored) {
					// "-": no position
				}
			}
		}
		return c;
	}

	// ---- Crowdin layout ----

	/** The surface a string belongs to: its scope's, else where it was first seen, else "misc". */
	static String surfaceOf(StringEntry e) {
		if (e.scope != null && e.scope.get("surface") != null) {
			return e.scope.get("surface");
		}
		if (e.contexts != null && !e.contexts.isEmpty() && e.contexts.get(0).get("surface") != null) {
			return String.valueOf(e.contexts.get(0).get("surface"));
		}
		return "misc";
	}

	/** {@code <dir>/en_us/<surface>.pot} for every surface, plus {@code <dir>/crowdin.yml} if missing. Returns the files. */
	public static List<Path> writeCrowdin(StringsFile file, Path dir, boolean positions) throws IOException {
		Map<String, List<StringEntry>> bySurface = new TreeMap<>();
		for (StringEntry e : file.entries) {
			bySurface.computeIfAbsent(safeName(surfaceOf(e)), k -> new ArrayList<>()).add(e);
		}
		List<Path> written = new ArrayList<>();
		String source = file.source == null ? "en_us" : file.source;
		for (Map.Entry<String, List<StringEntry>> s : bySurface.entrySet()) {
			Path out = dir.resolve(source).resolve(s.getKey() + ".pot");
			toPo(file, s.getValue(), true, positions).write(out);
			written.add(out);
		}
		Path yml = dir.resolve("crowdin.yml");
		if (!Files.exists(yml)) {
			StringsFile.writeAtomically(yml, crowdinYml(source).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			written.add(yml);
		}
		return written;
	}

	static String crowdinYml(String source) {
		return """
				# Crowdin configuration for CakeText strings (written by CakeText Collector; edit freely, it's not overwritten).
				# Upload sources:      crowdin upload sources
				# Download translations: crowdin download
				# Then copy translations/<lang>/ into .minecraft/config/caketext/translations/<lang>/.
				"project_id_env": "CROWDIN_PROJECT_ID"
				"api_token_env": "CROWDIN_PERSONAL_TOKEN"
				"preserve_hierarchy": true
				files:
				  - source: /%s/*.pot
				    translation: /translations/%%locale_with_underscore%%/%%file_name%%.po
				    languages_mapping:
				      locale_with_underscore:
				        zh-CN: zh_cn
				        zh-TW: zh_tw
				""".formatted(source);
	}

	private static String safeName(String s) {
		return s.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	// ---- the two-way converter ----

	/**
	 * Converts each file (or every file in each folder) into {@code outDir}: ours → {@code <name>/<surface>.po}
	 * ({@code .pot} when nothing is translated), PO → {@code <name>.json}. Returns what was written.
	 */
	public static List<Path> convert(List<Path> inputs, Path outDir) throws IOException {
		List<Path> files = new ArrayList<>();
		for (Path in : inputs) {
			if (Files.isDirectory(in)) {
				try (Stream<Path> list = Files.list(in)) {
					list.filter(Files::isRegularFile).sorted().forEach(files::add);
				}
			} else {
				files.add(in);
			}
		}
		List<Path> written = new ArrayList<>();
		for (Path f : files) {
			String name = f.getFileName().toString();
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.endsWith(".po") || lower.endsWith(".pot")) {
				String base = name.substring(0, name.lastIndexOf('.'));
				Path out = outDir.resolve(base + ".json");
				fromPo(PoFile.read(f)).write(out, false);
				written.add(out);
			} else if (lower.endsWith(".json") || lower.endsWith(".json.gz")) {
				String base = lower.endsWith(".json.gz") ? name.substring(0, name.length() - 8) : name.substring(0, name.length() - 5);
				StringsFile file = StringsFile.read(f);
				boolean translated = file.entries.stream().anyMatch(StringEntry::translated);
				Map<String, List<StringEntry>> bySurface = new TreeMap<>();
				for (StringEntry e : file.entries) {
					bySurface.computeIfAbsent(safeName(surfaceOf(e)), k -> new ArrayList<>()).add(e);
				}
				for (Map.Entry<String, List<StringEntry>> s : bySurface.entrySet()) {
					Path out = outDir.resolve(base).resolve(s.getKey() + (translated ? ".po" : ".pot"));
					toPo(file, s.getValue(), !translated, true).write(out);
					written.add(out);
				}
			}
		}
		return written;
	}

	/** {@code Exchanger <files or folders…> [--out <folder>]} */
	public static void main(String[] args) throws IOException {
		List<Path> inputs = new ArrayList<>();
		Path out = Path.of("converted");
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("--out") && i + 1 < args.length) {
				out = Path.of(args[++i]);
			} else {
				inputs.add(Path.of(args[i]));
			}
		}
		if (inputs.isEmpty()) {
			System.err.println("usage: Exchanger <file.json|file.json.gz|file.po|file.pot|folder>... [--out <folder>]");
			System.exit(2);
		}
		for (Path p : convert(inputs, out)) {
			System.out.println(p);
		}
	}
}
