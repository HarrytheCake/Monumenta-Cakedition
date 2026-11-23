package io.github.harrythecake.cakeui.configmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod versions a config map was written for: space-separated conditions that must all hold, each {@code >=v},
 * {@code >v}, {@code <=v}, {@code <v}, {@code =v} or a bare {@code v} (= exactly), or {@code *} for any version.
 * Example: {@code ">=2.13 <3"}. Build metadata after '+' is ignored ("2.15.2+1.20.4" is 2.15.2).
 */
public final class VersionRange {
	private record Cond(String op, String version) {
	}

	private final List<Cond> conds;
	private final String text;

	private VersionRange(List<Cond> conds, String text) {
		this.conds = conds;
		this.text = text;
	}

	public static final VersionRange ANY = new VersionRange(List.of(), "*");

	/** Parses the range; throws IllegalArgumentException on a condition it can't read. */
	public static VersionRange parse(String text) {
		String t = text.trim();
		if (t.isEmpty() || t.equals("*")) {
			return ANY;
		}
		List<Cond> conds = new ArrayList<>();
		for (String part : t.split("\\s+")) {
			String op = part.startsWith(">=") || part.startsWith("<=") ? part.substring(0, 2)
					: part.startsWith(">") || part.startsWith("<") || part.startsWith("=") ? part.substring(0, 1) : "=";
			String v = part.substring(part.startsWith(op) ? op.length() : 0);
			if (v.isEmpty() || !Character.isDigit(v.charAt(0))) {
				throw new IllegalArgumentException("can't read the version condition '" + part + "'");
			}
			conds.add(new Cond(op, v));
		}
		return new VersionRange(List.copyOf(conds), t);
	}

	/** Whether the version is in the range; an unknown version (null) only fits {@link #ANY}. */
	public boolean contains(String version) {
		if (conds.isEmpty()) {
			return true;
		}
		if (version == null) {
			return false;
		}
		for (Cond c : conds) {
			int cmp = compare(version, c.version);
			boolean ok = switch (c.op) {
				case ">=" -> cmp >= 0;
				case ">" -> cmp > 0;
				case "<=" -> cmp <= 0;
				case "<" -> cmp < 0;
				default -> cmp == 0;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Compares two versions part by part ("1.10" &gt; "1.9"; missing parts are 0, so "3" == "3.0"); a pre-release
	 * ("1.2.0-beta.1") is lower than its release; build metadata after '+' is ignored.
	 */
	public static int compare(String a, String b) {
		String[] x = split(a);
		String[] y = split(b);
		String[] mx = x[0].split("\\."), my = y[0].split("\\.");
		for (int i = 0; i < Math.max(mx.length, my.length); i++) {
			int c = part(i < mx.length ? mx[i] : "0", i < my.length ? my[i] : "0");
			if (c != 0) {
				return c;
			}
		}
		if (x[1] == null || y[1] == null) {
			return x[1] == null ? (y[1] == null ? 0 : 1) : -1;
		}
		String[] px = x[1].split("\\."), py = y[1].split("\\.");
		for (int i = 0; i < Math.max(px.length, py.length); i++) {
			if (i >= px.length) {
				return -1;
			}
			if (i >= py.length) {
				return 1;
			}
			int c = part(px[i], py[i]);
			if (c != 0) {
				return c;
			}
		}
		return 0;
	}

	/** [release part, pre-release part or null], without build metadata. */
	private static String[] split(String v) {
		String s = v.trim();
		int plus = s.indexOf('+');
		if (plus >= 0) {
			s = s.substring(0, plus);
		}
		int dash = s.indexOf('-');
		return dash >= 0 ? new String[] {s.substring(0, dash), s.substring(dash + 1)} : new String[] {s, null};
	}

	private static int part(String a, String b) {
		boolean na = a.matches("\\d+"), nb = b.matches("\\d+");
		if (na && nb) {
			return Long.compare(Long.parseLong(a), Long.parseLong(b));
		}
		if (na != nb) {
			return na ? -1 : 1;  // numbers sort before words
		}
		return a.compareTo(b);
	}

	@Override
	public String toString() {
		return text;
	}
}
