package io.github.harrythecake.cakemonu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IconPlacementTest {
	private static final double[] NONE = null;

	@Test
	void dropNearCornersPicksThatAnchorAndKeepsTheBox() {
		// bottom-right corner of a 480x270 screen
		IconPlacement p = IconPlacement.fromBox(440, 230, 32, 480, 270, 0, 32);
		assertEquals(1, p.anchorX);
		assertEquals(1, p.anchorY);
		assertEquals(0, p.size, "same as UMM's size and was 0: keeps following UMM");
		assertArrayEquals(new double[] {440, 230, 32, 32}, p.box(480, 270, 32));
		// same distance from the corner on a bigger window
		assertArrayEquals(new double[] {600, 320, 32, 32}, p.box(640, 360, 32));
	}

	@Test
	void middleDropUsesCentreAnchor() {
		IconPlacement p = IconPlacement.fromBox(224, 100, 32, 480, 270, 0, 32);
		assertEquals(0.5, p.anchorX);
		assertEquals(0.5, p.anchorY);
		assertArrayEquals(new double[] {224, 100, 32, 32}, p.box(480, 270, 32));
		// centre stays centre on another window size: (640-32)/2 + same offset
		double[] b = p.box(640, 360, 32);
		assertEquals(304, b[0]);
		assertEquals(145, b[1]);
	}

	@Test
	void ownSizeIsKeptAndClamped() {
		IconPlacement p = IconPlacement.fromBox(10, 10, 48, 480, 270, 0, 32);
		assertEquals(48, p.size);
		assertEquals(48, p.box(480, 270, 16)[2], "own size ignores UMM's");
		assertEquals(IconPlacement.MAX_SIZE, IconPlacement.fromBox(0, 0, 500, 480, 270, 0, 32).size);
		assertEquals(IconPlacement.MIN_SIZE, IconPlacement.fromBox(0, 0, 2, 480, 270, 0, 32).size);
		// once it had its own size, going back to UMM's number keeps it explicit
		assertEquals(32, IconPlacement.fromBox(10, 10, 32, 480, 270, 48, 32).size);
	}

	@Test
	void followsUmmSizeWhenZero() {
		IconPlacement p = IconPlacement.fromBox(0, 0, 32, 480, 270, 0, 32);
		assertEquals(24, p.box(480, 270, 24)[2]);
	}

	@Test
	void boxStaysOnScreen() {
		IconPlacement p = new IconPlacement(1, 1, 500, 500, 0);
		assertArrayEquals(new double[] {448, 238, 32, 32}, p.box(480, 270, 32));
		IconPlacement q = new IconPlacement(0, 0, -50, -50, 0);
		assertArrayEquals(new double[] {0, 0, 32, 32}, q.box(480, 270, 32));
	}

	@Test
	void nextToARowGoesRightOfTheBarAndSkipsTakenSpots() {
		double[] bar = {100, 200, 3 * 32 + 2 * 2, 32};
		IconPlacement first = IconPlacement.nextTo(bar, true, 32, 2, List.of(), 480, 270);
		assertArrayEquals(new double[] {202, 200, 32, 32}, first.box(480, 270, 32));
		IconPlacement second = IconPlacement.nextTo(bar, true, 32, 2, List.of(first.box(480, 270, 32)), 480, 270);
		assertArrayEquals(new double[] {236, 200, 32, 32}, second.box(480, 270, 32));
	}

	@Test
	void nextToAColumnGoesBelow() {
		double[] bar = {10, 20, 32, 100};
		assertArrayEquals(new double[] {10, 122, 32, 32}, IconPlacement.nextTo(bar, false, 32, 2, List.of(), 480, 270).box(480, 270, 32));
	}

	@Test
	void nextToWithoutABarUsesTheMiddleAndStaysOnScreen() {
		double[] b = IconPlacement.nextTo(NONE, true, 32, 2, List.of(), 480, 270).box(480, 270, 32);
		assertEquals(224, b[0]);
		double[] edge = IconPlacement.nextTo(new double[] {440, 0, 40, 32}, true, 32, 2, List.of(), 480, 270).box(480, 270, 32);
		assertEquals(448, edge[0], "past the right edge: back on the screen");
	}

	@Test
	void layoutRoundTrip(@TempDir Path dir) throws Exception {
		IconLayout l = new IconLayout();
		l.put("mage/magma shield", new IconPlacement(0.5, 1, -60, -70, 0));
		l.put("rogue/escape death", new IconPlacement(0, 0, 4, 5, 48));
		Path f = dir.resolve("cakemonu/ability-icons.json");
		l.write(f);
		IconLayout back = IconLayout.read(f);
		assertEquals(l.all(), back.all());
		assertTrue(Files.readString(f).contains("\"format\": 1"));
	}

	@Test
	void layoutSkipsJunkAndMovesBrokenFilesAside(@TempDir Path dir) throws Exception {
		IconLayout l = new IconLayout();
		l.parse("{\"icons\": {\"a/b\": 5, \"c/d\": {\"anchorX\": 1, \"x\": \"no\"}}}");
		assertEquals(1, l.all().size());
		assertEquals(new IconPlacement(1, 0, 0, 0, 0), l.get("c/d"));

		Path f = dir.resolve("ability-icons.json");
		Files.writeString(f, "{ not json");
		IconLayout broken = IconLayout.read(f);
		assertTrue(broken.isEmpty());
		assertTrue(Files.exists(dir.resolve("ability-icons.json.broken")));
		assertNotNull(IconLayout.read(dir.resolve("missing.json")));
	}

	@Test
	void newerFormatIsNotRead(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("ability-icons.json");
		Files.writeString(f, "{\"format\": 2, \"icons\": {\"a/b\": {}}}");
		assertTrue(IconLayout.read(f).isEmpty());
	}
}
