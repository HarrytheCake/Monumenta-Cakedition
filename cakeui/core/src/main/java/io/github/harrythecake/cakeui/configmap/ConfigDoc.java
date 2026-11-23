package io.github.harrythecake.cakeui.configmap;

import java.math.BigDecimal;
import java.util.List;

/**
 * Another mod's config file as text, edited value by value: {@link #set} replaces only the value's own characters, so
 * comments, key order, formatting and keys nobody mapped stay exactly as they were (ADR 0011).
 *
 * <p>Values read back are {@link Boolean}, {@link Double} (every number), {@link String}, or {@link #OTHER} (lists,
 * tables, null, anything else); a missing key reads as {@code null}. The line formats ({@code options.txt},
 * {@code .properties}) have no types: their values read back as the raw {@link String}. {@link #set} takes Boolean,
 * Long (written without a decimal point), Double (written with one) or String.
 */
public abstract class ConfigDoc {
	/** A value that exists but isn't a plain scalar (list, object/table, null). */
	public static final Object OTHER = new Object() {
		@Override
		public String toString() {
			return "(not a plain value)";
		}
	};

	protected String text;

	protected ConfigDoc(String text) {
		this.text = text;
	}

	/** Parses the text; throws {@link ConfigDocException} (with the line) if it isn't valid in that format. */
	public static ConfigDoc parse(Format format, String text) {
		String t = text.startsWith("﻿") ? text.substring(1) : text;
		return switch (format) {
			case JSON, JSON5 -> new JsonDoc(t, format == Format.JSON5);
			case TOML -> new TomlDoc(t);
			case OPTIONS -> new LineDoc(t, false);
			case PROPERTIES -> new LineDoc(t, true);
		};
	}

	/** The value at the path, {@link #OTHER} for a non-scalar, or null when the key isn't there. */
	public abstract Object get(List<String> path);

	/** Writes the value (Boolean, Long, Double, String) at the path, adding the key when it's missing. */
	public abstract void set(List<String> path, Object value);

	/** The whole document as it is now. */
	public String text() {
		return text;
	}

	/** Splits "a.b.c" into its keys; "\." is a dot inside a key. */
	public static List<String> path(String dotted) {
		java.util.ArrayList<String> out = new java.util.ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < dotted.length(); i++) {
			char c = dotted.charAt(i);
			if (c == '\\' && i + 1 < dotted.length() && dotted.charAt(i + 1) == '.') {
				sb.append('.');
				i++;
			} else if (c == '.') {
				out.add(sb.toString());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		out.add(sb.toString());
		return List.copyOf(out);
	}

	/** Whether two read or typed values mean the same (numbers by value, so 4, 4L and 4.0 match). */
	public static boolean same(Object a, Object b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a instanceof Number x && b instanceof Number y) {
			return x.doubleValue() == y.doubleValue();
		}
		return a.equals(b);
	}

	/** A number as written into a file: Long without a decimal point, Double always with one ("1.0", "0.15"). */
	static String number(Object v) {
		if (v instanceof Long || v instanceof Integer) {
			return v.toString();
		}
		double d = ((Number) v).doubleValue();
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			throw new IllegalArgumentException("can't write " + d);
		}
		if (d == Math.rint(d) && Math.abs(d) < 1e15) {
			return (long) d + ".0";
		}
		return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
	}

	/** A double-quoted string with JSON/TOML escapes. */
	static String quoted(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}

	/** Replaces text[start, end) and returns the new text. */
	protected void splice(int start, int end, String with) {
		text = text.substring(0, start) + with + text.substring(end);
	}

	/** The line number (from 1) of a character offset, for problems. */
	static int lineOf(String text, int offset) {
		int line = 1;
		for (int i = 0; i < offset && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}
}
