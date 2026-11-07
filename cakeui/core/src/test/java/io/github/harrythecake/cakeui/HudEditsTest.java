package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.edit.HudEdits;
import io.github.harrythecake.cakeui.edit.Placement;
import io.github.harrythecake.cakeui.edit.Placement.Box;
import io.github.harrythecake.cakeui.edit.PortEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudEditsTest {
	@TempDir
	Path dir;

	private static final double W = 480;
	private static final double H = 270;

	@Test
	void anchoredFollowsItsCornerOnAnotherScreenSize() {
		// a 64x64 minimap dropped near the bottom-right corner
		PortEdit e = new PortEdit();
		Placement.place(e, PortEdit.Mode.ANCHORED, W - 70, H - 80, 64, 64, W, H);
		assertEquals(1, e.ax);
		assertEquals(1, e.ay);
		assertEquals(-6, e.x);
		assertEquals(-16, e.y);
		// on a bigger screen it keeps the same distance to that corner
		Box inner = new Box(4, 4, 64, 64);
		Box shown = Placement.transform(e, inner, 960, 540).apply(inner);
		assertEquals(960 - 6 - 64, shown.x(), 1e-9);
		assertEquals(540 - 16 - 64, shown.y(), 1e-9);
	}

	@Test
	void anchoredPicksTheNearestOfNine() {
		PortEdit e = new PortEdit();
		Placement.place(e, PortEdit.Mode.ANCHORED, W / 2 - 91, H - 22, 182, 22, W, H); // the hotbar's own place
		assertEquals(0.5, e.ax);
		assertEquals(1, e.ay);
		assertEquals(0, e.x);
		assertEquals(0, e.y);
		Placement.place(e, PortEdit.Mode.ANCHORED, 10, 100, 40, 20, W, H);
		assertEquals(0, e.ax);
		assertEquals(0.5, e.ay);
	}

	@Test
	void percentAndFixed() {
		PortEdit p = new PortEdit();
		Placement.place(p, PortEdit.Mode.PERCENT, (W - 100) * 0.25, 0, 100, 10, W, H);
		assertEquals(0.25, p.ax, 1e-4);
		Box inner = new Box(0, 0, 100, 10);
		assertEquals((960 - 100) * 0.25, Placement.transform(p, inner, 960, 540).apply(inner).x(), 0.01);

		PortEdit f = new PortEdit();
		Placement.place(f, PortEdit.Mode.FIXED, 30, 40, 100, 10, W, H);
		assertEquals(30, Placement.transform(f, inner, 960, 540).apply(inner).x(), 1e-9);
		assertEquals(40, Placement.transform(f, inner, 960, 540).apply(inner).y(), 1e-9);
	}

	@Test
	void scaleOnlyGrowsFromTheCentreAndScaleKeepsTheAnchor() {
		Box inner = new Box(100, 100, 20, 10);
		PortEdit e = new PortEdit();
		e.scale = 2.0;
		Box shown = Placement.transform(e, inner, W, H).apply(inner);
		assertEquals(inner.cx(), shown.cx(), 1e-9);
		assertEquals(40, shown.w(), 1e-9);
		// anchored bottom-right: a bigger piece stays in its corner
		Placement.place(e, PortEdit.Mode.ANCHORED, W - 50, H - 30, 40, 20, W, H);
		e.scale = 3.0;
		Box big = Placement.transform(e, inner, W, H).apply(inner);
		assertEquals(W - 10, big.right(), 1e-9);
		assertEquals(H - 10, big.bottom(), 1e-9);
	}

	@Test
	void xformInvertsForMeasuring() {
		Placement.Xform t = new Placement.Xform(10, 20, 2);
		assertEquals(5, t.innerX(20), 1e-9);
		assertEquals(5, t.innerY(30), 1e-9);
	}

	@Test
	void snapsToScreenCentreAndOtherPieces() {
		Placement.Snapped s = Placement.snap(W / 2 - 50 + 3, 50, 100, 20, List.of(), W, H, 4);
		assertEquals(W / 2 - 50, s.left(), 1e-9);
		assertEquals(1, s.guides().size());
		Box other = new Box(300, 200, 50, 10);
		Placement.Snapped t = Placement.snap(10, 211.5, 40, 10, List.of(other), W, H, 4);
		assertEquals(210, t.top(), 1e-9); // top edge onto the other piece's bottom
		Placement.Snapped none = Placement.snap(100, 100, 10, 10, List.of(), W, H, 4);
		assertEquals(100, none.left(), 1e-9);
		assertTrue(none.guides().isEmpty());
	}

	@Test
	void fileRoundTripPerLayout() throws Exception {
		HudEdits edits = new HudEdits();
		PortEdit hotbar = new PortEdit();
		Placement.place(hotbar, PortEdit.Mode.ANCHORED, 0, H - 22, 182, 22, W, H);
		hotbar.opacity = 0.5f;
		PortEdit boss = new PortEdit();
		boss.hide = true;
		edits.put(HudEdits.VANILLA, Map.of("hotbar", hotbar, "bossBars", boss, "air", new PortEdit()));
		PortEdit mini = new PortEdit();
		mini.scale = 1.5;
		edits.put("window_example", Map.of("minimap", mini));
		Path file = dir.resolve("cakeui").resolve("hud.json5");
		edits.save(file);
		assertTrue(Files.readString(file).startsWith("//"));

		HudEdits back = HudEdits.load(file);
		assertEquals(2, back.set(HudEdits.VANILLA).size()); // the empty "air" edit isn't kept
		assertEquals(hotbar, back.set(HudEdits.VANILLA).get("hotbar"));
		assertEquals(Boolean.TRUE, back.set(HudEdits.VANILLA).get("bossBars").hide);
		assertNull(back.set(HudEdits.VANILLA).get("bossBars").mode);
		assertEquals(1.5, back.set("window_example").get("minimap").scale);
		assertTrue(back.set("window_other").isEmpty());
	}

	@Test
	void brokenOrNewerFileGivesNoEdits() throws Exception {
		Path file = dir.resolve("hud.json5");
		Files.writeString(file, "{ format: 9, layouts: {} }");
		assertTrue(HudEdits.load(file).set(HudEdits.VANILLA).isEmpty());
		assertTrue(Files.exists(dir.resolve("hud.json5.broken")));
		Files.writeString(file, "{ not json");
		assertTrue(HudEdits.load(file).set(HudEdits.VANILLA).isEmpty());
	}
}
