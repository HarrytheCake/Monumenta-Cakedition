package io.github.harrythecake.cakewiki.doc;

import java.util.Locale;
import java.util.Map;

/** Reads colours out of inline CSS ({@code style="color: #f00"}) and old {@code color=} attributes. */
public final class CssColor {
	private static final Map<String, Integer> NAMED = Map.ofEntries(
			Map.entry("black", 0x000000), Map.entry("white", 0xFFFFFF), Map.entry("red", 0xFF0000),
			Map.entry("green", 0x008000), Map.entry("blue", 0x0000FF), Map.entry("yellow", 0xFFFF00),
			Map.entry("orange", 0xFFA500), Map.entry("purple", 0x800080), Map.entry("gray", 0x808080),
			Map.entry("grey", 0x808080), Map.entry("gold", 0xFFD700), Map.entry("aqua", 0x00FFFF),
			Map.entry("cyan", 0x00FFFF), Map.entry("magenta", 0xFF00FF), Map.entry("fuchsia", 0xFF00FF),
			Map.entry("lime", 0x00FF00), Map.entry("maroon", 0x800000), Map.entry("navy", 0x000080),
			Map.entry("olive", 0x808000), Map.entry("teal", 0x008080), Map.entry("silver", 0xC0C0C0),
			Map.entry("pink", 0xFFC0CB), Map.entry("brown", 0xA52A2A), Map.entry("crimson", 0xDC143C),
			Map.entry("violet", 0xEE82EE), Map.entry("indigo", 0x4B0082), Map.entry("darkred", 0x8B0000),
			Map.entry("darkgreen", 0x006400), Map.entry("darkblue", 0x00008B), Map.entry("darkorange", 0xFF8C00),
			Map.entry("darkgray", 0xA9A9A9), Map.entry("darkgrey", 0xA9A9A9), Map.entry("lightgray", 0xD3D3D3),
			Map.entry("lightgrey", 0xD3D3D3), Map.entry("lightblue", 0xADD8E6), Map.entry("lightgreen", 0x90EE90),
			Map.entry("skyblue", 0x87CEEB), Map.entry("goldenrod", 0xDAA520), Map.entry("orangered", 0xFF4500),
			Map.entry("darkviolet", 0x9400D3), Map.entry("deepskyblue", 0x00BFFF), Map.entry("limegreen", 0x32CD32));

	private CssColor() {}

	/** The value of {@code property} in a {@code style} attribute as 0xRRGGBB, or null if absent or not a plain colour. */
	public static Integer fromStyle(String style, String property) {
		if (style == null || style.isEmpty()) return null;
		Integer found = null;
		for (String decl : style.split(";")) {
			int colon = decl.indexOf(':');
			if (colon < 0) continue;
			String name = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			String value = decl.substring(colon + 1).replace("!important", "").trim();
			if (name.equals(property)) {
				found = parse(value);
			} else if (property.equals("background-color") && name.equals("background")) {
				// shorthand: the colour is any one of its tokens
				for (String token : value.split("\\s+(?![^(]*\\))")) {
					Integer c = parse(token);
					if (c != null) { found = c; break; }
				}
			}
		}
		return found; // a later declaration wins, as in CSS
	}

	/** One CSS colour value ({@code #rgb}, {@code #rrggbb}, {@code #rrggbbaa}, {@code rgb(…)}, a common name) or null. */
	public static Integer parse(String value) {
		if (value == null) return null;
		String v = value.trim().toLowerCase(Locale.ROOT);
		try {
			if (v.startsWith("#")) {
				String hex = v.substring(1);
				if (hex.length() == 3 || hex.length() == 4) {
					return Integer.parseInt("" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
							+ hex.charAt(2) + hex.charAt(2), 16);
				}
				if (hex.length() == 6 || hex.length() == 8) return Integer.parseInt(hex.substring(0, 6), 16);
				return null;
			}
			if (v.startsWith("rgb")) {
				String[] parts = v.substring(v.indexOf('(') + 1, v.lastIndexOf(')')).split("[,\\s/]+");
				int rgb = 0;
				for (int i = 0; i < 3; i++) {
					String p = parts[i].trim();
					int c = p.endsWith("%") ? Math.round(Float.parseFloat(p.substring(0, p.length() - 1)) * 2.55f)
							: Math.round(Float.parseFloat(p));
					rgb = rgb << 8 | Math.max(0, Math.min(255, c));
				}
				return rgb;
			}
		} catch (RuntimeException e) {
			return null;
		}
		return NAMED.get(v);
	}
}
