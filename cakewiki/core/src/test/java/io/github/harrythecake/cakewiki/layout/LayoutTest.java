package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.Block.*;
import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Inline.InlineImage;
import io.github.harrythecake.cakewiki.doc.Inline.LineBreak;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Inline.Text;
import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.layout.Laid.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LayoutTest {
	/** 6 px per character (7 bold), 10 px lines, pictures known only if listed. */
	static final class FakeMetrics implements Metrics {
		Map<String, int[]> loaded = Map.of();

		@Override public int width(String text, Style style) { return text.length() * (style.bold() ? 7 : 6); }
		@Override public int lineHeight() { return 10; }
		@Override public int[] naturalSize(Image image) { return loaded.get(image.url()); }
		@Override public String label(String key) { return key.substring(key.lastIndexOf('.') + 1); }
	}

	private final FakeMetrics m = new FakeMetrics();

	private Laid lay(int width, ViewState state, Block... blocks) {
		return Layout.page(List.of(), new Document("T", List.of(blocks)), width, m, state);
	}

	private Laid lay(int width, Block... blocks) {
		return lay(width, new ViewState(), blocks);
	}

	private static Paragraph para(String s) {
		return new Paragraph(List.of(new Text(s, Style.PLAIN)));
	}

	private static List<Run> runs(Laid l) {
		return l.items().stream().filter(i -> i instanceof Run).map(i -> (Run) i).toList();
	}

	@Test
	void wrapsAtWordsAndMergesRunsPerLine() {
		Laid l = lay(60, para("aaaa bbbb cccc dddd"));  // 4 chars = 24 px; two words + space = 54 px
		List<Run> runs = runs(l);
		assertEquals(List.of("aaaa bbbb", "cccc dddd"), runs.stream().map(Run::text).toList());
		assertEquals(0, runs.get(0).y());
		assertEquals(10, runs.get(1).y());
		assertEquals(20, l.height());
	}

	@Test
	void noBreakWhereThereIsNoSpace() {
		// "a rare " + link "sword" + "\u00A0found it": the line must not break between "sword" and "found"
		Paragraph p = new Paragraph(List.of(new Text("a rare ", Style.PLAIN),
				new Text("sword", Style.PLAIN.withLink(new Link.Page("Swords", null))), new Text("\u00A0found it", Style.PLAIN)));
		List<Run> runs = runs(lay(90, p)); // "a rare sword" = 72 px fits, "a rare sword found" doesn't
		assertEquals("a rare", runs.get(0).text(), "the space at the end of a line is dropped");
		assertEquals(10, runs.get(1).y(), "the glued link moved to line 2 with its neighbour");
		assertEquals("sword", runs.get(1).text());
		assertEquals(" found it", runs.get(2).text(), "drawn as a plain space");
		assertEquals(0, runs.get(1).x());
	}

	@Test
	void breaksWordsWiderThanTheLine() {
		List<Run> runs = runs(lay(30, para("abcdefghijkl")));
		assertEquals(List.of("abcde", "fghij", "kl"), runs.stream().map(Run::text).toList());
	}

	@Test
	void lineBreaksAndLeadingSpaces() {
		Paragraph p = new Paragraph(List.of(new Text("one", Style.PLAIN), new LineBreak(), new Text(" two", Style.PLAIN)));
		List<Run> runs = runs(lay(200, p));
		assertEquals("two", runs.get(1).text());
		assertEquals(0, runs.get(1).x());
		assertEquals(10, runs.get(1).y());
	}

	@Test
	void iconsSitInTextAtLineHeight() {
		Image icon = new Image("u/icon.png", 16, 16, "");
		Paragraph p = new Paragraph(List.of(new Text("2 ", Style.PLAIN), new InlineImage(icon, null), new Text(" Frag", Style.PLAIN)));
		Laid l = lay(200, p);
		Pic pic = (Pic) l.items().stream().filter(i -> i instanceof Pic).findFirst().orElseThrow();
		assertEquals(9, pic.h());
		assertEquals(12, pic.x());
		assertEquals(10, l.height(), "an icon doesn't make the line taller");
	}

	@Test
	void picturesOfUnknownSizeAreFlagged() {
		Image unknown = new Image("u/x.png", 0, 0, "");
		Laid before = lay(200, new ImageBlock(unknown, List.of()));
		assertTrue(before.unknownSizes());
		Pic placeholder = (Pic) before.items().get(0);
		assertEquals(32, placeholder.w());
		m.loaded = Map.of("u/x.png", new int[] {200, 100});
		Laid after = lay(200, new ImageBlock(unknown, List.of()));
		assertFalse(after.unknownSizes());
		Pic pic = (Pic) after.items().get(0);
		assertEquals(100, pic.w());
		assertEquals(50, pic.h());
	}

	@Test
	void bigPicturesShrinkToFit() {
		Pic pic = (Pic) lay(100, new ImageBlock(new Image("u/map.png", 600, 300, ""), List.of())).items().get(0);
		assertEquals(100, pic.w());
		assertEquals(50, pic.h());
	}

	@Test
	void tableColumnsFollowContent() {
		Table t = new Table(List.of(
				new Row(List.of(cell("Item", true), cell("Tier", true))),
				new Row(List.of(cell("Moonblade", false), cell("Rare", false)))));
		Laid l = lay(400, t);
		Run item = runs(l).stream().filter(r -> r.text().equals("Moonblade")).findFirst().orElseThrow();
		Run rare = runs(l).stream().filter(r -> r.text().equals("Rare")).findFirst().orElseThrow();
		// col 0 = max("Item" bold 28, "Moonblade" 54) + 4 padding = 58; border 1 + 58 + border 1 + padding 2
		assertEquals(1 + 2, item.x());
		assertEquals(1 + 58 + 1 + 2, rare.x());
		assertTrue(runs(l).get(0).style().bold(), "header cells are bold");
		Fill border = (Fill) l.items().get(0);
		assertEquals(1 + 58 + 1 + Math.max(28, 24) + 4 + 1, border.w(), "table is as wide as its content");
	}

	@Test
	void tooWideTablesSqueezeAndWrap() {
		Table t = new Table(List.of(new Row(List.of(cell("aaaa bbbb cccc", false), cell("dddd eeee ffff", false)))));
		Laid l = lay(80, t);
		Fill border = (Fill) l.items().get(0);
		assertTrue(border.w() <= 80, "fits in the page: " + border.w());
		assertTrue(l.height() > 14, "cells wrapped onto several lines");
	}

	@Test
	void colspanAndRowspanPlaceCells() {
		Table t = new Table(List.of(
				new Row(List.of(new Cell(true, 2, 1, null, List.of(para("Wide"))))),
				new Row(List.of(new Cell(false, 1, 2, null, List.of(para("Tall"))), cell("b", false))),
				new Row(List.of(cell("c", false)))));
		Laid l = lay(400, t);
		Run tall = runs(l).stream().filter(r -> r.text().equals("Tall")).findFirst().orElseThrow();
		Run c = runs(l).stream().filter(r -> r.text().equals("c")).findFirst().orElseThrow();
		Run b = runs(l).stream().filter(r -> r.text().equals("b")).findFirst().orElseThrow();
		assertEquals(b.x(), c.x(), "row 3's only cell goes into column 2, beside the rowspan");
		assertTrue(c.x() > tall.x());
	}

	@Test
	void closedCollapsiblesAreNotLaidOutUntilOpened() {
		Collapsible c = new Collapsible(List.of(new Text("Navigation", Style.PLAIN)), true, List.of(para("hidden text")));
		ViewState state = new ViewState();
		Laid closed = lay(300, state, c);
		assertTrue(runs(closed).stream().noneMatch(r -> r.text().contains("hidden")));
		assertTrue(runs(closed).stream().anyMatch(r -> r.text().startsWith("▶")));
		Control toggle = (Control) closed.items().stream().filter(i -> i instanceof Control).findFirst().orElseThrow();
		state.apply(toggle);
		Laid open = lay(300, state, c);
		assertTrue(runs(open).stream().anyMatch(r -> r.text().equals("hidden text")));
		assertTrue(open.height() > closed.height());
	}

	@Test
	void untitledCollapsibleShowsLabel() {
		Laid l = lay(300, new Collapsible(List.of(), true, List.of(para("x"))));
		assertTrue(runs(l).stream().anyMatch(r -> r.text().contains("show")));
	}

	@Test
	void tabsShowOnlyTheSelectedPanel() {
		Tabs tabs = new Tabs(List.of(new Tab("Helmet", List.of(para("H stats"))), new Tab("Boots", List.of(para("B stats")))));
		ViewState state = new ViewState();
		assertTrue(runs(lay(300, state, tabs)).stream().anyMatch(r -> r.text().equals("H stats")));
		Laid l = lay(300, state, tabs);
		Control boots = l.items().stream().filter(i -> i instanceof Control c && c.value() == 1).map(i -> (Control) i).findFirst().orElseThrow();
		state.apply(boots);
		List<String> texts = runs(lay(300, state, tabs)).stream().map(Run::text).toList();
		assertTrue(texts.contains("B stats") && !texts.contains("H stats"));
	}

	@Test
	void infoboxPicturesAreTabsToo() {
		Infobox box = new Infobox(List.of(new Text("Blade", Style.PLAIN)),
				List.of(new ImageTab("Base", new Image("u/a.png", 128, 128, "")), new ImageTab("Hexed", new Image("u/b.png", 128, 128, ""))),
				List.of(new InfoRow(List.of(new Text("Tier", Style.PLAIN)), List.of(para("Rare")))));
		ViewState state = new ViewState();
		Laid l = lay(400, state, box);
		Pic pic = (Pic) l.items().stream().filter(i -> i instanceof Pic).findFirst().orElseThrow();
		assertEquals("u/a.png", pic.image().url());
		assertEquals(64, pic.w());
		state.select(box, 1);
		Pic hexed = (Pic) lay(400, state, box).items().stream().filter(i -> i instanceof Pic).findFirst().orElseThrow();
		assertEquals("u/b.png", hexed.image().url());
		Fill border = (Fill) l.items().get(0);
		assertEquals(Layout.INFOBOX_WIDTH, border.w());
	}

	@Test
	void infoboxFloatsRightWhenThereIsRoom() {
		Infobox box = new Infobox(List.of(new Text("Blade", Style.PLAIN)), List.of(),
				List.of(new InfoRow(List.of(new Text("Tier", Style.PLAIN)), List.of(para("Rare")))));
		Table table = new Table(List.of(new Row(List.of(cell("x", false)))));
		Laid l = lay(600, box, para("Intro text"), table);
		Fill boxBorder = (Fill) l.items().get(0);
		assertEquals(600 - Layout.INFOBOX_WIDTH, boxBorder.x(), "infobox on the right");
		Run intro = runs(l).stream().filter(r -> r.text().equals("Intro text")).findFirst().orElseThrow();
		assertEquals(0, intro.x());
		assertEquals(0, intro.y(), "text starts beside the infobox, not below it");
		Fill tableBorder = (Fill) l.items().stream().filter(i -> i instanceof Fill f && f.argb() == Layout.BORDER && f.x() == 0)
				.findFirst().orElseThrow();
		assertTrue(tableBorder.y() >= boxBorder.y() + boxBorder.h(), "tables go below the float");
		assertEquals(l.height(), tableBorder.y() + tableBorder.h());

		Laid narrow = lay(400, box, para("Intro text"));
		Run below = runs(narrow).stream().filter(r -> r.text().equals("Intro text")).findFirst().orElseThrow();
		assertTrue(below.y() > 0, "no room to float: stacked");
	}

	@Test
	void missingCellsUseCellColourNotBorder() {
		Table t = new Table(List.of(
				new Row(List.of(cell("a", true), cell("b", true), cell("c", true))),
				new Row(List.of(cell("only", false)))));
		Laid l = lay(400, t);
		long cellFills = l.items().stream().filter(i -> i instanceof Fill f && f.argb() == Layout.CELL_BG).count();
		assertEquals(3, cellFills, "one real cell + two empty slots in row 2");
	}

	@Test
	void anchorsRecordHeadingPositions() {
		Laid l = lay(300, para("intro"), new Heading(2, List.of(new Text("Stats", Style.PLAIN)), "Stats"));
		assertEquals(10 + Layout.GAP, l.anchors().get("Stats"));
	}

	@Test
	void linksAreFoundUnderTheMouse() {
		Link target = new Link.Page("Sierhaven", null);
		Paragraph p = new Paragraph(List.of(new Text("go to ", Style.PLAIN), new Text("Sierhaven", Style.PLAIN.withLink(target))));
		Laid l = lay(300, p);
		assertNull(l.linkAt(5, 5));
		assertEquals(target, l.linkAt(40, 5));
	}

	@Test
	void unreadableColoursFallBack() {
		Style dark = Style.PLAIN.withColor(0x101010);
		assertEquals(Layout.TEXT, Layout.colorFor(dark, Layout.PAGE_BG, null), "near-black text on the dark page");
		assertEquals(0xFF101010, Layout.colorFor(dark, 0xFFFFFF, null), "fine on white");
		assertEquals(0xFFFFAA00, Layout.colorFor(Style.PLAIN.withColor(0xFFAA00), Layout.PAGE_BG, null));
		assertEquals(Layout.TEXT_ON_LIGHT, Layout.colorFor(Style.PLAIN, 0xFFFFFF, null), "default text on a white cell");
		assertEquals(Layout.LINK_MISSING, Layout.colorFor(Style.PLAIN.withLink(new Link.Missing("x")), Layout.PAGE_BG, null));
	}

	@Test
	void fitColumnsStrategies() {
		assertArrayEquals(new int[] {10, 20}, Layout.fitColumns(new int[] {5, 5}, new int[] {10, 20}, 100));
		assertArrayEquals(new int[] {10, 10}, Layout.fitColumns(new int[] {10, 10}, new int[] {50, 50}, 20));
		int[] mid = Layout.fitColumns(new int[] {10, 10}, new int[] {30, 50}, 50);
		assertEquals(50, mid[0] + mid[1], 1);
	}

	private static Cell cell(String text, boolean header) {
		return new Cell(header, 1, 1, null, List.of(para(text)));
	}

	@SuppressWarnings("unused")
	private static List<Inline> line(String s) {
		return List.of(new Text(s, Style.PLAIN));
	}

	@Test
	void chineseBreaksBetweenCharactersButNotBeforePunctuation() {
		// 5 characters a line; "，" and "。" stay with the character before them
		List<Run> runs = runs(lay(30, para("由凯尔掉落的剑，很强。")));
		assertEquals(List.of("由凯尔掉落", "的剑，很", "强。"), runs.stream().map(Run::text).toList());
		assertTrue(Layout.breakBetween('剑', 'a'));
		assertFalse(Layout.breakBetween('（', '剑'));
		assertFalse(Layout.breakBetween('a', 'b'), "English still breaks only at spaces");
	}

	@Test
	void translationHookSkipsHeaderAndInfoboxTitle() {
		java.util.function.UnaryOperator<List<Inline>> upper = l -> l.stream()
				.map(i -> i instanceof Text t ? (Inline) new Text(t.text().toUpperCase(), t.style()) : i).toList();
		Infobox box = new Infobox(List.of(new Text("Blade", Style.PLAIN)), List.of(),
				List.of(new InfoRow(List.of(new Text("Tier", Style.PLAIN)), List.of(para("Rare")))));
		Laid l = Layout.page(List.of(para("Page title")), new Document("T", List.of(box, para("intro"))), 200, m,
				new ViewState(), upper);
		List<String> texts = runs(l).stream().map(Run::text).toList();
		assertTrue(texts.contains("Page title"), texts.toString());
		assertTrue(texts.contains("Blade"), texts.toString());
		assertTrue(texts.contains("TIER") && texts.contains("RARE") && texts.contains("INTRO"), texts.toString());
	}
}
