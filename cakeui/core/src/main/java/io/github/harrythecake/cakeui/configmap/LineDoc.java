package io.github.harrythecake.cakeui.configmap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A file of one {@code key<sep>value} per line: vanilla's options.txt ({@code key:value}, the key ends at the first
 * ':') or Java .properties ({@code key=value} or {@code key: value}, '#' and '!' start comments). Values are the raw
 * text after the separator; keys are never nested (a path has one part).
 */
final class LineDoc extends ConfigDoc {
	private final boolean properties;
	private Map<String, int[]> spans;  // key -> [start, end) of the value

	LineDoc(String text, boolean properties) {
		super(text);
		this.properties = properties;
		scan();
	}

	@Override
	public Object get(List<String> path) {
		int[] s = spans.get(key(path));
		return s == null ? null : text.substring(s[0], s[1]);
	}

	@Override
	public void set(List<String> path, Object value) {
		String key = key(path);
		String raw = value instanceof Number ? number(value) : String.valueOf(value);
		if (raw.contains("\n") || raw.contains("\r")) {
			throw new ConfigDocException(0, "a value in " + (properties ? "a .properties file" : "options.txt") + " can't hold a line break");
		}
		int[] s = spans.get(key);
		if (s != null) {
			splice(s[0], s[1], raw);
		} else {
			String nl = text.contains("\r\n") ? "\r\n" : "\n";
			String sep = properties ? "=" : ":";
			if (!text.isEmpty() && !text.endsWith("\n")) {
				text += nl;
			}
			text += key + sep + raw + nl;
		}
		scan();
	}

	private static String key(List<String> path) {
		return String.join(".", path);  // options.txt keys like "key_key.attack" are one key with dots in it
	}

	private void scan() {
		spans = new LinkedHashMap<>();
		int i = 0;
		while (i < text.length()) {
			int end = text.indexOf('\n', i);
			if (end < 0) {
				end = text.length();
			}
			int lineEnd = end > i && text.charAt(end - 1) == '\r' ? end - 1 : end;
			line(i, lineEnd);
			i = end + 1;
		}
	}

	private void line(int start, int end) {
		int p = start;
		if (properties) {
			while (p < end && Character.isWhitespace(text.charAt(p))) {
				p++;
			}
			if (p >= end || text.charAt(p) == '#' || text.charAt(p) == '!') {
				return;
			}
			int k = p;
			while (k < end && text.charAt(k) != '=' && text.charAt(k) != ':' && !Character.isWhitespace(text.charAt(k))) {
				if (text.charAt(k) == '\\') {
					k++;
				}
				k++;
			}
			String key = text.substring(p, Math.min(k, end));
			int v = k;
			while (v < end && Character.isWhitespace(text.charAt(v))) {
				v++;
			}
			if (v < end && (text.charAt(v) == '=' || text.charAt(v) == ':')) {
				v++;
			}
			while (v < end && Character.isWhitespace(text.charAt(v))) {
				v++;
			}
			spans.putIfAbsent(key, new int[] {v, end});
		} else {
			int colon = text.indexOf(':', p);
			if (colon < 0 || colon >= end) {
				return;
			}
			spans.putIfAbsent(text.substring(p, colon), new int[] {colon + 1, end});
		}
	}
}
