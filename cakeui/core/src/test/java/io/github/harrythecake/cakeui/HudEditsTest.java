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
	void layoutEditsFileWithElementsAndMerge() throws Exception {
		PortEdit hotbar = new PortEdit();
		hotbar.scale = 0.75;
		Placement.place(hotbar, PortEdit.Mode.ANCHORED, 10, 10, 182, 22, W, H);
		PortEdit info = new PortEdit();
		info.hide = true;
		Path file = dir.resolve("hud").resolve("edits.json5");
		Files.createDirectories(file.getParent());
		HudEdits.saveLayoutEdits(file, Map.of("hotbar", hotbar, HudEdits.ELEMENT + "info", info));
		String text = Files.readString(file);
		assertTrue(text.contains("\"elements\"") && text.contains("\"info\""));
		Map<String, PortEdit> back = HudEdits.loadLayoutEdits(file);
		assertEquals(hotbar, back.get("hotbar"));
		assertEquals(Boolean.TRUE, back.get("@info").hide);

		// the player's edit replaces what it sets; the rest follows the layout
		PortEdit mine = new PortEdit();
		mine.opacity = 0.5f;
		Map<String, PortEdit> merged = HudEdits.merge(back, Map.of("hotbar", mine));
		assertEquals(0.75, merged.get("hotbar").scale);
		assertEquals(PortEdit.Mode.ANCHORED, merged.get("hotbar").mode);
		assertEquals(0.5f, merged.get("hotbar").opacity);
		assertEquals(Boolean.TRUE, merged.get("@info").hide);
		assertNull(back.get("hotbar").opacity); // merge doesn't change the layout's edits
		PortEdit moved = new PortEdit();
		Placement.place(moved, PortEdit.Mode.FIXED, 50, 60, 182, 22, W, H);
		assertEquals(PortEdit.Mode.FIXED, HudEdits.merge(back, Map.of("hotbar", moved)).get("hotbar").mode);

		// nothing left: the file goes
		HudEdits.saveLayoutEdits(file, Map.of("hotbar", new PortEdit()));
		assertTrue(!Files.exists(file));
		assertTrue(HudEdits.loadLayoutEdits(file).isEmpty());
	}

	@Test
	void personalFileKeepsElementsApart() throws Exception {
		HudEdits edits = new HudEdits();
		PortEdit e = new PortEdit();
		e.scale = 2.0;
		edits.put("window_example", Map.of("@info", e, "hotbar", e));
		Path file = dir.resolve("hud.json5");
		edits.save(file);
		HudEdits back = HudEdits.load(file);
		assertEquals(2, back.set("window_example").size());
		assertEquals(2.0, back.set("window_example").get("@info").scale);
	}

	@Test
	void addedElementsRoundTripAndMerge() throws Exception {
		PortEdit text = new PortEdit();
		Placement.place(text, PortEdit.Mode.ANCHORED, 10, 10, 40, 9, W, H);
		text.element = io.github.harrythecake.cakeui.edit.AddedElements.defaults("text", null);
		String key = io.github.harrythecake.cakeui.edit.AddedElements.newKey("text", Map.of());
		assertTrue(key.startsWith("+text-"));
		HudEdits edits = new HudEdits();
		edits.put(HudEdits.VANILLA, Map.of(key, text));
		Path file = dir.resolve("hud.json5");
		edits.save(file);
		assertTrue(Files.readString(file).contains("\"added\""));
		PortEdit back = HudEdits.load(file).set(HudEdits.VANILLA).get(key);
		assertEquals(text, back);
		assertEquals("{fps} fps", back.element.get("text").getAsString());

		// a layout's added element: the player may hide it, its definition stays the layout's
		PortEdit hide = new PortEdit();
		hide.hide = true;
		PortEdit merged = HudEdits.merge(Map.of(key, back), Map.of(key, hide)).get(key);
		assertEquals(Boolean.TRUE, merged.hide);
		assertEquals("text", merged.element.get("type").getAsString());

		// a definition-less "added" entry is dropped (nothing to draw)
		Path layoutFile = dir.resolve("edits.json5");
		Files.writeString(layoutFile, "{ format: 1, added: { broken: { mode: 'fixed', x: 1, y: 2 } } }");
		assertTrue(HudEdits.loadLayoutEdits(layoutFile).isEmpty());
	}

	@Test
	void addedElementParsesLikeALayoutElement() {
		// in game these come from McValues; a new element's defaults use them
		io.github.harrythecake.cakeui.model.GameValues.register("fps", () -> 60);
		io.github.harrythecake.cakeui.model.GameValues.register("player.health", () -> 20.0);
		io.github.harrythecake.cakeui.model.GameValues.register("player.maxHealth", () -> 20.0);
		io.github.harrythecake.cakeui.model.Layout layout = new io.github.harrythecake.cakeui.model.Layout("personal", dir);
		for (String type : io.github.harrythecake.cakeui.edit.AddedElements.TYPES) {
			String json = io.github.harrythecake.cakeui.edit.AddedElements.defaults(type, "x.png").toString();
			assertTrue(io.github.harrythecake.cakeui.model.Loader.element(layout, "+" + type, json, "hud.json5") != null, type);
		}
		assertTrue(layout.problems.isEmpty(), layout.problems.list().toString());
		assertNull(io.github.harrythecake.cakeui.model.Loader.element(layout, "+x", "{ type: 'nope' }", "hud.json5"));
		assertTrue(!layout.problems.isEmpty());
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
