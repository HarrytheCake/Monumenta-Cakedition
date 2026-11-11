package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * The Markdown subset book pages and notes are written in: front matter, headings, paragraphs, lists, quotes, code
 * blocks, rules, images on their own line, simple tables, and inline bold/italic/strikethrough/code, links and
 * {@code {key:<id>}} (the player's current key for that binding). Plain Java (no Minecraft classes), so it can be
 * tested on its own; {@link PageLayout} turns the result into game text.
 */
final class Markdown {
	enum Kind { HEADING, PARAGRAPH, ITEM, QUOTE, CODE, RULE, IMAGE }

	static final int BOLD = 1;
	static final int ITALIC = 2;
	static final int CODE = 4;
	static final int STRIKE = 8;

	/** A run of text in one style. {@code link} is the raw link target; {@code key} a key binding id (text is empty). */
	record Span(String text, int style, @Nullable String link, @Nullable String key) {
		Span(String text, int style) {
			this(text, style, null, null);
		}
	}

	/**
	 * One block. HEADING: {@code level} 1–6. ITEM: {@code level} = nesting depth, {@code marker} "•" or "3.".
	 * IMAGE: {@code src}, the alt text as the only span, {@code width} in GUI pixels (0 = natural size).
	 */
	record Block(Kind kind, int level, @Nullable String marker, List<Span> spans, @Nullable String src, int width) {
		Block(Kind kind, int level, List<Span> spans) {
			this(kind, level, null, spans, null, 0);
		}

		String plainText() {
			StringBuilder text = new StringBuilder();
			for (Span span : spans) {
				text.append(span.text());
			}
			return text.toString();
		}
	}

	record Doc(Map<String, String> meta, List<Block> blocks) {
		/** Front matter {@code title:}, else the first level-1 heading, else null. */
		@Nullable String title() {
			String title = meta.get("title");
			if (title != null && !title.isBlank()) {
				return title.strip();
			}
			for (Block block : blocks) {
				if (block.kind() == Kind.HEADING && block.level() == 1) {
					return block.plainText().strip();
				}
			}
			return null;
		}

		/** Every block's text, one per line: what search looks through. */
		String plainText() {
			StringBuilder text = new StringBuilder();
			for (Block block : blocks) {
				if (block.kind() != Kind.RULE) {
					text.append(block.plainText()).append('\n');
				}
			}
			return text.toString();
		}

		int order() {
			try {
				return Integer.parseInt(meta.getOrDefault("order", "").strip());
			} catch (NumberFormatException e) {
				return DEFAULT_ORDER;
			}
		}

		boolean hidden() {
			return "true".equalsIgnoreCase(meta.getOrDefault("hidden", "").strip());
		}
	}

	static final int DEFAULT_ORDER = 1000;

	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$");
	private static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
	private static final Pattern SETEXT = Pattern.compile("^\\s{0,3}(=+|-+)\\s*$");
	private static final Pattern ITEM = Pattern.compile("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$");
	private static final Pattern IMAGE = Pattern.compile("^\\s*!\\[([^\\]]*)]\\(([^)\\s]+)\\)\\s*$");
	private static final Pattern IMAGE_WIDTH = Pattern.compile("^(.*)\\|\\s*(\\d{1,4})\\s*$");
	private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$");
	private static final Pattern KEY = Pattern.compile("\\{key:([A-Za-z0-9_.\\-]+)}");
	private static final Pattern URL = Pattern.compile("https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"]");

	private Markdown() {}

	static Doc parse(String source) {
		List<String> lines = new ArrayList<>(List.of(source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
		Map<String, String> meta = frontMatter(lines);
		List<Block> blocks = new ArrayList<>();
		StringBuilder paragraph = new StringBuilder();
		Kind paragraphKind = null;
		int itemLevel = 0;
		String itemMarker = null;

		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i);
			String trimmed = line.strip();

			if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				String fence = trimmed.substring(0, 3);
				StringBuilder code = new StringBuilder();
				i++;
				while (i < lines.size() && !lines.get(i).strip().startsWith(fence)) {
					if (!code.isEmpty()) {
						code.append('\n');
					}
					code.append(lines.get(i).replace("\t", "    "));
					i++;
				}
				blocks.add(new Block(Kind.CODE, 0, List.of(new Span(code.toString(), CODE))));
				i++;
				continue;
			}

			if (trimmed.isEmpty()) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				i++;
				continue;
			}

			Matcher heading = HEADING.matcher(trimmed);
			Matcher item = ITEM.matcher(line);
			Matcher image = IMAGE.matcher(line);
			if (heading.matches()) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				blocks.add(new Block(Kind.HEADING, heading.group(1).length(), inline(heading.group(2))));
			} else if (paragraphKind == Kind.PARAGRAPH && SETEXT.matcher(line).matches()) {
				// "Title" underlined with === or --- is a heading
				String text = paragraph.toString().strip();
				paragraph.setLength(0);
				paragraphKind = null;
				blocks.add(new Block(Kind.HEADING, trimmed.charAt(0) == '=' ? 1 : 2, inline(text)));
			} else if (RULE.matcher(line).matches()) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				blocks.add(new Block(Kind.RULE, 0, List.of()));
			} else if (image.matches()) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				String alt = image.group(1);
				int width = 0;
				Matcher sized = IMAGE_WIDTH.matcher(alt);
				if (sized.matches()) {
					alt = sized.group(1).strip();
					width = Integer.parseInt(sized.group(2));
				}
				blocks.add(new Block(Kind.IMAGE, 0, null, List.of(new Span(alt, 0)), image.group(2), width));
			} else if (item.matches()) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = Kind.ITEM;
				itemLevel = Math.min(indent(item.group(1)) / 2, 4);
				itemMarker = item.group(3) != null ? item.group(3) + "." : "•";
				paragraph.append(item.group(4).strip());
			} else if (trimmed.startsWith(">")) {
				String text = trimmed.substring(1).strip();
				if (paragraphKind != Kind.QUOTE) {
					flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
					paragraphKind = Kind.QUOTE;
				}
				if (text.isEmpty()) {
					paragraph.append('\n');
				} else {
					join(paragraph, text);
				}
			} else if (trimmed.startsWith("|")) {
				flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
				paragraphKind = null;
				if (!TABLE_DIVIDER.matcher(trimmed).matches()) {
					blocks.add(new Block(Kind.PARAGRAPH, 0, inline(tableRow(trimmed))));
				}
			} else {
				// a lazy continuation keeps the list item or quote going
				if (paragraphKind == null) {
					paragraphKind = Kind.PARAGRAPH;
				}
				join(paragraph, trimmed);
			}
			if (endsWithHardBreak(line) && paragraphKind != null && paragraphKind != Kind.QUOTE) {
				paragraph.append('\n');
			}
			i++;
		}
		flush(blocks, paragraph, paragraphKind, itemLevel, itemMarker);
		return new Doc(meta, blocks);
	}

	/** Removes a leading {@code ---} block from {@code lines} and returns its {@code key: value} pairs. */
	private static Map<String, String> frontMatter(List<String> lines) {
		Map<String, String> meta = new LinkedHashMap<>();
		if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
			return meta;
		}
		for (int end = 1; end < lines.size(); end++) {
			if (lines.get(end).strip().equals("---")) {
				for (String entry : lines.subList(1, end)) {
					int colon = entry.indexOf(':');
					if (colon > 0) {
						String value = entry.substring(colon + 1).strip();
						if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
								|| value.startsWith("'") && value.endsWith("'"))) {
							value = value.substring(1, value.length() - 1);
						}
						meta.put(entry.substring(0, colon).strip().toLowerCase(Locale.ROOT), value);
					}
				}
				lines.subList(0, end + 1).clear();
				return meta;
			}
		}
		return meta;
	}

	private static void flush(List<Block> blocks, StringBuilder paragraph, @Nullable Kind kind, int level, String marker) {
		String text = paragraph.toString().strip();
		paragraph.setLength(0);
		if (kind == null || text.isEmpty()) {
			return;
		}
		switch (kind) {
			case ITEM -> blocks.add(new Block(Kind.ITEM, level, marker, inline(text), null, 0));
			default -> blocks.add(new Block(kind, 0, inline(text)));
		}
	}

	/** Joins a source line onto a paragraph: with a space, except between CJK characters, which use none. */
	private static void join(StringBuilder paragraph, String text) {
		if (!paragraph.isEmpty()) {
			char last = paragraph.charAt(paragraph.length() - 1);
			if (last != '\n' && !(isCjk(last) && isCjk(text.charAt(0)))) {
				paragraph.append(' ');
			}
		}
		paragraph.append(text);
	}

	static boolean isCjk(char c) {
		return c >= '⺀' && c <= '鿿' || c >= '豈' && c <= '﫿' || c >= '＀' && c <= '￯';
	}

	private static boolean endsWithHardBreak(String line) {
		return line.endsWith("  ") || line.endsWith("\\") && !line.endsWith("\\\\");
	}

	private static int indent(String whitespace) {
		int columns = 0;
		for (char c : whitespace.toCharArray()) {
			columns += c == '\t' ? 4 : 1;
		}
		return columns;
	}

	private static String tableRow(String row) {
		String cells = row.strip();
		if (cells.startsWith("|")) {
			cells = cells.substring(1);
		}
		if (cells.endsWith("|") && !cells.endsWith("\\|")) {
			cells = cells.substring(0, cells.length() - 1);
		}
		List<String> parts = new ArrayList<>();
		for (String cell : cells.split("(?<!\\\\)\\|")) {
			parts.add(cell.strip());
		}
		return String.join("  ·  ", parts);
	}

	/** Inline formatting of one block's text. */
	static List<Span> inline(String text) {
		List<Span> spans = new ArrayList<>();
		inline(text, 0, null, spans);
		return merge(spans);
	}

	private static void inline(String text, int baseStyle, @Nullable String link, List<Span> out) {
		StringBuilder run = new StringBuilder();
		int style = baseStyle;
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);

			if (c == '\\' && i + 1 < text.length() && isEscapable(text.charAt(i + 1))) {
				run.append(text.charAt(i + 1));
				i += 2;
				continue;
			}
			if (text.startsWith("``", i)) {
				// a double-backtick code span can hold single backticks: `` `code` ``
				int close = text.indexOf("``", i + 2);
				if (close > i) {
					emit(out, run, style, link);
					String code = text.substring(i + 2, close);
					if (code.length() >= 2 && code.startsWith(" ") && code.endsWith(" ")) {
						code = code.substring(1, code.length() - 1);
					}
					out.add(new Span(code, style | CODE, link, null));
					i = close + 2;
					continue;
				}
			}
			if (c == '`') {
				int close = text.indexOf('`', i + 1);
				if (close > i) {
					emit(out, run, style, link);
					out.add(new Span(text.substring(i + 1, close), style | CODE, link, null));
					i = close + 1;
					continue;
				}
			}
			if (c == '{' && text.startsWith("{key:", i)) {
				Matcher key = KEY.matcher(text).region(i, text.length());
				if (key.lookingAt()) {
					emit(out, run, style, link);
					out.add(new Span("", style, link, key.group(1)));
					i = key.end();
					continue;
				}
			}
			if (link == null && c == '[') {
				int close = closingBracket(text, i);
				if (close > 0 && close + 1 < text.length() && text.charAt(close + 1) == '(') {
					int end = text.indexOf(')', close + 2);
					if (end > 0) {
						emit(out, run, style, null);
						String target = text.substring(close + 2, end).strip();
						inline(text.substring(i + 1, close), style, target.isEmpty() ? "#" : target, out);
						i = end + 1;
						continue;
					}
				}
			}
			if (link == null && c == '<') {
				int end = text.indexOf('>', i);
				if (end > 0 && URL.matcher(text.substring(i + 1, end)).matches()) {
					emit(out, run, style, null);
					String url = text.substring(i + 1, end);
					out.add(new Span(url, style, url, null));
					i = end + 1;
					continue;
				}
			}
			if (link == null && (c == 'h') && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
				Matcher url = URL.matcher(text).region(i, text.length());
				if (url.lookingAt()) {
					emit(out, run, style, null);
					out.add(new Span(url.group(), style, url.group(), null));
					i = url.end();
					continue;
				}
			}
			if (text.startsWith("**", i) || text.startsWith("__", i)) {
				String delimiter = text.substring(i, i + 2);
				if ((style & BOLD) != 0 && (baseStyle & BOLD) == 0) {
					emit(out, run, style, link);
					style &= ~BOLD;
					i += 2;
					continue;
				}
				if ((style & BOLD) == 0 && opens(text, i + 2, delimiter)) {
					emit(out, run, style, link);
					style |= BOLD;
					i += 2;
					continue;
				}
			}
			if (text.startsWith("~~", i)) {
				if ((style & STRIKE) != 0 && (baseStyle & STRIKE) == 0) {
					emit(out, run, style, link);
					style &= ~STRIKE;
					i += 2;
					continue;
				}
				if ((style & STRIKE) == 0 && opens(text, i + 2, "~~")) {
					emit(out, run, style, link);
					style |= STRIKE;
					i += 2;
					continue;
				}
			}
			if (c == '*') {
				if ((style & ITALIC) != 0 && (baseStyle & ITALIC) == 0 && i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
					emit(out, run, style, link);
					style &= ~ITALIC;
					i++;
					continue;
				}
				if ((style & ITALIC) == 0 && opens(text, i + 1, "*")) {
					emit(out, run, style, link);
					style |= ITALIC;
					i++;
					continue;
				}
			}
			run.append(c);
			i++;
		}
		emit(out, run, style, link);
	}

	private static boolean isEscapable(char c) {
		return "\\`*_{}[]()#+-.!|~<>".indexOf(c) >= 0;
	}

	/** Whether a delimiter at {@code from} starts emphasis: not followed by a space, and closed later on. */
	private static boolean opens(String text, int from, String delimiter) {
		if (from >= text.length() || Character.isWhitespace(text.charAt(from))) {
			return false;
		}
		int close = text.indexOf(delimiter, from + 1);
		while (close > 0 && delimiter.equals("*") && (text.startsWith("**", close)
				&& !(close + 2 < text.length() && text.charAt(close + 2) == '*'))) {
			close = text.indexOf(delimiter, close + 2);
		}
		return close > from && !Character.isWhitespace(text.charAt(close - 1));
	}

	private static int closingBracket(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\') {
				i++;
			} else if (c == '[') {
				depth++;
			} else if (c == ']' && --depth == 0) {
				return i;
			}
		}
		return -1;
	}

	private static void emit(List<Span> out, StringBuilder run, int style, @Nullable String link) {
		if (!run.isEmpty()) {
			out.add(new Span(run.toString(), style, link, null));
			run.setLength(0);
		}
	}

	private static List<Span> merge(List<Span> spans) {
		List<Span> merged = new ArrayList<>();
		for (Span span : spans) {
			if (!merged.isEmpty()) {
				Span last = merged.get(merged.size() - 1);
				if (last.key() == null && span.key() == null && last.style() == span.style()
						&& Objects.equals(last.link(), span.link())) {
					merged.set(merged.size() - 1, new Span(last.text() + span.text(), span.style(), span.link(), null));
					continue;
				}
			}
			merged.add(span);
		}
		return merged;
	}
}
