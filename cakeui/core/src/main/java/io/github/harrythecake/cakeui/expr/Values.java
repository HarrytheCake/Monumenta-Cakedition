package io.github.harrythecake.cakeui.expr;

import java.util.Locale;

/** Conversions between expression values. Never throws: a value that doesn't fit becomes 0, "" or false. */
public final class Values {
	private Values() {
	}

	public static double num(Object v) {
		if (v instanceof Double d) {
			return d;
		}
		if (v instanceof Px p) {
			return p.value();
		}
		if (v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		if (v instanceof Number n) {
			return n.doubleValue();
		}
		if (v instanceof String s) {
			if (s.startsWith("#")) {
				Integer c = parseColor(s);
				return c == null ? 0 : c;
			}
			try {
				return Double.parseDouble(s.trim());
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	public static boolean bool(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof String s) {
			return !s.isEmpty() && !s.equals("false");
		}
		if (v == null) {
			return false;
		}
		double d = num(v);
		return d != 0 && !Double.isNaN(d);
	}

	public static String str(Object v) {
		if (v == null) {
			return "";
		}
		if (v instanceof String s) {
			return s;
		}
		if (v instanceof Double d) {
			return format(d);
		}
		if (v instanceof Px p) {
			return format(p.value());
		}
		return v.toString();
	}

	/** Whole numbers without ".0"; others with up to 2 decimals. */
	public static String format(double d) {
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			return "0";
		}
		if (d == Math.rint(d) && Math.abs(d) < 1e15) {
			return Long.toString((long) d);
		}
		String s = String.format(Locale.ROOT, "%.2f", d);
		while (s.endsWith("0")) {
			s = s.substring(0, s.length() - 1);
		}
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}

	public static String format(double d, int decimals) {
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			return "0";
		}
		return String.format(Locale.ROOT, "%." + Math.max(0, Math.min(10, decimals)) + "f", d);
	}

	/** A length value in GUI pixels: a Px as is, a plain number as design pixels. */
	public static double length(Object v, Env env) {
		if (v instanceof Px p) {
			return p.value();
		}
		if (v instanceof String s) {
			Object parsed = parseLengthText(s.trim(), env);
			if (parsed != null) {
				return (Double) parsed;
			}
		}
		return env.plainLength(num(v));
	}

	private static Object parseLengthText(String s, Env env) {
		Env.Unit unit = null;
		String digits = s;
		if (s.endsWith("px")) {
			unit = Env.Unit.PX;
			digits = s.substring(0, s.length() - 2);
		} else if (s.endsWith("r")) {
			unit = Env.Unit.R;
			digits = s.substring(0, s.length() - 1);
		} else if (s.endsWith("%")) {
			unit = Env.Unit.PERCENT;
			digits = s.substring(0, s.length() - 1);
		}
		try {
			double d = Double.parseDouble(digits);
			return unit == null ? env.plainLength(d) : env.unit(unit, d);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** An ARGB colour: numbers as 0xAARRGGBB, text as "#RGB", "#RRGGBB" or "#RRGGBBAA". */
	public static int color(Object v) {
		if (v instanceof String s) {
			Integer c = parseColor(s.trim());
			return c == null ? 0xFFFFFFFF : c;
		}
		return (int) (long) num(v);
	}

	/** Parses "#RGB", "#RGBA", "#RRGGBB" or "#RRGGBBAA" (CSS order, alpha last) to ARGB; null if not a colour. */
	public static Integer parseColor(String s) {
		if (!s.startsWith("#")) {
			return null;
		}
		String h = s.substring(1);
		for (int i = 0; i < h.length(); i++) {
			if (Character.digit(h.charAt(i), 16) < 0) {
				return null;
			}
		}
		switch (h.length()) {
			case 3, 4 -> {
				StringBuilder sb = new StringBuilder();
				for (char c : h.toCharArray()) {
					sb.append(c).append(c);
				}
				h = sb.toString();
			}
			case 6, 8 -> {
			}
			default -> {
				return null;
			}
		}
		long rgb = Long.parseLong(h.substring(0, 6), 16);
		long a = h.length() == 8 ? Long.parseLong(h.substring(6, 8), 16) : 0xFF;
		return (int) ((a << 24) | rgb);
	}

	public static boolean equal(Object a, Object b) {
		if (a instanceof String || b instanceof String) {
			return str(a).equals(str(b));
		}
		if (a instanceof Boolean || b instanceof Boolean) {
			return bool(a) == bool(b);
		}
		return num(a) == num(b);
	}

	public static int mixColor(int c1, int c2, double t) {
		t = Math.max(0, Math.min(1, t));
		int a = (int) Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
		int r = (int) Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = (int) Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = (int) Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
