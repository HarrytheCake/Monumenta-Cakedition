package io.github.harrythecake.cakewiki.doc;

import java.util.List;

/** One block of a wiki page, in reading order. The renderer lays these out top to bottom. */
public sealed interface Block {
	/** {@code level} 1–6 as in HTML; {@code anchor} is the id that {@code #fragment} links point at, or null. */
	record Heading(int level, List<Inline> text, String anchor) implements Block {}

	record Paragraph(List<Inline> text) implements Block {}

	/** Each item is its own list of blocks, so nested lists stay nested. */
	record ListBlock(boolean ordered, List<List<Block>> items) implements Block {}

	record ImageBlock(Image image, List<Inline> caption) implements Block {}

	/** Pictures laid out side by side, wrapping (MediaWiki {@code <gallery>}). */
	record Gallery(List<ImageBlock> images) implements Block {}

	record Table(List<Row> rows) implements Block {}

	record Row(List<Cell> cells) {}

	/** {@code background} is 0xRRGGBB or null for none. */
	record Cell(boolean header, int colspan, int rowspan, Integer background, List<Block> content) {}

	/** Portable Infobox: the item/mob/NPC stats box at the top of most pages. */
	record Infobox(List<Inline> title, List<ImageTab> images, List<InfoItem> items) implements Block {}

	/** One picture in the infobox; {@code label} is null when the infobox has a single picture. */
	record ImageTab(String label, Image image) {}

	sealed interface InfoItem {}

	record InfoRow(List<Inline> label, List<Block> value) implements InfoItem {}

	record InfoHeader(List<Inline> text) implements InfoItem {}

	/** A section the reader can open and close. An empty {@code title} means the renderer shows its own "Show" label. */
	record Collapsible(List<Inline> title, boolean collapsed, List<Block> content) implements Block {}

	/** TabberNeue tabs: one panel shown at a time. */
	record Tabs(List<Tab> tabs) implements Block {}

	record Tab(String label, List<Block> content) {}

	/** Indented content ({@code dd}, {@code blockquote}). */
	record Indented(List<Block> content) implements Block {}

	record Rule() implements Block {}
}
