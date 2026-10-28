package io.github.harrythecake.cakebook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTest {
	private static List<Markdown.Kind> kinds(Markdown.Doc doc) {
		return doc.blocks().stream().map(Markdown.Block::kind).toList();
	}

	@Test
	void blocks() {
		Markdown.Doc doc = Markdown.parse("""
				# Title

				First line
				same paragraph.

				## Section
				- one
				- two
				  - nested
				1. first
				> a tip
				> goes on

				---
				```
				code  line
				```
				![Map|120](../images/map.png)
				| Key | Does |
				|-----|------|
				| F | Swap |
				""");
		assertEquals(List.of(Markdown.Kind.HEADING, Markdown.Kind.PARAGRAPH, Markdown.Kind.HEADING, Markdown.Kind.ITEM,
				Markdown.Kind.ITEM, Markdown.Kind.ITEM, Markdown.Kind.ITEM, Markdown.Kind.QUOTE, Markdown.Kind.RULE,
				Markdown.Kind.CODE, Markdown.Kind.IMAGE, Markdown.Kind.PARAGRAPH, Markdown.Kind.PARAGRAPH), kinds(doc));
		List<Markdown.Block> b = doc.blocks();
		assertEquals("First line same paragraph.", b.get(1).plainText());
		assertEquals(2, b.get(2).level());
		assertEquals(1, b.get(5).level());
		assertEquals("1.", b.get(6).marker());
		assertEquals("a tip goes on", b.get(7).plainText());
		assertEquals("code  line", b.get(9).plainText());
		assertEquals("../images/map.png", b.get(10).src());
		assertEquals(120, b.get(10).width());
		assertEquals("Map", b.get(10).plainText());
		assertEquals("Key  ·  Does", b.get(11).plainText());
		assertEquals("Title", doc.title());
	}

	@Test
	void frontMatterGivesTitleOrderAndHidden() {
		Markdown.Doc doc = Markdown.parse("""
				---
				title: "Sodium: settings"
				order: 5
				hidden: true
				---
				# Not the title
				""");
		assertEquals("Sodium: settings", doc.title());
		assertEquals(5, doc.order());
		assertTrue(doc.hidden());
		assertEquals(Markdown.DEFAULT_ORDER, Markdown.parse("text").order());
		assertNull(Markdown.parse("no heading").title());
	}

	@Test
	void chineseLinesJoinWithoutSpaces() {
		assertEquals("第一行第二行", Markdown.parse("第一行\n第二行").blocks().get(0).plainText());
		assertEquals("Press 键", Markdown.parse("Press\n键").blocks().get(0).plainText());
	}

	@Test
	void setextHeadingsAndHardBreaks() {
		Markdown.Doc doc = Markdown.parse("Big\n===\nSmall\n---\nline one  \nline two");
		assertEquals(Markdown.Kind.HEADING, doc.blocks().get(0).kind());
		assertEquals(1, doc.blocks().get(0).level());
		assertEquals(2, doc.blocks().get(1).level());
		assertEquals("line one\nline two", doc.blocks().get(2).plainText());
	}

	@Test
	void inlineStyles() {
		List<Markdown.Span> spans = Markdown.inline("a **bold** *it* ~~old~~ `code` \\*not\\*");
		assertEquals("a ", spans.get(0).text());
		assertEquals(new Markdown.Span("bold", Markdown.BOLD), spans.get(1));
		assertEquals(new Markdown.Span("it", Markdown.ITALIC), spans.get(3));
		assertEquals(new Markdown.Span("old", Markdown.STRIKE), spans.get(5));
		assertEquals(new Markdown.Span("code", Markdown.CODE), spans.get(7));
		assertEquals(" *not*", spans.get(8).text());
	}

	@Test
	void doubleBacktickCodeHoldsBackticks() {
		assertEquals(List.of(new Markdown.Span("write ", 0), new Markdown.Span("`code`", Markdown.CODE)),
				Markdown.inline("write `` `code` ``"));
	}

	@Test
	void unclosedMarkersStayText() {
		assertEquals(List.of(new Markdown.Span("2 * 3 = 6 and **open", 0)), Markdown.inline("2 * 3 = 6 and **open"));
	}

	@Test
	void linksKeysAndBareUrls() {
		List<Markdown.Span> spans = Markdown.inline("See [the **map**](mods/xaero.md), press {key:key.inventory} or https://example.org.");
		assertEquals(new Markdown.Span("the ", 0, "mods/xaero.md", null), spans.get(1));
		assertEquals(new Markdown.Span("map", Markdown.BOLD, "mods/xaero.md", null), spans.get(2));
		assertEquals("key.inventory", spans.get(4).key());
		assertEquals(new Markdown.Span("https://example.org", 0, "https://example.org", null), spans.get(6));
		assertEquals(".", spans.get(7).text());
	}

	@Test
	void plainTextSkipsMarkup() {
		Markdown.Doc doc = Markdown.parse("# Hi\n\nSome **bold** [link](x)\n\n---\n");
		assertEquals("Hi\nSome bold link\n", doc.plainText());
	}
}
