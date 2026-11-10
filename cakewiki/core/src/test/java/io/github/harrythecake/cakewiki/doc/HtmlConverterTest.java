package io.github.harrythecake.cakewiki.doc;

import io.github.harrythecake.cakewiki.doc.Block.*;
import io.github.harrythecake.cakewiki.doc.Inline.InlineImage;
import io.github.harrythecake.cakewiki.doc.Inline.LineBreak;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Inline.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Hand-written HTML in the shapes monumenta.wiki.gg returns (no real wiki text in the repo). */
class HtmlConverterTest {
	private static final String BASE = "https://monumenta.wiki.gg";
	private final HtmlConverter converter = new HtmlConverter(BASE);

	private List<Block> convert(String inner) {
		return converter.convert("Test", "<div class=\"mw-content-ltr mw-parser-output\" lang=\"en\">" + inner + "</div>").blocks();
	}

	private static String plain(List<Inline> line) {
		return line.stream().map(i -> i instanceof Text t ? t.text() : i instanceof LineBreak ? "\n" : "[img]")
				.collect(Collectors.joining());
	}

	@Test
	void paragraphsCollapseWhitespaceAndDropEmptyOnes() {
		List<Block> blocks = convert("<p class=\"mw-empty-elt\"></p><p>  Hello \n  <b>bold</b>   world  </p><p> </p>");
		assertEquals(1, blocks.size());
		Paragraph p = (Paragraph) blocks.get(0);
		assertEquals("Hello bold world", plain(p.text()));
		assertTrue(((Text) p.text().get(1)).style().bold());
	}

	@Test
	void headingKeepsAnchorAndDropsEditLinks() {
		List<Block> blocks = convert("<h2><span class=\"mw-headline\" id=\"Drop_Locations\">Drop Locations</span>"
				+ "<span class=\"mw-editsection\">[edit]</span></h2>");
		Heading h = (Heading) blocks.get(0);
		assertEquals(2, h.level());
		assertEquals("Drop_Locations", h.anchor());
		assertEquals("Drop Locations", plain(h.text()));
	}

	@Test
	void linksAreClassified() {
		Paragraph p = (Paragraph) convert("<p>"
				+ "<a href=\"/wiki/King%27s_Valley\" title=\"King's Valley\">a</a> "
				+ "<a href=\"/wiki/Fire_Protection#Levels\">b</a> "
				+ "<a href=\"#Stats\">c</a> "
				+ "<a href=\"/index.php?title=Nope&amp;action=edit&amp;redlink=1\" class=\"new\" title=\"Nope (page does not exist)\">d</a> "
				+ "<a class=\"external text\" href=\"https://www.playmonumenta.com/\">e</a> "
				+ "<a href=\"https://monumenta.wiki.gg/wiki/C%2B%2B_Item\">f</a>"
				+ "</p>").get(0);
		List<Link> links = p.text().stream().filter(i -> i instanceof Text t && t.style().link() != null)
				.map(i -> ((Text) i).style().link()).toList();
		assertEquals(List.of(
				new Link.Page("King's Valley", null),
				new Link.Page("Fire Protection", "Levels"),
				new Link.Page("", "Stats"),
				new Link.Missing("Nope"),
				new Link.External("https://www.playmonumenta.com/"),
				new Link.Page("C++ Item", null)), links);
	}

	@Test
	void inlineColoursFromStyleAndFont() {
		Paragraph p = (Paragraph) convert("<p><span style=\"color:#FFAA00\">Rare</span>"
				+ "<span style=\"color: rgb(85, 255, 85);\">Green</span><font color=\"red\">Red</font>"
				+ "<span style=\"white-space:nowrap\">plain</span></p>").get(0);
		List<Integer> colors = p.text().stream().map(i -> ((Text) i).style().color()).toList();
		assertEquals(java.util.Arrays.asList(0xFFAA00, 0x55FF55, 0xFF0000, null), colors);
	}

	@Test
	void imagesBecomeAbsoluteAndGifIsAnimated() {
		Paragraph p = (Paragraph) convert("<p>Icon <a href=\"/wiki/File:X.gif\" class=\"image\">"
				+ "<img src=\"/images/X.gif?abc\" width=\"16\" height=\"16\" alt=\"X\"/></a> after</p>").get(0);
		InlineImage img = (InlineImage) p.text().get(1);
		assertEquals("https://monumenta.wiki.gg/images/X.gif?abc", img.image().url());
		assertEquals(16, img.image().width());
		assertTrue(img.image().animated());
		assertNull(img.link(), "File: page links are dropped");
		assertEquals("Icon [img] after", plain(p.text()));
	}

	@Test
	void nestedListsStayNested() {
		ListBlock list = (ListBlock) convert("<ul><li>One<ul><li>One.A</li></ul></li><li>Two</li></ul>").get(0);
		assertFalse(list.ordered());
		assertEquals(2, list.items().size());
		List<Block> first = list.items().get(0);
		assertEquals("One", plain(((Paragraph) first.get(0)).text()));
		assertInstanceOf(ListBlock.class, first.get(1));
	}

	@Test
	void wikitableWithHeadersSpansAndBackgrounds() {
		Table t = (Table) convert("<table class=\"wikitable\"><tbody>"
				+ "<tr><th>Item</th><th colspan=\"2\">Stats</th></tr>"
				+ "<tr style=\"background:#191E3E\"><td>Blade</td><td rowspan=\"2\">+7%</td><td style=\"background-color:#fff\">x</td></tr>"
				+ "</tbody></table>").get(0);
		assertEquals(2, t.rows().size());
		Cell head = t.rows().get(0).cells().get(1);
		assertTrue(head.header());
		assertEquals(2, head.colspan());
		List<Cell> row = t.rows().get(1).cells();
		assertEquals(0x191E3E, row.get(0).background());
		assertEquals(2, row.get(1).rowspan());
		assertEquals(0xFFFFFF, row.get(2).background());
	}

	@Test
	void nestedTableRowsStayInTheirCell() {
		Table t = (Table) convert("<table><tr><td><table><tr><td>inner</td></tr></table></td></tr></table>").get(0);
		assertEquals(1, t.rows().size());
		assertInstanceOf(Table.class, t.rows().get(0).cells().get(0).content().get(0));
	}

	@Test
	void infoboxWithImageTabsRowsAndGroups() {
		Infobox box = (Infobox) convert("<aside class=\"portable-infobox pi-background\">"
				+ "<h2 class=\"pi-item pi-title\">Test Blade</h2>"
				+ "<div class=\"pi-media-collection\"><ul class=\"pi-media-collection-tabs\">"
				+ "<li class=\"pi-tab-link current\">Base</li><li class=\"pi-tab-link\">Hexed</li></ul>"
				+ "<div class=\"pi-media-collection-tab-content current\"><figure class=\"pi-item pi-image\"><a><img src=\"/images/A.png\"/></a></figure></div>"
				+ "<div class=\"pi-media-collection-tab-content\"><figure class=\"pi-item pi-image\"><a><img src=\"/images/B.gif\"/></a></figure></div></div>"
				+ "<div class=\"pi-item pi-data\"><h3 class=\"pi-data-label\">Tier</h3><div class=\"pi-data-value\">Rare</div></div>"
				+ "<section class=\"pi-item pi-group\"><h2 class=\"pi-item pi-header\">Stats</h2>"
				+ "<div class=\"pi-item pi-data\"><h3 class=\"pi-data-label\">Damage</h3><div class=\"pi-data-value\"><p>10</p></div></div></section>"
				+ "</aside><p>After</p>").get(0);
		assertEquals("Test Blade", plain(box.title()));
		assertEquals(2, box.images().size());
		assertEquals("Hexed", box.images().get(1).label());
		assertTrue(box.images().get(1).image().animated());
		assertEquals(3, box.items().size());
		InfoRow tier = (InfoRow) box.items().get(0);
		assertEquals("Tier", plain(tier.label()));
		assertEquals("Rare", plain(((Paragraph) tier.value().get(0)).text()));
		assertEquals("Stats", plain(((InfoHeader) box.items().get(1)).text()));
	}

	@Test
	void collapsibleDivUsesContentAndTitle() {
		List<Block> blocks = convert("<div class=\"mw-collapsible mw-collapsed\"><b>Old stats</b>"
				+ "<div class=\"mw-collapsible-content\"><p>Hidden</p></div></div>"
				+ "<div class=\"mw-collapsible\"><p>No title</p></div>");
		Collapsible c = (Collapsible) blocks.get(0);
		assertTrue(c.collapsed());
		assertEquals("Old stats", plain(c.title()));
		assertEquals("Hidden", plain(((Paragraph) c.content().get(0)).text()));
		Collapsible open = (Collapsible) blocks.get(1);
		assertFalse(open.collapsed());
		assertTrue(open.title().isEmpty());
		assertEquals(1, open.content().size());
	}

	@Test
	void collapsibleTableKeepsFirstRowAsTitle() {
		Collapsible c = (Collapsible) convert("<table class=\"mw-collapsible mw-collapsed\"><tbody>"
				+ "<tr><th>Navigation</th></tr><tr><td>Row 1</td></tr><tr><td>Row 2</td></tr></tbody></table>").get(0);
		assertEquals("Navigation", plain(c.title()));
		assertEquals(2, ((Table) c.content().get(0)).rows().size());
	}

	@Test
	void tabberPanelsGetTheirLabels() {
		Tabs tabs = (Tabs) convert("<div class=\"tabber tabber--init\"><header class=\"tabber__header\">"
				+ "<button class=\"tabber__header__prev\"></button><nav class=\"tabber__tabs\">"
				+ "<a class=\"tabber__tab\" id=\"Helmet-0-label\" href=\"#Helmet-0\">Helmet</a>"
				+ "<a class=\"tabber__tab\" id=\"Boots-0-label\" href=\"#Boots-0\">Boots</a></nav></header>"
				+ "<section class=\"tabber__section\">"
				+ "<article class=\"tabber__panel\" id=\"Helmet-0\" aria-labelledby=\"Helmet-0-label\"><p>H</p></article>"
				+ "<article class=\"tabber__panel\" id=\"Boots-0\" aria-labelledby=\"Boots-0-label\"><p>B</p></article>"
				+ "</section></div>").get(0);
		assertEquals(List.of("Helmet", "Boots"), tabs.tabs().stream().map(Tab::label).toList());
		assertEquals("B", plain(((Paragraph) tabs.tabs().get(1).content().get(0)).text()));
	}

	@Test
	void thumbnailAndGallery() {
		List<Block> blocks = convert("<div class=\"thumb tright\"><div class=\"thumbinner\"><a class=\"image\">"
				+ "<img src=\"/images/Map.png\" width=\"300\" height=\"200\"/></a>"
				+ "<div class=\"thumbcaption\"><div class=\"magnify\"><a>m</a></div>The map</div></div></div>"
				+ "<ul class=\"gallery mw-gallery-nolines\"><li class=\"gallerybox\"><div class=\"thumb\"><img src=\"/images/1.png\"/></div>"
				+ "<div class=\"gallerytext\">One</div></li><li class=\"gallerybox\"><img src=\"/images/2.png\"/></li></ul>");
		ImageBlock thumb = (ImageBlock) blocks.get(0);
		assertEquals(300, thumb.image().width());
		assertEquals("The map", plain(thumb.caption()));
		Gallery g = (Gallery) blocks.get(1);
		assertEquals(2, g.images().size());
		assertEquals("One", plain(g.images().get(0).caption()));
	}

	@Test
	void skippedElementsLeaveNoTrace() {
		List<Block> blocks = convert("<div id=\"toc\" class=\"toc\"><ul><li>1 Intro</li></ul></div>"
				+ "<style>.x{}</style><script>x()</script><div style=\"display: none\">hidden</div>"
				+ "<div class=\"embedvideo\"><button>Load video</button></div><p>Kept</p>");
		assertEquals(1, blocks.size());
		assertEquals("Kept", plain(((Paragraph) blocks.get(0)).text()));
	}

	@Test
	void textDirectlyInDivsBecomesParagraphs() {
		List<Block> blocks = convert("<div class=\"mp-box\"><div class=\"mp-heading\">Welcome</div>"
				+ "<div class=\"mp-body\">Line one<br>Line two</div></div>");
		assertEquals(2, blocks.size());
		assertEquals("Line one\nLine two", plain(((Paragraph) blocks.get(1)).text()));
	}

	@Test
	void definitionListsIndent() {
		List<Block> blocks = convert("<dl><dt>Term</dt><dd>Meaning</dd></dl>");
		assertTrue(((Text) ((Paragraph) blocks.get(0)).text().get(0)).style().bold());
		assertInstanceOf(Indented.class, blocks.get(1));
	}

	@Test
	void styleOnParentsCarriesDown() {
		Paragraph p = (Paragraph) convert("<div style=\"color:#55FFFF; font-weight: bold\"><i>x</i></div>").get(0);
		Style s = ((Text) p.text().get(0)).style();
		assertEquals(0x55FFFF, s.color());
		assertTrue(s.bold() && s.italic());
	}
}
