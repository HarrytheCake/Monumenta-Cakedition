package io.github.harrythecake.cakeconfighub.configmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A TOML file (Forge-style configs: {@code [client.options]} tables, indented keys, # comments) with the span of each
 * plain value. Arrays, inline tables, multi-line strings and dates are read past and never edited; keys inside
 * {@code [[array tables]]} aren't reachable.
 */
final class TomlDoc extends ConfigDoc {
	private Map<List<String>, Val> values;
	private Map<List<String>, Table> tables;

	private record Val(int start, int end, Object value) {
	}

	/** Where a table's lines end, and how its keys are indented. */
	private static final class Table {
		int insertAt;          // end of the header line, then of each key line
		String indent = "";
	}

	TomlDoc(String text) {
		super(text);
		scan();
	}

	@Override
	public Object get(List<String> path) {
		Val v = values.get(path);
		return v == null ? null : v.value;
	}

	@Override
	public void set(List<String> path, Object value) {
		String literal;
		if (value instanceof Boolean b) {
			literal = b.toString();
		} else if (value instanceof Number) {
			literal = number(value);
		} else if (value instanceof String s) {
			literal = quoted(s);
		} else {
			throw new IllegalArgumentException("can't write " + value);
		}
		Val v = values.get(path);
		if (v != null) {
			if (v.value == OTHER) {
				throw new ConfigDocException(lineOf(text, v.start), String.join(".", path) + " is not a plain value");
			}
			splice(v.start, v.end, literal);
		} else {
			String nl = text.contains("\r\n") ? "\r\n" : "\n";
			List<String> tablePath = path.subList(0, path.size() - 1);
			String line = key(path.get(path.size() - 1)) + " = " + literal;
			Table t = tables.get(tablePath);
			if (t != null) {
				splice(t.insertAt, t.insertAt, (t.insertAt == 0 ? "" : nl) + t.indent + line + (t.insertAt == 0 ? nl : ""));
			} else {
				StringBuilder header = new StringBuilder();
				for (String k : tablePath) {
					header.append(header.length() == 0 ? "" : ".").append(key(k));
				}
				if (!text.isEmpty() && !text.endsWith("\n")) {
					text += nl;
				}
				text += nl + "[" + header + "]" + nl + line + nl;
			}
		}
		scan();
	}

	private static String key(String k) {
		return k.matches("[A-Za-z0-9_-]+") ? k : quoted(k);
	}

	// --- scanning --------------------------------------------------------------------------------------------

	private int pos;

	private void scan() {
		values = new HashMap<>();
		tables = new HashMap<>();
		Table root = new Table();
		tables.put(List.of(), root);
		List<String> current = List.of();
		boolean inArrayTable = false;
		pos = 0;
		while (pos < text.length()) {
			int lineStart = pos;
			skipBlank();
			if (pos >= text.length()) {
				break;
			}
			char c = text.charAt(pos);
			if (c == '\n' || c == '\r' || c == '#') {
				toLineEnd();
				continue;
			}
			if (c == '[') {
				boolean array = text.startsWith("[[", pos);
				pos += array ? 2 : 1;
				skipBlank();
				List<String> keys = keys(array ? "]]" : "]");
				pos += array ? 2 : 1;
				inArrayTable = array;
				current = keys;
				toLineEnd();
				if (!array) {
					Table t = tables.computeIfAbsent(keys, k -> new Table());
					t.insertAt = lineEnd(lineStart);
				}
				continue;
			}
			String indent = text.substring(lineStart, pos);
			List<String> keys = keys("=");
			pos++; // =
			skipBlank();
			int start = pos;
			Object v = value();
			int end = pos;
			toLineEnd();
			if (!inArrayTable) {
				List<String> full = new ArrayList<>(current);
				full.addAll(keys);
				values.put(List.copyOf(full), new Val(start, end, v));
				Table t = tables.get(current);
				t.insertAt = lineEnd(end);
				t.indent = indent;
			}
		}
	}

	private ConfigDocException err(String message) {
		return new ConfigDocException(lineOf(text, pos), message);
	}

	/** Dotted keys up to (not past) the terminator. */
	private List<String> keys(String terminator) {
		List<String> out = new ArrayList<>();
		while (true) {
			skipBlank();
			if (pos >= text.length()) {
				throw err("unexpected end of file in a key");
			}
			char c = text.charAt(pos);
			if (c == '"') {
				out.add(basicString());
			} else if (c == '\'') {
				int end = text.indexOf('\'', pos + 1);
				if (end < 0) {
					throw err("unclosed key");
				}
				out.add(text.substring(pos + 1, end));
				pos = end + 1;
			} else {
				int start = pos;
				while (pos < text.length() && (Character.isLetterOrDigit(text.charAt(pos)) || text.charAt(pos) == '_' || text.charAt(pos) == '-')) {
					pos++;
				}
				if (start == pos) {
					throw err("expected a key, found '" + c + "'");
				}
				out.add(text.substring(start, pos));
			}
			skipBlank();
			if (text.startsWith(terminator, pos)) {
				return out;
			}
			if (pos < text.length() && text.charAt(pos) == '.') {
				pos++;
				continue;
			}
			throw err("expected '" + terminator + "' after a key");
		}
	}

	private Object value() {
		if (pos >= text.length()) {
			throw err("missing value");
		}
		char c = text.charAt(pos);
		if (text.startsWith("\"\"\"", pos) || text.startsWith("'''", pos)) {
			String q = text.substring(pos, pos + 3);
			int end = text.indexOf(q, pos + 3);
			if (end < 0) {
				throw err("unclosed multi-line text");
			}
			pos = end + 3;
			while (pos < text.length() && text.charAt(pos) == q.charAt(0)) {
				pos++;  // up to two quotes may end the content
			}
			return OTHER;
		}
		if (c == '"') {
			return basicString();
		}
		if (c == '\'') {
			int end = text.indexOf('\'', pos + 1);
			int nl = text.indexOf('\n', pos + 1);
			if (end < 0 || (nl >= 0 && nl < end)) {
				throw err("unclosed text");
			}
			String s = text.substring(pos + 1, end);
			pos = end + 1;
			return s;
		}
		if (c == '[' || c == '{') {
			bracketed();
			return OTHER;
		}
		int start = pos;
		while (pos < text.length() && " \t\r\n#,]}".indexOf(text.charAt(pos)) < 0) {
			pos++;
		}
		String token = text.substring(start, pos);
		if (token.equals("true")) {
			return Boolean.TRUE;
		}
		if (token.equals("false")) {
			return Boolean.FALSE;
		}
		String n = token.replace("_", "");
		try {
			if (n.startsWith("0x") || n.startsWith("0o") || n.startsWith("0b")) {
				return (double) Long.parseLong(n.substring(2), n.charAt(1) == 'x' ? 16 : n.charAt(1) == 'o' ? 8 : 2);
			}
			return switch (n) {
				case "inf", "+inf" -> Double.POSITIVE_INFINITY;
				case "-inf" -> Double.NEGATIVE_INFINITY;
				case "nan", "+nan", "-nan" -> Double.NaN;
				default -> Double.parseDouble(n);
			};
		} catch (NumberFormatException e) {
			return OTHER;  // a date or time
		}
	}

	/** Skips a [...] or {...} value, which may run over several lines and hold strings and comments. */
	private void bracketed() {
		int depth = 0;
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (c == '"' || c == '\'') {
				if (text.startsWith("\"\"\"", pos) || text.startsWith("'''", pos)) {
					value();
					continue;
				}
				if (c == '"') {
					basicString();
				} else {
					value();
				}
				continue;
			}
			if (c == '#') {
				toLineEndKeep();
				continue;
			}
			if (c == '[' || c == '{') {
				depth++;
			} else if (c == ']' || c == '}') {
				depth--;
				if (depth == 0) {
					pos++;
					return;
				}
			}
			pos++;
		}
		throw err("unclosed list or table");
	}

	private String basicString() {
		pos++;
		StringBuilder sb = new StringBuilder();
		while (true) {
			if (pos >= text.length() || text.charAt(pos) == '\n') {
				throw err("unclosed text");
			}
			char c = text.charAt(pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = text.charAt(pos++);
			switch (e) {
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'r' -> sb.append('\r');
				case 'b' -> sb.append('\b');
				case 'f' -> sb.append('\f');
				case 'u', 'U' -> {
					int len = e == 'u' ? 4 : 8;
					try {
						sb.appendCodePoint(Integer.parseInt(text.substring(pos, pos + len), 16));
					} catch (RuntimeException ex) {
						throw err("bad \\" + e + " escape");
					}
					pos += len;
				}
				default -> sb.append(e);
			}
		}
	}

	private void skipBlank() {
		while (pos < text.length() && (text.charAt(pos) == ' ' || text.charAt(pos) == '\t')) {
			pos++;
		}
	}

	/** Past the rest of the line (a trailing comment) and its line break. */
	private void toLineEnd() {
		toLineEndKeep();
		if (pos < text.length()) {
			pos++;
		}
	}

	private void toLineEndKeep() {
		while (pos < text.length() && text.charAt(pos) != '\n') {
			pos++;
		}
	}

	/** The offset of the line break (or file end) at or after p, before any '\r'. */
	private int lineEnd(int p) {
		int nl = text.indexOf('\n', p);
		if (nl < 0) {
			return text.length();
		}
		return nl > 0 && text.charAt(nl - 1) == '\r' ? nl - 1 : nl;
	}
}
