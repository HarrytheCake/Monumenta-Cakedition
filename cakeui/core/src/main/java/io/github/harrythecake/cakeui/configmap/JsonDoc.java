package io.github.harrythecake.cakeui.configmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON or JSON5 file with the character span of every value, so a value can be replaced in place. JSON5 extras
 * (comments, trailing commas, unquoted keys, single quotes, hex) are read in both, since mods' "JSON" often has them.
 */
final class JsonDoc extends ConfigDoc {
	private final boolean json5;
	private Map<List<String>, Val> values;
	private Map<List<String>, Obj> objects;

	/** One value: its characters [start, end) and what it reads as. */
	private record Val(int start, int end, Object value, boolean compound) {
	}

	/** One object: where it closes, its last member, how its members are written. */
	private static final class Obj {
		int open;
		int close;
		int lastEnd = -1;      // end of the last member's value
		String indent;         // whitespace before a member that starts its own line
		boolean quotedKeys = true;
		boolean any;
	}

	JsonDoc(String text, boolean json5) {
		super(text);
		this.json5 = json5;
		scan();
	}

	@Override
	public Object get(List<String> path) {
		Val v = values.get(path);
		return v == null ? null : v.value;
	}

	@Override
	public void set(List<String> path, Object value) {
		String literal = literal(value);
		Val v = values.get(path);
		if (v != null) {
			if (v.compound) {
				throw new ConfigDocException(lineOf(text, v.start), String.join(".", path) + " is a list or object, not a value");
			}
			splice(v.start, v.end, literal);
			scan();
			return;
		}
		// missing: add it to the deepest object that exists, with any missing objects in between
		int have = path.size() - 1;
		while (have > 0 && !objects.containsKey(path.subList(0, have))) {
			if (values.containsKey(path.subList(0, have))) {
				throw new ConfigDocException(0, String.join(".", path.subList(0, have)) + " is not an object");
			}
			have--;
		}
		Obj parent = objects.get(path.subList(0, have));
		if (parent == null) {
			throw new ConfigDocException(1, "the file is not a JSON object");
		}
		String inner = literal;
		for (int i = path.size() - 1; i > have; i--) {
			inner = "{" + key(path.get(i), parent) + ": " + inner + "}";
		}
		insert(parent, key(path.get(have), parent) + ": " + inner);
		scan();
	}

	private String literal(Object value) {
		if (value instanceof Boolean b) {
			return b.toString();
		}
		if (value instanceof Number) {
			return number(value);
		}
		if (value instanceof String s) {
			return quoted(s);
		}
		throw new IllegalArgumentException("can't write " + value);
	}

	private String key(String k, Obj parent) {
		if (!json5 || parent.quotedKeys || !k.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
			return quoted(k);
		}
		return k;
	}

	private void insert(Obj o, String member) {
		String unit = text.contains("\n\t") ? "\t" : "  ";
		if (!o.any) {
			String outer = lineIndent(o.open);
			splice(o.close, o.close, "\n" + outer + unit + member + "\n" + outer);
			return;
		}
		String indent = o.indent != null ? o.indent : lineIndent(o.open) + unit;
		int after = skip(o.lastEnd);
		if (after < text.length() && text.charAt(after) == ',') {
			// the file keeps a trailing comma after the last member: keep that style
			splice(after + 1, after + 1, "\n" + indent + member + ",");
		} else {
			splice(o.lastEnd, o.lastEnd, ",\n" + indent + member);
		}
	}

	/** The whitespace at the start of the line holding offset. */
	private String lineIndent(int offset) {
		int start = text.lastIndexOf('\n', offset - 1) + 1;
		int end = start;
		while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
			end++;
		}
		return text.substring(start, end);
	}

	// --- scanning --------------------------------------------------------------------------------------------

	private int pos;

	private void scan() {
		values = new HashMap<>();
		objects = new HashMap<>();
		pos = skip(0);
		value(List.of());
		pos = skip(pos);
		if (pos < text.length()) {
			throw err("unexpected '" + text.charAt(pos) + "' after the end of the document");
		}
	}

	private ConfigDocException err(String message) {
		return new ConfigDocException(lineOf(text, pos), message);
	}

	private void value(List<String> path) {
		if (pos >= text.length()) {
			throw err("unexpected end of file");
		}
		int start = pos;
		char c = text.charAt(pos);
		if (c == '{') {
			object(path);
			values.put(path, new Val(start, pos, OTHER, true));
			return;
		}
		if (c == '[') {
			array(path);
			values.put(path, new Val(start, pos, OTHER, true));
			return;
		}
		Object v;
		if (c == '"' || c == '\'') {
			v = string();
		} else if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) {
			v = number();
		} else {
			String word = word();
			v = switch (word) {
				case "true" -> Boolean.TRUE;
				case "false" -> Boolean.FALSE;
				case "null" -> OTHER;
				case "Infinity" -> Double.POSITIVE_INFINITY;
				case "NaN" -> Double.NaN;
				default -> throw err(word.isEmpty() ? "unexpected '" + c + "'" : "unexpected word '" + word + "'");
			};
		}
		values.put(path, new Val(start, pos, v, false));
	}

	private void object(List<String> path) {
		Obj o = new Obj();
		o.open = pos;
		objects.put(path, o);
		pos++;
		boolean first = true;
		while (true) {
			pos = skip(pos);
			if (pos >= text.length()) {
				throw err("unclosed '{'");
			}
			if (text.charAt(pos) == '}') {
				o.close = pos;
				pos++;
				return;
			}
			int keyStart = pos;
			char c = text.charAt(pos);
			String key;
			if (c == '"' || c == '\'') {
				key = string();
			} else {
				key = word();
				if (key.isEmpty()) {
					throw err("expected a key, found '" + c + "'");
				}
			}
			if (first) {
				o.quotedKeys = c == '"' || c == '\'';
				String before = text.substring(text.lastIndexOf('\n', keyStart - 1) + 1, keyStart);
				if (before.isBlank() && text.lastIndexOf('\n', keyStart - 1) >= 0) {
					o.indent = before;
				}
				first = false;
			}
			pos = skip(pos);
			if (pos >= text.length() || text.charAt(pos) != ':') {
				throw err("expected ':' after key '" + key + "'");
			}
			pos = skip(pos + 1);
			List<String> child = new ArrayList<>(path);
			child.add(key);
			value(List.copyOf(child));
			o.any = true;
			o.lastEnd = pos;
			pos = skip(pos);
			if (pos < text.length() && text.charAt(pos) == ',') {
				pos++;
			} else if (pos < text.length() && text.charAt(pos) != '}') {
				throw err("expected ',' or '}' after the value of '" + key + "'");
			}
		}
	}

	private void array(List<String> path) {
		pos++;
		int i = 0;
		while (true) {
			pos = skip(pos);
			if (pos >= text.length()) {
				throw err("unclosed '['");
			}
			if (text.charAt(pos) == ']') {
				pos++;
				return;
			}
			List<String> child = new ArrayList<>(path);
			child.add("[" + i++ + "]");  // lists are read past, never edited
			value(List.copyOf(child));
			pos = skip(pos);
			if (pos < text.length() && text.charAt(pos) == ',') {
				pos++;
			} else if (pos < text.length() && text.charAt(pos) != ']') {
				throw err("expected ',' or ']' in a list");
			}
		}
	}

	private String string() {
		char quote = text.charAt(pos++);
		StringBuilder sb = new StringBuilder();
		while (true) {
			if (pos >= text.length() || text.charAt(pos) == '\n') {
				throw err("unclosed text");
			}
			char c = text.charAt(pos++);
			if (c == quote) {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			if (pos >= text.length()) {
				throw err("unclosed text");
			}
			char e = text.charAt(pos++);
			switch (e) {
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'r' -> sb.append('\r');
				case 'b' -> sb.append('\b');
				case 'f' -> sb.append('\f');
				case '0' -> sb.append('\0');
				case 'u' -> {
					try {
						sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
					} catch (RuntimeException ex) {
						throw err("bad \\u escape");
					}
					pos += 4;
				}
				case '\r' -> {
					if (pos < text.length() && text.charAt(pos) == '\n') {
						pos++;
					}
				}
				case '\n' -> {
				}
				default -> sb.append(e);
			}
		}
	}

	private Double number() {
		int start = pos;
		if (text.charAt(pos) == '+' || text.charAt(pos) == '-') {
			pos++;
		}
		boolean negative = text.charAt(start) == '-';
		if (text.startsWith("Infinity", pos)) {
			pos += 8;
			return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		}
		if (text.startsWith("NaN", pos)) {
			pos += 3;
			return Double.NaN;
		}
		if (text.startsWith("0x", pos) || text.startsWith("0X", pos)) {
			pos += 2;
			int digits = pos;
			while (pos < text.length() && Character.digit(text.charAt(pos), 16) >= 0) {
				pos++;
			}
			try {
				double v = Long.parseLong(text.substring(digits, pos), 16);
				return negative ? -v : v;
			} catch (NumberFormatException e) {
				throw err("bad hex number");
			}
		}
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E'
					|| ((c == '+' || c == '-') && (text.charAt(pos - 1) == 'e' || text.charAt(pos - 1) == 'E'))) {
				pos++;
			} else {
				break;
			}
		}
		try {
			return Double.parseDouble(text.substring(start, pos));
		} catch (NumberFormatException e) {
			throw err("bad number '" + text.substring(start, pos) + "'");
		}
	}

	private String word() {
		int start = pos;
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-' || c == '.') {
				pos++;
			} else {
				break;
			}
		}
		return text.substring(start, pos);
	}

	/** The first offset from p that isn't whitespace or a comment. */
	private int skip(int p) {
		while (p < text.length()) {
			char c = text.charAt(p);
			if (Character.isWhitespace(c) || c == '﻿') {
				p++;
			} else if (c == '/' && p + 1 < text.length() && text.charAt(p + 1) == '/') {
				while (p < text.length() && text.charAt(p) != '\n') {
					p++;
				}
			} else if (c == '/' && p + 1 < text.length() && text.charAt(p + 1) == '*') {
				int end = text.indexOf("*/", p + 2);
				if (end < 0) {
					throw new ConfigDocException(lineOf(text, p), "unclosed /* comment");
				}
				p = end + 2;
			} else {
				break;
			}
		}
		return p;
	}
}
