package io.github.harrythecake.cakemap.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The colours a player can give their own waypoints (the pin icon, its name and its world-view
 * text). 0 stands for the default blue, so old files without a colour keep their look. Pure Java.
 */
public final class WaypointColors {
	/** The default pin colour (player.svg's own blue). */
	public static final int DEFAULT = 0x3A8DFF;
	/** The swatches, in the editor's order: 0 = default blue, red, orange, yellow, green, cyan, purple, white. */
	public static final int[] PALETTE = {0, 0xE5484D, 0xFF9F1C, 0xFFD43B, 0x4CC764, 0x3BD4E0, 0xA66BFF, 0xF2F2F2};

	/** The colour in an icon that a player waypoint's colour replaces, unless the icon names another. */
	public static final String RECOLOR = "#3a8dff";
	/** An icon names its own recolour target with {@code <!-- cakemap-recolor: #9a9a9a -->}. */
	private static final Pattern RECOLOR_MARK = Pattern.compile("<!--\\s*cakemap-recolor:\\s*(#[0-9a-fA-F]{6})\\b.*?-->", Pattern.DOTALL);

	private WaypointColors() {
	}

	/** The colour an icon's source gets replaced (its {@code cakemap-recolor} comment, else {@link #RECOLOR}). */
	public static String recolorTarget(String svg) {
		Matcher m = RECOLOR_MARK.matcher(svg);
		return m.find() ? m.group(1) : RECOLOR;
	}

	/** An SVG icon's source with its recolour target (case-insensitive) painted in {@code color}. */
	public static String recolorSvg(String svg, int color) {
		String from = RECOLOR;
		Matcher m = RECOLOR_MARK.matcher(svg);
		if (m.find()) {
			from = m.group(1);
			svg = svg.substring(0, m.start()) + svg.substring(m.end());
		}
		String to = String.format(Locale.ROOT, "#%06x", opaque(color) & 0xFFFFFF);
		return Pattern.compile(Pattern.quote(from), Pattern.CASE_INSENSITIVE).matcher(svg).replaceAll(to);
	}

	/** The RGB a colour value stands for (0 → the default blue). */
	public static int opaque(int color) {
		return color == 0 ? DEFAULT : color & 0xFFFFFF;
	}

	/** The swatch closest to an RGB colour (Xaero's 16 chat colours are mapped with this). */
	public static int nearest(int rgb) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int c : PALETTE) {
			int o = opaque(c);
			long dr = ((o >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			long dg = ((o >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			long db = (o & 0xFF) - (rgb & 0xFF);
			long d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}
}
