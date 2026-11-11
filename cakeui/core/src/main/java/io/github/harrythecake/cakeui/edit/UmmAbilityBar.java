package io.github.harrythecake.cakeui.edit;

/**
 * The arithmetic behind moving the Unofficial Monumenta Mod's ability bar in the HUD editor (its runtime
 * side is {@code UmmAbilities} in the 1.20.4 code). UMM places the bar with an anchor of its own:
 * {@code x = round(screenW * relX + absX - alignX * barW)} (the same for y). A dropped bar is attached to
 * the nearest of the 9 screen anchors, as the editor's own pieces are ({@link Placement#nearest}), with
 * align = rel, so it keeps its distance from that edge or centre on any window size. Pure Java, tested.
 */
public final class UmmAbilityBar {
	/** Icon sizes the editor may set (UMM itself has no limits; it defaults to 32). */
	public static final int MIN_ICON = 8;
	public static final int MAX_ICON = 128;

	private UmmAbilityBar() {
	}

	/**
	 * UMM's position fields for a bar whose top-left is at (left, top), size bw x bh, on a sw x sh screen:
	 * {relX, relY, absX, absY} (alignX = relX, alignY = relY).
	 */
	public static double[] anchor(double left, double top, double bw, double bh, double sw, double sh) {
		double rx = Placement.nearest(left + bw / 2, sw);
		double ry = Placement.nearest(top + bh / 2, sh);
		return new double[] {rx, ry, Math.round(left - rx * (sw - bw)), Math.round(top - ry * (sh - bh))};
	}

	/** Where UMM draws the bar's left (or top) edge with these fields: its own formula. */
	public static int edge(double screen, double rel, double abs, double align, double size) {
		return Math.round((float) (screen * rel + abs - align * size));
	}

	/** The bar's size: {width, height} for n icons. */
	public static int[] size(int n, int icon, int gap, boolean horizontal) {
		int main = n * icon + gap * Math.max(0, n - 1);
		return horizontal ? new int[] {main, icon} : new int[] {icon, main};
	}

	/** How many icons a bar of this size holds (at least 1). */
	public static int count(double bw, double bh, int icon, int gap, boolean horizontal) {
		double main = horizontal ? bw : bh;
		return Math.max(1, (int) Math.round((main + gap) / (icon + gap)));
	}

	/**
	 * The icon size for a bar the editor resized to w x h (the corner handle, or scrolling x1.05). The
	 * side that changed more decides (a drag may only move one); a change of at least 0.4 px always moves
	 * the size by one, so scrolling still works on small icons. Unchanged when w x h is the current size.
	 */
	public static int iconSize(int current, int n, int gap, boolean horizontal, double w, double h) {
		double cross = horizontal ? h : w;
		double main = horizontal ? w : h;
		double fromCross = cross;
		double fromMain = (main - gap * Math.max(0, n - 1)) / Math.max(1, n);
		double wanted = Math.abs(fromCross - current) >= Math.abs(fromMain - current) ? fromCross : fromMain;
		int size = (int) Math.round(wanted);
		if (size == current && Math.abs(wanted - current) >= 0.4) {
			size += wanted > current ? 1 : -1;
		}
		return Math.max(MIN_ICON, Math.min(MAX_ICON, size));
	}
}
