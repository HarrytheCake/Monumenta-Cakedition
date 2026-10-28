package io.github.harrythecake.cakeupdater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The changelog the pack ships: config/cakeupdater/changelog/&lt;version&gt;/&lt;lang&gt;.md, one folder per pack version.
 * Two kinds of line mean something: headings ("**Added**" or "## Added") and items ("- ..."); any other text is kept
 * as a plain line. Plain Java (no Minecraft classes), so it can be tested on its own.
 */
final class Changelog {
	enum Kind { HEADING, ITEM, TEXT }

	record Line(Kind kind, String text) {}

	record Entry(String version, List<Line> lines) {
		List<String> items() {
			return lines.stream().filter(l -> l.kind() == Kind.ITEM).map(Line::text).toList();
		}
	}

	private static final Pattern BOLD_LINE = Pattern.compile("^\\*\\*(.+?)\\*\\*:?$");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private Changelog() {}

	static Path dir(Path gameDir) {
		return gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve("changelog");
	}

	/**
	 * The versions after {@code from} up to and including {@code to}, newest first: everything a player who skipped
	 * versions has missed. An unknown {@code from} gives only {@code to}; {@code to == null} gives every version.
	 * Versions without a file in any fallback language are left out.
	 */
	static List<Entry> between(Path gameDir, String from, String to, String language) {
		List<Entry> entries = new ArrayList<>();
		if (to != null && !known(to)) {
			return entries;
		}
		for (String version : versions(gameDir)) {
			boolean upTo = to == null || compare(version, to) <= 0;
			boolean after = to == null || (known(from) ? compare(version, from) > 0 : compare(version, to) == 0);
			if (upTo && after) {
				Entry entry = read(gameDir, version, language);
				if (entry != null) {
					entries.add(entry);
				}
			}
		}
		return entries;
	}

	/** Every version folder, newest first. */
	static List<String> versions(Path gameDir) {
		Path dir = dir(gameDir);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> dirs = Files.list(dir)) {
			return dirs.filter(Files::isDirectory).map(d -> d.getFileName().toString())
					.sorted((a, b) -> compare(b, a)).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	static Entry read(Path gameDir, String version, String language) {
		for (String lang : fallback(language)) {
			Path file = dir(gameDir).resolve(version).resolve(lang + ".md");
			if (Files.isRegularFile(file)) {
				try {
					return new Entry(version, parse(Files.readString(file, StandardCharsets.UTF_8)));
				} catch (IOException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** The game language first; Traditional Chinese falls back to Simplified, and everything ends in English. */
	static List<String> fallback(String language) {
		String lang = language == null ? "en_us" : language.toLowerCase(Locale.ROOT);
		Set<String> order = new LinkedHashSet<>();
		order.add(lang);
		if (lang.equals("zh_hk")) {
			order.add("zh_tw");
		}
		if (lang.startsWith("zh_")) {
			order.add("zh_cn");
		}
		order.add("en_us");
		return List.copyOf(order);
	}

	static List<Line> parse(String markdown) {
		List<Line> lines = new ArrayList<>();
		for (String raw : markdown.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || line.matches("-{3,}|\\*{3,}")) {
				continue;
			}
			Matcher bold = BOLD_LINE.matcher(line);
			if (line.startsWith("#")) {
				lines.add(new Line(Kind.HEADING, clean(line.replaceFirst("^#+\\s*", ""))));
			} else if (bold.matches()) {
				lines.add(new Line(Kind.HEADING, clean(bold.group(1))));
			} else if (line.startsWith("- ") || line.startsWith("* ")) {
				lines.add(new Line(Kind.ITEM, clean(line.substring(2))));
			} else {
				lines.add(new Line(Kind.TEXT, clean(line)));
			}
		}
		return lines;
	}

	/** Markdown that chat can't show: links become their text, emphasis and code marks are dropped. */
	static String clean(String text) {
		return LINK.matcher(text).replaceAll("$1").replace("**", "").replace("__", "").replace("`", "").strip();
	}

	/** Orders pack versions by their numbers (2.10.0 after 2.9.1); a pre-release ("2.3.0-beta") comes before its release. */
	static int compare(String a, String b) {
		String[] pa = a.split("-", 2);
		String[] pb = b.split("-", 2);
		List<Integer> na = numbers(pa[0]);
		List<Integer> nb = numbers(pb[0]);
		for (int i = 0; i < Math.max(na.size(), nb.size()); i++) {
			int x = i < na.size() ? na.get(i) : 0;
			int y = i < nb.size() ? nb.get(i) : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		if (pa.length != pb.length) {
			return pa.length == 1 ? 1 : -1;
		}
		return pa.length == 1 ? 0 : pa[1].compareTo(pb[1]);
	}

	private static List<Integer> numbers(String s) {
		List<Integer> out = new ArrayList<>();
		Matcher m = NUMBER.matcher(s);
		while (m.find()) {
			out.add(Integer.parseInt(m.group()));
		}
		return out;
	}

	private static boolean known(String version) {
		return version != null && !version.isBlank() && !version.equals("?");
	}
}
