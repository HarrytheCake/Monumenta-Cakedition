package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.Block.*;
import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Link;

import java.util.List;

/** A plain-text outline of a converted page, for reading conversions by eye in tests. */
final class DocumentDump {
	private DocumentDump() {}

	static String dump(Document doc) {
		StringBuilder sb = new StringBuilder("# " + doc.title() + "\n");
		blocks(doc.blocks(), sb, "");
		return sb.toString();
	}

	private static void blocks(List<Block> blocks, StringBuilder sb, String in) {
		for (Block b : blocks) {
			if (b instanceof Heading h) line(sb, in, "H" + h.level() + " " + text(h.text()) + (h.anchor() != null ? "  #" + h.anchor() : ""));
			else if (b instanceof Paragraph p) line(sb, in, text(p.text()));
			else if (b instanceof ListBlock l) {
				int n = 1;
				for (List<Block> item : l.items()) {
					line(sb, in, l.ordered() ? (n++) + "." : "*");
					blocks(item, sb, in + "    ");
				}
			} else if (b instanceof ImageBlock i) line(sb, in, "[image " + i.image().url() + "] " + text(i.caption()));
			else if (b instanceof Gallery g) g.images().forEach(i -> line(sb, in, "[gallery image " + i.image().url() + "] " + text(i.caption())));
			else if (b instanceof Table t) {
				line(sb, in, "TABLE " + t.rows().size() + " rows");
				for (Row r : t.rows()) {
					line(sb, in, "  |");
					for (Cell c : r.cells()) {
						line(sb, in, "    " + (c.header() ? "TH" : "TD") + (c.colspan() > 1 ? " colspan=" + c.colspan() : "")
								+ (c.rowspan() > 1 ? " rowspan=" + c.rowspan() : "") + (c.background() != null ? String.format(" bg=%06X", c.background()) : ""));
						blocks(c.content(), sb, in + "        ");
					}
				}
			} else if (b instanceof Infobox box) {
				line(sb, in, "INFOBOX " + text(box.title()));
				box.images().forEach(i -> line(sb, in, "  [image tab " + i.label() + ": " + i.image().url() + "]"));
				for (InfoItem item : box.items()) {
					if (item instanceof InfoHeader h) line(sb, in, "  == " + text(h.text()));
					else if (item instanceof InfoRow r) {
						line(sb, in, "  " + text(r.label()) + ":");
						blocks(r.value(), sb, in + "      ");
					}
				}
			} else if (b instanceof Collapsible c) {
				line(sb, in, "COLLAPSIBLE" + (c.collapsed() ? " (closed) " : " (open) ") + text(c.title()));
				blocks(c.content(), sb, in + "    ");
			} else if (b instanceof Tabs t) {
				for (Tab tab : t.tabs()) {
					line(sb, in, "TAB " + tab.label());
					blocks(tab.content(), sb, in + "    ");
				}
			} else if (b instanceof Indented i) blocks(i.content(), sb, in + "  > ");
			else if (b instanceof Rule) line(sb, in, "----");
		}
	}

	private static void line(StringBuilder sb, String in, String s) {
		sb.append(in).append(s).append('\n');
	}

	static String text(List<Inline> line) {
		StringBuilder sb = new StringBuilder();
		for (Inline i : line) {
			if (i instanceof Inline.Text t) {
				String s = t.text();
				Inline.Style st = t.style();
				if (st.bold()) s = "**" + s + "**";
				if (st.color() != null) s = String.format("{%06X:%s}", st.color(), s);
				if (st.link() instanceof Link.Page p) s = "[" + s + "](" + p.title() + (p.anchor() != null ? "#" + p.anchor() : "") + ")";
				else if (st.link() instanceof Link.External e) s = "[" + s + "](" + e.url() + ")";
				else if (st.link() instanceof Link.Missing) s = "[" + s + "](missing)";
				sb.append(s);
			} else if (i instanceof Inline.InlineImage img) sb.append("[img]");
			else sb.append(" / ");
		}
		return sb.toString();
	}
}
