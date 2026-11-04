package io.github.harrythecake.cakeui.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON5 reader that keeps the line of every value, so layout problems can name file and line.
 * Supports comments, trailing commas, unquoted keys, single-quoted strings, hex numbers,
 * leading/trailing dots, + signs, Infinity and NaN.
 */
public final class Json5 {
	private final String text;
	private final String file;
	private int pos;
	private int line = 1;

	private Json5(String text, String file) {
		this.text = text;
		this.file = file;
	}

	/** Parses a whole document; throws {@link Json5Exception} with the line on bad input. */
	public static JValue parse(String text, String file) {
		Json5 p = new Json5(text.startsWith("﻿") ? text.substring(1) : text, file);
		p.skipSpace();
		JValue value = p.value();
		p.skipSpace();
		if (p.pos < p.text.length()) {
			throw p.error("unexpected '" + p.text.charAt(p.pos) + "' after the end of the document");
		}
		return value;
	}

	private Json5Exception error(String message) {
		return new Json5Exception(file, line, message);
	}

	private JValue value() {
		if (pos >= text.length()) {
			throw error("unexpected end of file");
		}
		char c = text.charAt(pos);
		int at = line;
		switch (c) {
			case '{':
				return object();
			case '[':
				return array();
			case '"':
			case '\'':
				return new JValue.JString(string(), file, at);
			default:
				break;
		}
		if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) {
			return new JValue.JNumber(number(), file, at);
		}
		String word = identifier();
		switch (word) {
			case "true":
				return new JValue.JBool(true, file, at);
			case "false":
				return new JValue.JBool(false, file, at);
			case "null":
				return new JValue.JNull(file, at);
			case "Infinity":
				return new JValue.JNumber(Double.POSITIVE_INFINITY, file, at);
			case "NaN":
				return new JValue.JNumber(Double.NaN, file, at);
			case "":
				throw error("unexpected '" + c + "'");
			default:
				throw error("unexpected word '" + word + "' (text values need quotes)");
		}
	}

	private JValue.JObject object() {
		int at = line;
		pos++; // {
		Map<String, JValue> map = new LinkedHashMap<>();
		Map<String, Integer> keyLines = new LinkedHashMap<>();
		while (true) {
			skipSpace();
			if (pos >= text.length()) {
				throw error("unclosed '{' (opened on line " + at + ")");
			}
			if (text.charAt(pos) == '}') {
				pos++;
				return new JValue.JObject(map, keyLines, file, at);
			}
			int keyLine = line;
			String key;
			char c = text.charAt(pos);
			if (c == '"' || c == '\'') {
				key = string();
			} else {
				key = identifier();
				if (key.isEmpty()) {
					throw error("expected a key, found '" + c + "'");
				}
			}
			skipSpace();
			if (pos >= text.length() || text.charAt(pos) != ':') {
				throw error("expected ':' after key '" + key + "'");
			}
			pos++;
			skipSpace();
			if (map.containsKey(key)) {
				throw error("key '" + key + "' appears twice (first on line " + keyLines.get(key) + ")");
			}
			map.put(key, value());
			keyLines.put(key, keyLine);
			skipSpace();
			if (pos < text.length() && text.charAt(pos) == ',') {
				pos++;
			} else if (pos < text.length() && text.charAt(pos) != '}') {
				throw error("expected ',' or '}' after the value of '" + key + "'");
			}
		}
	}

	private JValue.JArray array() {
		int at = line;
		pos++; // [
		List<JValue> list = new ArrayList<>();
		while (true) {
			skipSpace();
			if (pos >= text.length()) {
				throw error("unclosed '[' (opened on line " + at + ")");
			}
			if (text.charAt(pos) == ']') {
				pos++;
				return new JValue.JArray(list, file, at);
			}
			list.add(value());
			skipSpace();
			if (pos < text.length() && text.charAt(pos) == ',') {
				pos++;
			} else if (pos < text.length() && text.charAt(pos) != ']') {
				throw error("expected ',' or ']' in the list");
			}
		}
	}

	private String string() {
		char quote = text.charAt(pos++);
		int at = line;
		StringBuilder sb = new StringBuilder();
		while (true) {
			if (pos >= text.length()) {
				throw new Json5Exception(file, at, "unclosed text (missing " + quote + ")");
			}
			char c = text.charAt(pos++);
			if (c == quote) {
				return sb.toString();
			}
			if (c == '\n') {
				throw new Json5Exception(file, at, "text runs past the end of the line (missing " + quote + ")");
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			if (pos >= text.length()) {
				throw error("unclosed text");
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
					if (pos + 4 > text.length()) {
						throw error("bad \\u escape");
					}
					try {
						sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
					} catch (NumberFormatException ex) {
						throw error("bad \\u escape");
					}
					pos += 4;
				}
				case '\n' -> line++; // line continuation
				case '\r' -> {
					if (pos < text.length() && text.charAt(pos) == '\n') {
						pos++;
					}
					line++;
				}
				default -> sb.append(e);
			}
		}
	}

	private double number() {
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
			if (digits == pos) {
				throw error("bad hex number");
			}
			double v = Long.parseLong(text.substring(digits, pos), 16);
			return negative ? -v : v;
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
		String s = text.substring(start, pos);
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw error("bad number '" + s + "'");
		}
	}

	private String identifier() {
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

	private void skipSpace() {
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (c == '\n') {
				line++;
				pos++;
			} else if (Character.isWhitespace(c) || c == '﻿') {
				pos++;
			} else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
				while (pos < text.length() && text.charAt(pos) != '\n') {
					pos++;
				}
			} else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
				int at = line;
				pos += 2;
				while (pos < text.length() && !(text.charAt(pos) == '*' && pos + 1 < text.length() && text.charAt(pos + 1) == '/')) {
					if (text.charAt(pos) == '\n') {
						line++;
					}
					pos++;
				}
				if (pos >= text.length()) {
					throw new Json5Exception(file, at, "unclosed /* comment");
				}
				pos += 2;
			} else {
				break;
			}
		}
	}
}
