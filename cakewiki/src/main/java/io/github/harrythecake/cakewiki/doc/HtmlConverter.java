package io.github.harrythecake.cakewiki.doc;

import io.github.harrythecake.cakewiki.doc.Block.*;
import io.github.harrythecake.cakewiki.doc.Inline.InlineImage;
import io.github.harrythecake.cakewiki.doc.Inline.LineBreak;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Inline.Text;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the HTML that MediaWiki's {@code action=parse} returns into a {@link Document}.
 * Recognises the structures the Monumenta Wiki uses (Portable Infobox, TabberNeue, collapsibles, wikitables,
 * galleries, thumbnails); anything else keeps its text.
 */
public final class HtmlConverter {
	private static final Set<String> SKIP_TAGS = Set.of("script", "style", "link", "meta", "noscript", "input",
			"button", "template", "iframe", "video", "audio");
	private static final Set<String> SKIP_CLASSES = Set.of("mw-editsection", "toc", "noprint", "magnify",
			"embedvideo", "mw-empty-elt", "tabber__header");
	private static final Set<String> BLOCK_TAGS = Set.of("p", "div", "section", "article", "aside", "figure",
			"center", "table", "ul", "ol", "dl", "blockquote", "hr", "pre", "h1", "h2", "h3", "h4", "h5", "h6",
			"header", "footer", "nav", "main");

	private final String base;

	/** {@code base} is the wiki's origin without a trailing slash, e.g. {@code https://monumenta.wiki.gg}. */
	public HtmlConverter(String base) {
		this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	public Document convert(String title, String html) {
		Element body = Jsoup.parseBodyFragment(html).body();
		Element root = body.selectFirst(".mw-parser-output");
		return new Document(title, blocks(root != null ? root : body, Style.PLAIN));
	}

	// ---- blocks -------------------------------------------------------------------------------------------

	private List<Block> blocks(Element container, Style style) {
		Out out = new Out();
		for (Node child : container.childNodes()) node(child, out, style);
		out.flush();
		return out.blocks;
	}

	private void node(Node node, Out out, Style style) {
		if (node instanceof TextNode text) {
			out.text(text.getWholeText(), style);
			return;
		}
		if (!(node instanceof Element e) || skipped(e)) return;
		Style s = styled(e, style);
		String tag = e.normalName();
		switch (tag) {
			case "br" -> out.inline(new LineBreak());
			case "img" -> {
				Image img = image(e);
				if (img != null) out.inline(new InlineImage(img, null));
			}
			case "a" -> {
				Element img = e.selectFirst("img");
				if (img != null && e.text().isBlank()) {
					Image i = image(img);
					// links around pictures mostly go to the File: page, which the reader doesn't need
					if (i != null) out.inline(new InlineImage(i, e.hasClass("image") ? null : link(e)));
				} else {
					Link l = link(e);
					children(e, out, l != null ? s.withLink(l) : s);
				}
			}
			case "b", "strong" -> children(e, out, s.withBold());
			case "i", "em", "cite" -> children(e, out, s.withItalic());
			case "u", "ins" -> children(e, out, s.withUnderline());
			case "s", "strike", "del" -> children(e, out, s.withStrikethrough());
			case "p" -> {
				out.flush();
				children(e, out, s);
				out.flush();
			}
			case "h1", "h2", "h3", "h4", "h5", "h6" -> {
				Element headline = e.selectFirst(".mw-headline");
				String anchor = headline != null && !headline.id().isEmpty() ? headline.id()
						: e.id().isEmpty() ? null : e.id();
				out.block(new Heading(tag.charAt(1) - '0', inlines(e, s), anchor));
			}
			case "ul", "ol" -> {
				if (e.hasClass("gallery")) {
					gallery(e, out);
				} else {
					List<List<Block>> items = new ArrayList<>();
					for (Element li : e.children()) {
						if (li.normalName().equals("li") && !skipped(li)) items.add(blocks(li, styled(li, s)));
					}
					if (!items.isEmpty()) out.block(new ListBlock(tag.equals("ol"), items));
				}
			}
			case "dl" -> {
				for (Element item : e.children()) {
					if (skipped(item)) continue;
					if (item.normalName().equals("dt")) {
						List<Inline> term = inlines(item, styled(item, s).withBold());
						if (!term.isEmpty()) out.block(new Paragraph(term));
					} else {
						List<Block> content = blocks(item, styled(item, s));
						if (!content.isEmpty()) out.block(new Indented(content));
					}
				}
			}
			case "blockquote" -> {
				List<Block> content = blocks(e, s);
				if (!content.isEmpty()) out.block(new Indented(content));
			}
			case "hr" -> out.block(new Rule());
			case "pre" -> {
				out.flush();
				String[] lines = e.wholeText().split("\n", -1);
				for (int i = 0; i < lines.length; i++) {
					if (i > 0) out.inline(new LineBreak());
					out.raw(lines[i], s);
				}
				out.flush();
			}
			case "table" -> {
				if (e.hasClass("mw-collapsible")) {
					collapsibleTable(e, out, s);
				} else {
					List<Row> rows = rows(e, s);
					if (!rows.isEmpty()) out.block(new Table(rows));
				}
			}
			case "aside" -> {
				if (e.hasClass("portable-infobox")) infobox(e, out, s);
				else container(e, out, s);
			}
			case "figure" -> figure(e, out, s);
			default -> {
				if (e.hasClass("mw-collapsible")) collapsibleDiv(e, out, s);
				else if (e.hasClass("tabber")) tabber(e, out, s);
				else if (e.hasClass("thumb")) figure(e, out, s);
				else if (BLOCK_TAGS.contains(tag)) container(e, out, s);
				else children(e, out, s); // span, font, small, big, sup, code, …: text with its style
			}
		}
	}

	private void children(Element e, Out out, Style style) {
		for (Node child : e.childNodes()) node(child, out, style);
	}

	private void container(Element e, Out out, Style style) {
		out.flush();
		children(e, out, style);
		out.flush();
	}

	private void figure(Element e, Out out, Style style) {
		Element imgEl = e.selectFirst("img");
		Image img = imgEl != null ? image(imgEl) : null;
		if (img == null) {
			container(e, out, style);
			return;
		}
		Element caption = e.selectFirst("figcaption, .thumbcaption");
		out.block(new ImageBlock(img, caption != null ? inlines(caption, style) : List.of()));
	}

	private void gallery(Element e, Out out) {
		List<ImageBlock> images = new ArrayList<>();
		for (Element box : e.select("li.gallerybox")) {
			Element imgEl = box.selectFirst("img");
			Image img = imgEl != null ? image(imgEl) : null;
			if (img == null) continue;
			Element text = box.selectFirst(".gallerytext");
			images.add(new ImageBlock(img, text != null ? inlines(text, Style.PLAIN) : List.of()));
		}
		if (!images.isEmpty()) out.block(new Gallery(images));
	}

	private List<Row> rows(Element table, Style style) {
		List<Row> rows = new ArrayList<>();
		for (Element tr : rowElements(table)) {
			Style rowStyle = styled(tr, style);
			Integer rowBackground = background(tr);
			List<Cell> cells = new ArrayList<>();
			for (Element cell : tr.children()) {
				String t = cell.normalName();
				if (!t.equals("td") && !t.equals("th") || skipped(cell)) continue;
				Integer bg = background(cell);
				cells.add(new Cell(t.equals("th"), span(cell, "colspan"), span(cell, "rowspan"),
						bg != null ? bg : rowBackground, blocks(cell, styled(cell, rowStyle))));
			}
			if (!cells.isEmpty()) rows.add(new Row(cells));
		}
		return rows;
	}

	/** The table's own rows, not those of tables nested in its cells. */
	private static List<Element> rowElements(Element table) {
		List<Element> trs = new ArrayList<>();
		for (Element child : table.children()) {
			switch (child.normalName()) {
				case "tr" -> trs.add(child);
				case "thead", "tbody", "tfoot" -> {
					for (Element tr : child.children()) if (tr.normalName().equals("tr")) trs.add(tr);
				}
				default -> {}
			}
		}
		return trs;
	}

	/** A collapsible table keeps its first row visible as the title; the rest is the hidden content. */
	private void collapsibleTable(Element e, Out out, Style style) {
		List<Element> trs = rowElements(e);
		if (trs.isEmpty()) return;
		List<Inline> title = inlines(trs.get(0), styled(trs.get(0), style));
		Element rest = e.shallowClone();
		for (int i = 1; i < trs.size(); i++) rest.appendChild(trs.get(i).clone());
		List<Row> rows = rows(rest, style);
		out.block(new Collapsible(title, e.hasClass("mw-collapsed"),
				rows.isEmpty() ? List.of() : List.of(new Table(rows))));
	}

	/** A collapsible div hides its {@code .mw-collapsible-content}; anything outside that is the title. */
	private void collapsibleDiv(Element e, Out out, Style style) {
		Element content = null;
		for (Element child : e.children()) {
			if (child.hasClass("mw-collapsible-content")) { content = child; break; }
		}
		List<Inline> title = List.of();
		if (content != null) {
			Element head = e.shallowClone();
			for (Node n : e.childNodes()) if (n != content) head.appendChild(n.clone());
			title = inlines(head, style);
		}
		out.block(new Collapsible(title, e.hasClass("mw-collapsed"), blocks(content != null ? content : e, style)));
	}

	private void tabber(Element e, Out out, Style style) {
		List<Element> labels = e.select(".tabber__tab");
		List<Tab> tabs = new ArrayList<>();
		List<Element> panels = e.select(".tabber__panel");
		for (int i = 0; i < panels.size(); i++) {
			Element panel = panels.get(i);
			if (panel.parents().stream().filter(p -> p.hasClass("tabber")).findFirst().orElse(null) != e) continue;
			String label = panel.attr("data-mw-tab-title");
			if (label.isEmpty()) label = panel.attr("data-title");
			if (label.isEmpty()) {
				String by = panel.attr("aria-labelledby");
				Element labelEl = by.isEmpty() ? null : e.getElementById(by);
				label = labelEl != null ? labelEl.text() : i < labels.size() ? labels.get(i).text() : String.valueOf(i + 1);
			}
			tabs.add(new Tab(label, blocks(panel, style)));
		}
		if (!tabs.isEmpty()) out.block(new Tabs(tabs));
	}

	private void infobox(Element aside, Out out, Style style) {
		Element titleEl = aside.selectFirst(".pi-title");
		List<Inline> title = titleEl != null ? inlines(titleEl, style) : List.of();
		List<ImageTab> images = new ArrayList<>();
		List<InfoItem> items = new ArrayList<>();
		infoboxItems(aside, images, items, style);
		out.block(new Infobox(title, images, items));
	}

	private void infoboxItems(Element parent, List<ImageTab> images, List<InfoItem> items, Style style) {
		for (Element el : parent.children()) {
			if (skipped(el) || el.hasClass("pi-title")) continue;
			if (el.hasClass("pi-media-collection")) {
				List<Element> labels = el.select(".pi-tab-link");
				List<Element> contents = el.select(".pi-media-collection-tab-content");
				for (int i = 0; i < contents.size(); i++) {
					Element imgEl = contents.get(i).selectFirst("img");
					Image img = imgEl != null ? image(imgEl) : null;
					if (img != null) images.add(new ImageTab(i < labels.size() ? labels.get(i).text() : null, img));
				}
			} else if (el.hasClass("pi-image")) {
				Element imgEl = el.selectFirst("img");
				Image img = imgEl != null ? image(imgEl) : null;
				if (img != null) images.add(new ImageTab(null, img));
			} else if (el.hasClass("pi-header")) {
				items.add(new InfoHeader(inlines(el, style)));
			} else if (el.hasClass("pi-data")) {
				Element label = el.selectFirst(".pi-data-label");
				Element value = el.selectFirst(".pi-data-value");
				items.add(new InfoRow(label != null ? inlines(label, style) : List.of(),
						value != null ? blocks(value, style) : List.of()));
			} else if (el.hasClass("pi-horizontal-group")) {
				// a small table: labels in the head row, values below, one pair per column
				List<Element> heads = el.select("th.pi-horizontal-group-item");
				List<Element> values = el.select("td.pi-horizontal-group-item");
				Element caption = el.selectFirst("caption");
				if (caption != null) items.add(new InfoHeader(inlines(caption, style)));
				for (int i = 0; i < values.size(); i++) {
					items.add(new InfoRow(i < heads.size() ? inlines(heads.get(i), style) : List.of(),
							blocks(values.get(i), style)));
				}
			} else {
				infoboxItems(el, images, items, style); // section.pi-group and other wrappers
			}
		}
	}

	// ---- inline pieces ------------------------------------------------------------------------------------

	/** Everything inside {@code e} as one line of text: block structure becomes spaces. */
	private List<Inline> inlines(Element e, Style style) {
		List<Inline> line = new ArrayList<>();
		flatten(blocks(e, style), line);
		return line;
	}

	private static void flatten(List<Block> blocks, List<Inline> line) {
		for (Block b : blocks) {
			List<Inline> part = new ArrayList<>();
			if (b instanceof Paragraph p) part.addAll(p.text());
			else if (b instanceof Heading h) part.addAll(h.text());
			else if (b instanceof ListBlock l) l.items().forEach(item -> flatten(item, part));
			else if (b instanceof Indented i) flatten(i.content(), part);
			else if (b instanceof Collapsible c) { part.addAll(c.title()); flatten(c.content(), part); }
			else if (b instanceof Table t) t.rows().forEach(r -> r.cells().forEach(c -> flatten(c.content(), part)));
			else if (b instanceof ImageBlock i) part.add(new InlineImage(i.image(), null));
			if (part.isEmpty()) continue;
			if (!line.isEmpty()) line.add(new Text(" ", Style.PLAIN));
			line.addAll(part);
		}
	}

	private Image image(Element img) {
		String src = img.attr("src");
		if (src.isEmpty() || src.startsWith("data:")) src = img.attr("data-src");
		if (src.isEmpty() || src.startsWith("data:")) return null;
		return new Image(absolute(src), number(img.attr("width")), number(img.attr("height")), img.attr("alt"));
	}

	private Link link(Element a) {
		String href = a.attr("href");
		if (href.isEmpty()) return null;
		if (a.hasClass("new")) {
			String t = a.attr("title").replace(" (page does not exist)", "");
			return new Link.Missing(t);
		}
		if (href.startsWith("#")) return new Link.Page("", decode(href.substring(1)));
		String url = absolute(href);
		if (url.startsWith(base + "/wiki/")) {
			String path = url.substring((base + "/wiki/").length());
			int hash = path.indexOf('#');
			String anchor = hash >= 0 ? decode(path.substring(hash + 1)) : null;
			if (hash >= 0) path = path.substring(0, hash);
			return new Link.Page(decode(path).replace('_', ' '), anchor);
		}
		return url.startsWith("http://") || url.startsWith("https://") ? new Link.External(url) : null;
	}

	private String absolute(String src) {
		if (src.startsWith("//")) return "https:" + src;
		if (src.startsWith("/")) return base + src;
		return src;
	}

	private static String decode(String s) {
		try {
			return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return s; // a stray % that isn't an escape
		}
	}

	private static int number(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int span(Element cell, String attr) {
		return Math.max(1, Math.min(number(cell.attr(attr)), 100));
	}

	private static Integer background(Element e) {
		Integer c = CssColor.fromStyle(e.attr("style"), "background-color");
		return c != null ? c : CssColor.parse(e.attr("bgcolor"));
	}

	private static boolean skipped(Element e) {
		if (SKIP_TAGS.contains(e.normalName())) return true;
		if (e.id().equals("toc")) return true;
		for (String c : e.classNames()) if (SKIP_CLASSES.contains(c)) return true;
		String style = e.attr("style").replace(" ", "").toLowerCase(Locale.ROOT);
		return style.contains("display:none");
	}

	private static Style styled(Element e, Style style) {
		String css = e.attr("style");
		Integer color = CssColor.fromStyle(css, "color");
		if (color == null && e.normalName().equals("font")) color = CssColor.parse(e.attr("color"));
		Style s = color != null ? style.withColor(color) : style;
		if (css.isEmpty()) return s;
		String c = css.replace(" ", "").toLowerCase(Locale.ROOT);
		if (c.contains("font-weight:bold") || c.contains("font-weight:700")) s = s.withBold();
		if (c.contains("font-style:italic")) s = s.withItalic();
		if (c.contains("text-decoration:underline")) s = s.withUnderline();
		if (c.contains("text-decoration:line-through")) s = s.withStrikethrough();
		return s;
	}

	/** Collects inline runs into paragraphs, with HTML's whitespace rules (runs collapse; trimmed at line ends). */
	private static final class Out {
		final List<Block> blocks = new ArrayList<>();
		private List<Inline> line = new ArrayList<>();

		void text(String raw, Style style) {
			raw(raw.replaceAll("[ \\t\\n\\r\\f]+", " "), style);
		}

		void raw(String t, Style style) {
			Inline last = line.isEmpty() ? null : line.get(line.size() - 1);
			boolean lineStart = last == null || last instanceof LineBreak;
			if (lineStart || last instanceof Text lt && lt.text().endsWith(" ")) t = stripLeadingSpace(t);
			if (t.isEmpty()) return;
			if (last instanceof Text lt && lt.style().equals(style)) {
				line.set(line.size() - 1, new Text(lt.text() + t, style));
			} else {
				line.add(new Text(t, style));
			}
		}

		void inline(Inline i) {
			line.add(i);
		}

		void block(Block b) {
			flush();
			blocks.add(b);
		}

		void flush() {
			while (!line.isEmpty() && line.get(line.size() - 1) instanceof LineBreak) line.remove(line.size() - 1);
			if (!line.isEmpty() && line.get(line.size() - 1) instanceof Text t) {
				String trimmed = t.text().stripTrailing();
				if (trimmed.isEmpty()) line.remove(line.size() - 1);
				else line.set(line.size() - 1, new Text(trimmed, t.style()));
			}
			boolean content = line.stream().anyMatch(i -> i instanceof InlineImage
					|| i instanceof Text t && !t.text().isBlank());
			if (content) blocks.add(new Paragraph(List.copyOf(line)));
			line = new ArrayList<>();
		}

		private static String stripLeadingSpace(String t) {
			return t.startsWith(" ") ? t.substring(1) : t;
		}
	}
}
