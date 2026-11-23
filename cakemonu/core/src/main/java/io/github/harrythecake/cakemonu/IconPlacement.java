package io.github.harrythecake.cakemonu;

/**
 * Where one ability icon sits on the HUD, in GUI pixels: tied to one of nine screen anchors (corners, edge
 * middles, centre), so it keeps its place when the window size changes. {@code offsetX/Y} go from the
 * anchor point of the screen to the same anchor point of the icon (top-left anchor: from the screen's
 * top-left to the icon's top-left; centre: centre to centre). {@code size} 0 = UMM's icon size.
 */
public final class IconPlacement {
	public static final int MIN_SIZE = 8;
	public static final int MAX_SIZE = 128;

	public final double anchorX;
	public final double anchorY;
	public final int offsetX;
	public final int offsetY;
	public final int size;

	public IconPlacement(double anchorX, double anchorY, int offsetX, int offsetY, int size) {
		this.anchorX = snap(anchorX);
		this.anchorY = snap(anchorY);
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.size = size <= 0 ? 0 : clamp(size, MIN_SIZE, MAX_SIZE);
	}

	/** The size it's drawn at: its own, else UMM's. */
	public int drawnSize(int ummSize) {
		return size > 0 ? size : Math.max(1, ummSize);
	}

	/** {x, y, size, size} on a screen of that size, kept on the screen. */
	public double[] box(int screenWidth, int screenHeight, int ummSize) {
		int s = drawnSize(ummSize);
		double x = anchorX * screenWidth + offsetX - anchorX * s;
		double y = anchorY * screenHeight + offsetY - anchorY * s;
		x = Math.max(0, Math.min(x, screenWidth - s));
		y = Math.max(0, Math.min(y, screenHeight - s));
		return new double[] {x, y, s, s};
	}

	/**
	 * The placement for an icon at that box (e.g. where the player dropped it in the HUD editor): the
	 * nearest of the nine anchors (by the box's centre, in thirds of the screen). The size stays 0 (follow
	 * UMM) while it equals UMM's size and was 0 before.
	 */
	public static IconPlacement fromBox(double x, double y, double width, int screenWidth, int screenHeight, int previousSize, int ummSize) {
		int s = clamp((int) Math.round(width), MIN_SIZE, MAX_SIZE);
		int size = previousSize == 0 && s == ummSize ? 0 : s;
		double ax = third((x + s / 2.0) / Math.max(1, screenWidth));
		double ay = third((y + s / 2.0) / Math.max(1, screenHeight));
		int ox = (int) Math.round(x - ax * screenWidth + ax * s);
		int oy = (int) Math.round(y - ay * screenHeight + ay * s);
		return new IconPlacement(ax, ay, ox, oy, size);
	}

	/**
	 * A first place for an icon that just left UMM's bar: right after the bar (a row) or below it (a
	 * column), moved on along the bar until it doesn't cover another placed icon. {@code bar} = UMM's bar
	 * box {x, y, w, h} or null (then the middle of the screen); {@code taken} = boxes of placed icons.
	 */
	public static IconPlacement nextTo(double[] bar, boolean horizontal, int ummSize, int gap, Iterable<double[]> taken,
			int screenWidth, int screenHeight) {
		int s = Math.max(1, ummSize);
		double x;
		double y;
		if (bar == null) {
			x = (screenWidth - s) / 2.0;
			y = screenHeight / 2.0 + 20;
		} else if (horizontal) {
			x = bar[0] + Math.max(0, bar[2]) + gap;
			y = bar[1];
		} else {
			x = bar[0];
			y = bar[1] + Math.max(0, bar[3]) + gap;
		}
		for (int tries = 0; tries < 64 && overlaps(x, y, s, taken); tries++) {
			if (horizontal) {
				x += s + gap;
			} else {
				y += s + gap;
			}
		}
		// past the edge: back onto the screen (it may then cover something; the player moves it anyway)
		x = Math.max(0, Math.min(x, screenWidth - s));
		y = Math.max(0, Math.min(y, screenHeight - s));
		return fromBox(x, y, s, screenWidth, screenHeight, 0, ummSize);
	}

	private static boolean overlaps(double x, double y, int s, Iterable<double[]> taken) {
		for (double[] b : taken) {
			if (x < b[0] + b[2] && b[0] < x + s && y < b[1] + b[3] && b[1] < y + s) {
				return true;
			}
		}
		return false;
	}

	private static double third(double f) {
		return f < 1 / 3.0 ? 0 : f > 2 / 3.0 ? 1 : 0.5;
	}

	private static double snap(double a) {
		return a < 0.25 ? 0 : a > 0.75 ? 1 : 0.5;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof IconPlacement p && p.anchorX == anchorX && p.anchorY == anchorY && p.offsetX == offsetX && p.offsetY == offsetY
				&& p.size == size;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(anchorX, anchorY, offsetX, offsetY, size);
	}

	@Override
	public String toString() {
		return "IconPlacement[anchor " + anchorX + "," + anchorY + " offset " + offsetX + "," + offsetY + " size " + size + "]";
	}
}
