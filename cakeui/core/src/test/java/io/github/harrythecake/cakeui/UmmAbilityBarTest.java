package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.UmmAbilityBar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UmmAbilityBarTest {
	private static final int W = 480;
	private static final int H = 270;

	/** Puts the bar at (left, top) and returns where UMM's own formula then draws it. */
	private static int[] dropAndDraw(double left, double top, int bw, int bh, int sw, int sh, int drawW, int drawH) {
		double[] a = UmmAbilityBar.anchor(left, top, bw, bh, sw, sh);
		return new int[] {
				UmmAbilityBar.edge(drawW, a[0], a[2], a[0], bw),
				UmmAbilityBar.edge(drawH, a[1], a[3], a[1], bh)};
	}

	@Test
	void droppedBarIsDrawnWhereItWasDropped() {
		// 5 icons of 32 in a row, in each of the 9 regions of the screen
		for (double left : new double[] {4, 160, 300}) {
			for (double top : new double[] {4, 120, 230}) {
				assertArrayEquals(new int[] {(int) left, (int) top}, dropAndDraw(left, top, 160, 32, W, H, W, H), left + "," + top);
			}
		}
	}

	@Test
	void anchorFollowsItsCornerOnAnotherScreenSize() {
		// near the bottom-right corner: same distance from that corner on a bigger screen
		int[] at = dropAndDraw(W - 170, H - 40, 160, 32, W, H, 640, 360);
		assertArrayEquals(new int[] {640 - 170, 360 - 40}, at);
		// above the hotbar (centre-bottom): stays centred
		at = dropAndDraw(W / 2.0 - 80, H - 73, 160, 32, W, H, 640, 360);
		assertArrayEquals(new int[] {320 - 80, 360 - 73}, at);
	}

	@Test
	void anchorIsTheNearestOfNine() {
		double[] a = UmmAbilityBar.anchor(W / 2.0 - 80, H - 73, 160, 32, W, H);
		assertEquals(0.5, a[0]);
		assertEquals(1, a[1]);
		a = UmmAbilityBar.anchor(2, 2, 32, 160, W, H);
		assertEquals(0, a[0]);
		assertEquals(0, a[1]);
		assertEquals(2, a[2]);
		assertEquals(2, a[3]);
	}

	@Test
	void sizeAndCount() {
		assertArrayEquals(new int[] {5 * 32 + 4 * 2, 32}, UmmAbilityBar.size(5, 32, 2, true));
		assertArrayEquals(new int[] {32, 5 * 32}, UmmAbilityBar.size(5, 32, 0, false));
		assertEquals(5, UmmAbilityBar.count(168, 32, 32, 2, true));
		assertEquals(5, UmmAbilityBar.count(32, 160, 32, 0, false));
		assertEquals(1, UmmAbilityBar.count(0, 0, 32, 0, true));
	}

	@Test
	void movingKeepsTheIconSize() {
		assertEquals(32, UmmAbilityBar.iconSize(32, 5, 2, true, 168, 32));
		assertEquals(20, UmmAbilityBar.iconSize(20, 4, 0, false, 20, 80));
	}

	@Test
	void scrollingScalesBothWays() {
		int s = 32;
		int up = UmmAbilityBar.iconSize(s, 5, 0, true, 160 * 1.05, 32 * 1.05);
		assertEquals(34, up);
		int down = UmmAbilityBar.iconSize(s, 5, 0, true, 160 / 1.05, 32 / 1.05);
		assertEquals(30, down);
		// small icons still grow and shrink by one (5% of 8 is under half a pixel)
		assertEquals(9, UmmAbilityBar.iconSize(8, 3, 0, true, 24 * 1.05, 8 * 1.05));
		assertEquals(UmmAbilityBar.MIN_ICON, UmmAbilityBar.iconSize(8, 3, 0, true, 24 / 1.05, 8 / 1.05));
	}

	@Test
	void handleDragUsesTheSideThatChangedMore() {
		// a horizontal bar of 5: dragging only down by 8 px makes the icons 8 px bigger
		assertEquals(40, UmmAbilityBar.iconSize(32, 5, 0, true, 160, 40));
		// dragging only right by 50 px: 10 px more per icon
		assertEquals(42, UmmAbilityBar.iconSize(32, 5, 0, true, 210, 32));
		assertEquals(UmmAbilityBar.MAX_ICON, UmmAbilityBar.iconSize(32, 1, 0, true, 999, 999));
	}
}
