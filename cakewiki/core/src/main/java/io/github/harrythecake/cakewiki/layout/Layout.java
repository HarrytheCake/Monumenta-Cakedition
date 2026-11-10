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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Lays a {@link Document} out for a given width. Pure arithmetic over {@link Metrics}: no Minecraft classes,
 * so it is unit-tested with fake metrics. Closed collapsibles are not laid out at all (some navigation boxes
 * hold most of a 1 MB page), and only the selected tab's content is.
 */
public final class Layout {
	// Dark theme, as the wiki's own navigation boxes expect (ARGB).
	public static final int PAGE_BG = 0xFF15151D;
	public static final int TEXT = 0xFFE2E2E8;
	public static final int TEXT_ON_LIGHT = 0xFF1A1A1A;
	public static final int MUTED = 0xFF9C9CAC;
	public static final int LINK = 0xFF7DB9FF;
	public static final int LINK_ON_LIGHT = 0xFF0645AD;
	public static final int LINK_MISSING = 0xFFFF7B7B;
	public static final int HEADING = 0xFFFFD58A;
	public static final int BORDER = 0xFF3B3B4C;
	public static final int HEADER_BG = 0xFF272738;
	public static final int CELL_BG = 0xFF1C1C27;
	public static final int BOX_BG = 0xFF1E1E2B;
	public static final int TAB_BG = 0xFF2A2A3A;
	public static final int TAB_SELECTED = 0xFF4A4A80;

	/** Wiki pixels → GUI pixels for pictures (a 128 px item texture shows at 64). */
	static final double IMAGE_SCALE = 0.5;
	/** Inline pictures up to this tall (wiki px) are icons, drawn at text height. */
	static final int ICON_MAX = 32;
	static final int GAP = 4, INDENT = 12, PAD = 2, INFOBOX_WIDTH = 220, MIN_WORD_CAP = 100;

	private final Metrics m;
	private final ViewState state;
	private final int lh;
	private final boolean bold;
	private final List<Item> items = new ArrayList<>();
	private final Map<String, Integer> anchors;
	private boolean unknownSizes;
	/** Applied to every line of text before it's laid out: the translation (CakeText), or nothing. */
	private UnaryOperator<List<Inline>> words = UnaryOperator.identity();

	private Layout(Metrics m, ViewState state, boolean bold, Map<String, Integer> anchors) {
		this.m = m;
		this.state = state;
		this.lh = m.lineHeight();
		this.bold = bold;
		this.anchors = anchors;
	}

	/** Lays out {@code header} (title, attribution) and then the page, {@code width} GUI pixels wide. */
	public static Laid page(List<Block> header, Document doc, int width, Metrics m, ViewState state) {
		return page(header, doc, width, m, state, UnaryOperator.identity());
	}

	/**
	 * Like {@link #page(List, Document, int, Metrics, ViewState)}, with every line of the page (not the header, not
	 * the infobox title: those are the page's name) passed through {@code words} first.
	 */
	public static Laid page(List<Block> header, Document doc, int width, Metrics m, ViewState state,
			UnaryOperator<List<Inline>> words) {
		Layout l = new Layout(m, state, false, new HashMap<>());
		int y = l.blocks(header, 0, 0, width, PAGE_BG);
		l.words = words;
		y = l.blocks(doc.blocks(), 0, header.isEmpty() ? y : y + GAP * 2, width, PAGE_BG);
		return new Laid(List.copyOf(l.items), Map.copyOf(l.anchors), y, l.unknownSizes);
	}

	/** A layout for a table cell or similar, drawn at (0,0) and shifted into place by the caller. */
	private Layout child(boolean boldText) {
		Layout c = new Layout(m, state, boldText, new HashMap<>());
		c.words = words;
		return c;
	}

	// ---- blocks -------------------------------------------------------------------------------------------

	/**
	 * Blocks top to bottom. An infobox floats right when there is room for text beside it (as on the wiki):
	 * text, headings and lists flow in the narrower column; wide things (tables, tabs, galleries, collapsibles,
	 * pictures) start below it.
	 */
	int blocks(List<Block> blocks, int x, int y, int w, int bg) {
		boolean first = true;
		int floatBottom = Integer.MIN_VALUE, floatW = 0;
		for (Block b : blocks) {
			int top = first ? y : y + GAP;
			if (b instanceof Infobox box && w >= 2 * INFOBOX_WIDTH + 20 && floatBottom <= top) {
				floatBottom = infobox(box, x + w - INFOBOX_WIDTH, top, INFOBOX_WIDTH);
				floatW = INFOBOX_WIDTH + 8;
				continue;
			}
			boolean beside = top < floatBottom;
			if (beside && !flowsBesideFloat(b)) {
				top = floatBottom + GAP;
				beside = false;
			}
			int end = block(b, x, top, beside ? w - floatW : w, bg);
			if (end > top) { // empty blocks add no gap
				y = end;
				first = false;
			}
		}
		return Math.max(y, floatBottom);
	}

	private static boolean flowsBesideFloat(Block b) {
		return b instanceof Paragraph || b instanceof Heading || b instanceof ListBlock || b instanceof Indented || b instanceof Rule;
	}

	private int block(Block b, int x, int y, int w, int bg) {
		if (b instanceof Paragraph p) return text(p.text(), x, y, w, bg, bold, null);
		if (b instanceof Heading h) return heading(h, x, y, w, bg);
		if (b instanceof ListBlock l) return list(l, x, y, w, bg);
		if (b instanceof ImageBlock i) return image(i, x, y, w, bg);
		if (b instanceof Gallery g) return gallery(g, x, y, w, bg);
		if (b instanceof Table t) return table(t, x, y, w);
		if (b instanceof Infobox box) return infobox(box, x, y, w);
		if (b instanceof Collapsible c) return collapsible(c, x, y, w, bg);
		if (b instanceof Tabs t) return tabs(t, x, y, w, bg);
		if (b instanceof Indented i) return blocks(i.content(), x + INDENT, y, w - INDENT, bg);
		if (b instanceof Rule) {
			items.add(new Fill(x, y + 2, w, 1, BORDER));
			return y + 5;
		}
		return y;
	}

	private int heading(Heading h, int x, int y, int w, int bg) {
		if (h.anchor() != null) anchors.put(h.anchor(), y);
		int top = y + (h.level() <= 3 ? 4 : 2);
		int end = text(h.text(), x, top, w, bg, true, h.level() <= 2 ? HEADING : TEXT);
		if (h.level() <= 2) {
			items.add(new Fill(x, end + 1, w, 1, BORDER));
			end += 3;
		}
		return end;
	}

	private int list(ListBlock l, int x, int y, int w, int bg) {
		int n = 1;
		int indent = INDENT;
		if (l.ordered()) indent = Math.max(INDENT, m.width(l.items().size() + ".", Style.PLAIN) + 4);
		boolean first = true;
		for (List<Block> item : l.items()) {
			int top = first ? y : y + 1;
			first = false;
			String marker = l.ordered() ? (n++) + "." : "•";
			int mw = m.width(marker, Style.PLAIN);
			items.add(new Run(x + indent - mw - 4, top, mw, lh, marker, Style.PLAIN, colorFor(Style.PLAIN, bg, MUTED)));
			int end = blocks(item, x + indent, top, w - indent, bg);
			y = Math.max(end, top + lh);
		}
		return y;
	}

	private int image(ImageBlock ib, int x, int y, int w, int bg) {
		int[] size = blockSize(ib.image(), w);
		items.add(new Pic(x, y, size[0], size[1], ib.image(), null));
		int end = y + size[1];
		if (!ib.caption().isEmpty()) end = text(ib.caption(), x, end + 2, Math.max(size[0], Math.min(w, 160)), bg, false, MUTED);
		return end;
	}

	private int gallery(Gallery g, int x, int y, int w, int bg) {
		int gx = x, rowTop = y, rowBottom = y;
		for (ImageBlock ib : g.images()) {
			int[] size = blockSize(ib.image(), Math.min(w, 128));
			int cw = Math.min(w, Math.max(size[0], 56));
			if (gx > x && gx + cw > x + w) {
				gx = x;
				rowTop = rowBottom + 6;
			}
			items.add(new Pic(gx + (cw - size[0]) / 2, rowTop, size[0], size[1], ib.image(), null));
			int bottom = rowTop + size[1];
			if (!ib.caption().isEmpty()) bottom = text(ib.caption(), gx, bottom + 2, cw, bg, false, MUTED);
			rowBottom = Math.max(rowBottom, bottom);
			gx += cw + 6;
		}
		return rowBottom;
	}

	private int collapsible(Collapsible c, int x, int y, int w, int bg) {
		boolean open = state.isOpen(c);
		List<Inline> title = new ArrayList<>();
		title.add(new Text(open ? "▼ " : "▶ ", Style.PLAIN));
		if (c.title().isEmpty()) title.add(new Text(m.label(open ? "cakewiki.hide" : "cakewiki.show"), Style.PLAIN));
		else title.addAll(c.title());
		int start = items.size();
		int titleEnd = text(title, x + 4, y + 3, w - 8, HEADER_BG, true, null);
		int headerH = titleEnd - y + 3;
		items.add(start, new Fill(x, y, w, headerH, HEADER_BG));
		items.add(new Control(x, y, w, headerH, c, -1));
		int end = y + headerH;
		if (open && !c.content().isEmpty()) {
			int contentEnd = blocks(c.content(), x + 4, end + 4, w - 8, bg);
			int h = contentEnd + 4 - end;
			items.add(new Fill(x, end, 1, h, HEADER_BG));
			items.add(new Fill(x + w - 1, end, 1, h, HEADER_BG));
			items.add(new Fill(x, end + h - 1, w, 1, HEADER_BG));
			end += h;
		}
		return end;
	}

	private int tabs(Tabs t, int x, int y, int w, int bg) {
		List<String> labels = t.tabs().stream().map(Tab::label).toList();
		int selected = state.tab(t, labels.size());
		int end = tabRow(t, labels, selected, x, y, w);
		return blocks(t.tabs().get(selected).content(), x, end + GAP, w, bg);
	}

	/** A row of tab buttons (wrapping); returns the bottom. */
	private int tabRow(Object key, List<String> labels, int selected, int x, int y, int w) {
		int bx = x, by = y, rowH = lh + 4;
		for (int i = 0; i < labels.size(); i++) {
			String label = labels.get(i);
			int bw = Math.min(w, m.width(label, Style.PLAIN) + 10);
			if (bx > x && bx + bw > x + w) {
				bx = x;
				by += rowH + 2;
			}
			items.add(new Fill(bx, by, bw, rowH, i == selected ? TAB_SELECTED : TAB_BG));
			String shown = label.substring(0, m.fit(label, Style.PLAIN, bw - 10));
			items.add(new Run(bx + 5, by + 2, m.width(shown, Style.PLAIN), lh, shown, Style.PLAIN, TEXT));
			items.add(new Control(bx, by, bw, rowH, key, i));
			bx += bw + 2;
		}
		return by + rowH;
	}

	private int infobox(Infobox box, int x, int y, int w) {
		int iw = Math.min(w, INFOBOX_WIDTH);
		int start = items.size();
		int cy = y + 1;
		if (!box.title().isEmpty()) {
			int s = items.size();
			// the infobox title is the thing's name: never translated
			UnaryOperator<List<Inline>> pageWords = words;
			words = UnaryOperator.identity();
			int end = text(box.title(), x + 4, cy + 3, iw - 8, HEADER_BG, true, HEADING);
			words = pageWords;
			items.add(s, new Fill(x + 1, cy, iw - 2, end + 3 - cy, HEADER_BG));
			cy = end + 3;
		}
		if (!box.images().isEmpty()) {
			int sel = state.tab(box, box.images().size());
			if (box.images().size() > 1) {
				List<String> labels = new ArrayList<>();
				for (int i = 0; i < box.images().size(); i++) {
					String l = box.images().get(i).label();
					labels.add(l != null && !l.isEmpty() ? l : String.valueOf(i + 1));
				}
				cy = tabRow(box, labels, sel, x + 3, cy + 3, iw - 6);
			}
			Image img = box.images().get(sel).image();
			int[] size = blockSize(img, iw - 8);
			items.add(new Pic(x + (iw - size[0]) / 2, cy + 4, size[0], size[1], img, null));
			cy += size[1] + 8;
		}
		int labelW = iw * 2 / 5;
		for (InfoItem item : box.items()) {
			if (item instanceof InfoHeader h) {
				int s = items.size();
				int end = text(h.text(), x + 4, cy + 2, iw - 8, HEADER_BG, true, HEADING);
				items.add(s, new Fill(x + 1, cy, iw - 2, end + 2 - cy, HEADER_BG));
				cy = end + 2;
			} else if (item instanceof InfoRow r) {
				items.add(new Fill(x + 3, cy, iw - 6, 1, BORDER));
				int top = cy + 2;
				int end;
				if (r.label().isEmpty()) {
					end = blocks(r.value(), x + 4, top, iw - 8, BOX_BG);
				} else {
					int labelEnd = text(r.label(), x + 4, top, labelW - 6, BOX_BG, true, MUTED);
					end = Math.max(labelEnd, blocks(r.value(), x + labelW, top, iw - labelW - 4, BOX_BG));
				}
				cy = Math.max(end, top + lh) + 2;
			}
		}
		cy += 2;
		items.add(start, new Fill(x + 1, y + 1, iw - 2, cy - y - 1, BOX_BG));
		items.add(start, new Fill(x, y, iw, cy - y + 1, BORDER));
		return cy + 1;
	}

	// ---- tables -------------------------------------------------------------------------------------------

	/** Grid position of each cell after colspan/rowspan: {row, col, rowspan, colspan}. */
	private record Placed(Cell cell, int row, int col, int rows, int cols) {}

	private static List<Placed> place(Table t) {
		List<Placed> out = new ArrayList<>();
		Set<Long> taken = new HashSet<>();
		int nRows = t.rows().size();
		for (int r = 0; r < nRows; r++) {
			int c = 0;
			for (Cell cell : t.rows().get(r).cells()) {
				while (taken.contains(((long) r << 32) | c)) c++;
				int rs = Math.max(1, Math.min(cell.rowspan(), nRows - r));
				int cs = Math.max(1, cell.colspan());
				for (int dr = 0; dr < rs; dr++) for (int dc = 0; dc < cs; dc++) taken.add(((long) (r + dr) << 32) | (c + dc));
				out.add(new Placed(cell, r, c, rs, cs));
				c += cs;
			}
		}
		return out;
	}

	/** Per column {min[], max[]}: narrowest without breaking words, and widest without wrapping. */
	private int[][] columns(List<Placed> cells) {
		int n = 0;
		for (Placed p : cells) n = Math.max(n, p.col() + p.cols());
		int[] min = new int[n], max = new int[n];
		for (Placed p : cells) {
			if (p.cols() != 1) continue;
			int[] mm = measure(p.cell().content(), p.cell().header());
			min[p.col()] = Math.max(min[p.col()], mm[0] + 2 * PAD);
			max[p.col()] = Math.max(max[p.col()], mm[1] + 2 * PAD);
		}
		for (Placed p : cells) {
			if (p.cols() == 1) continue;
			int[] mm = measure(p.cell().content(), p.cell().header());
			spread(min, p.col(), p.cols(), mm[0] + 2 * PAD - (p.cols() - 1));
			spread(max, p.col(), p.cols(), mm[1] + 2 * PAD - (p.cols() - 1));
		}
		for (int c = 0; c < n; c++) max[c] = Math.max(max[c], min[c]);
		return new int[][] {min, max};
	}

	private static void spread(int[] widths, int from, int count, int need) {
		int have = 0;
		for (int i = from; i < from + count; i++) have += widths[i];
		if (need <= have) return;
		int extra = need - have;
		for (int i = 0; i < count; i++) widths[from + i] += extra / count + (i < extra % count ? 1 : 0);
	}

	static int[] fitColumns(int[] min, int[] max, int avail) {
		int sumMin = 0, sumMax = 0;
		for (int c = 0; c < min.length; c++) {
			sumMin += min[c];
			sumMax += max[c];
		}
		int[] w = new int[min.length];
		for (int c = 0; c < w.length; c++) {
			if (sumMax <= avail) w[c] = max[c];
			else if (sumMin >= avail) w[c] = Math.max(2 * PAD + 4, (int) ((long) min[c] * avail / Math.max(1, sumMin)));
			else w[c] = min[c] + (int) ((long) (max[c] - min[c]) * (avail - sumMin) / Math.max(1, sumMax - sumMin));
		}
		return w;
	}

	private int table(Table t, int x, int y, int w) {
		List<Placed> cells = place(t);
		if (cells.isEmpty()) return y;
		int[][] mm = columns(cells);
		int nCols = mm[0].length;
		int[] cw = fitColumns(mm[0], mm[1], w - (nCols + 1));
		int nRows = t.rows().size();
		int[] rowH = new int[nRows];
		java.util.Arrays.fill(rowH, 2 * PAD);
		List<List<Item>> laid = new ArrayList<>();
		int[] cellH = new int[cells.size()];
		int[] cellBg = new int[cells.size()];
		for (int i = 0; i < cells.size(); i++) {
			Placed p = cells.get(i);
			int spanW = span(cw, p.col(), p.cols());
			cellBg[i] = p.cell().background() != null ? 0xFF000000 | p.cell().background()
					: p.cell().header() ? HEADER_BG : CELL_BG;
			Layout sub = child(p.cell().header());
			int h = sub.blocks(p.cell().content(), 0, 0, spanW - 2 * PAD, cellBg[i]);
			unknownSizes |= sub.unknownSizes;
			laid.add(sub.items);
			cellH[i] = h + 2 * PAD;
			if (p.rows() == 1) rowH[p.row()] = Math.max(rowH[p.row()], cellH[i]);
		}
		for (int i = 0; i < cells.size(); i++) {
			Placed p = cells.get(i);
			if (p.rows() == 1) continue;
			int have = span(rowH, p.row(), p.rows());
			if (cellH[i] > have) rowH[p.row() + p.rows() - 1] += cellH[i] - have;
		}
		int[] rowTop = new int[nRows];
		int ry = y + 1;
		for (int r = 0; r < nRows; r++) {
			rowTop[r] = ry;
			ry += rowH[r] + 1;
		}
		int tableW = span(cw, 0, nCols) + 2;
		items.add(new Fill(x, y, tableW, ry - y, BORDER));
		boolean[][] filled = new boolean[nRows][nCols];
		for (Placed p : cells) {
			for (int r = p.row(); r < p.row() + p.rows(); r++) {
				for (int c = p.col(); c < Math.min(nCols, p.col() + p.cols()); c++) filled[r][c] = true;
			}
		}
		for (int r = 0; r < nRows; r++) { // rows with fewer cells than the table: plain cell colour, not border
			for (int c = 0; c < nCols; c++) {
				if (!filled[r][c]) items.add(new Fill(colX(x, cw, c), rowTop[r], cw[c], rowH[r], CELL_BG));
			}
		}
		for (int i = 0; i < cells.size(); i++) {
			Placed p = cells.get(i);
			int cx = colX(x, cw, p.col());
			int cy = rowTop[p.row()];
			items.add(new Fill(cx, cy, span(cw, p.col(), p.cols()), span(rowH, p.row(), p.rows()), cellBg[i]));
			for (Item it : laid.get(i)) items.add(it.shift(cx + PAD, cy + PAD));
		}
		return ry;
	}

	/** Left edge of column {@code col}: the outer border, the columns before it and their borders. */
	private static int colX(int x, int[] cw, int col) {
		return col == 0 ? x + 1 : x + 1 + span(cw, 0, col) + 1;
	}

	/** Width (or height) of {@code count} columns starting at {@code from}, with the 1 px borders between them. */
	private static int span(int[] sizes, int from, int count) {
		int s = 0;
		for (int i = from; i < from + count; i++) s += sizes[i];
		return s + Math.max(0, count - 1);
	}

	// ---- measuring (for table columns) --------------------------------------------------------------------

	/** {min, max} width of blocks: min = widest unbreakable piece (capped), max = widest unwrapped line. */
	private int[] measure(List<Block> blocks, boolean boldText) {
		int min = 0, max = 0;
		for (Block b : blocks) {
			int[] mm = measure(b, boldText);
			min = Math.max(min, mm[0]);
			max = Math.max(max, mm[1]);
		}
		return new int[] {min, max};
	}

	private int[] measure(Block b, boolean boldText) {
		if (b instanceof Paragraph p) return measureLine(p.text(), boldText);
		if (b instanceof Heading h) return measureLine(h.text(), true);
		if (b instanceof ListBlock l) {
			int[] mm = {0, 0};
			for (List<Block> item : l.items()) {
				int[] i = measure(item, boldText);
				mm[0] = Math.max(mm[0], i[0]);
				mm[1] = Math.max(mm[1], i[1]);
			}
			return new int[] {mm[0] + INDENT, mm[1] + INDENT};
		}
		if (b instanceof Indented i) {
			int[] mm = measure(i.content(), boldText);
			return new int[] {mm[0] + INDENT, mm[1] + INDENT};
		}
		if (b instanceof ImageBlock i) {
			int iw = blockSize(i.image(), Integer.MAX_VALUE)[0];
			return new int[] {Math.min(iw, MIN_WORD_CAP), Math.max(iw, measureLine(i.caption(), false)[1])};
		}
		if (b instanceof Gallery g) {
			int min = 0, sum = 0;
			for (ImageBlock i : g.images()) {
				int iw = Math.max(56, blockSize(i.image(), 128)[0]);
				min = Math.max(min, iw);
				sum += iw + 6;
			}
			return new int[] {min, sum};
		}
		if (b instanceof Table t) {
			int[][] cols = columns(place(t));
			int border = cols[0].length + 1;
			return new int[] {sum(cols[0]) + border, sum(cols[1]) + border};
		}
		if (b instanceof Infobox) return new int[] {120, INFOBOX_WIDTH};
		if (b instanceof Collapsible c) {
			int[] title = measureLine(c.title(), true);
			int[] mm = state.isOpen(c) ? measure(c.content(), boldText) : new int[] {0, 0};
			return new int[] {Math.max(title[0], mm[0]) + 16, Math.max(title[1], mm[1]) + 16};
		}
		if (b instanceof Tabs t) {
			int labels = 0;
			for (Tab tab : t.tabs()) labels += m.width(tab.label(), Style.PLAIN) + 12;
			int[] mm = measure(t.tabs().get(state.tab(t, t.tabs().size())).content(), boldText);
			return new int[] {mm[0], Math.max(mm[1], labels)};
		}
		return new int[] {0, 0};
	}

	private int[] measureLine(List<Inline> line, boolean boldText) {
		int min = 0, max = 0, cur = 0, group = 0;
		for (Token t : tokens(line, boldText)) {
			if (t.br()) {
				max = Math.max(max, cur);
				cur = 0;
				group = 0;
				continue;
			}
			int tw = width(t);
			if (t.space()) {
				group = 0;
			} else {
				group = t.glued() ? group + tw : tw;
				min = Math.max(min, Math.min(group, MIN_WORD_CAP));
			}
			cur += tw;
		}
		return new int[] {min, Math.max(max, cur)};
	}

	private static int sum(int[] a) {
		int s = 0;
		for (int v : a) s += v;
		return s;
	}

	// ---- text ---------------------------------------------------------------------------------------------

	/**
	 * A word, a space, a picture or a line break. {@code glued}: no line break is allowed before it, because
	 * it touches the previous word without a space (a link followed by its punctuation, a word split by a
	 * style change, a non-breaking space).
	 */
	private record Token(String text, Style style, Image image, Link imageLink, boolean br, boolean space, boolean glued) {}

	private List<Token> tokens(List<Inline> line, boolean boldText) {
		List<Token> out = new ArrayList<>();
		for (Inline in : words.apply(line)) {
			if (in instanceof LineBreak) {
				out.add(new Token(null, null, null, null, true, false, false));
			} else if (in instanceof InlineImage img) {
				out.add(new Token(null, null, img.image(), img.link(), false, false, touches(out)));
			} else if (in instanceof Text t) {
				Style s = boldText ? t.style().withBold() : t.style();
				String str = t.text();
				int i = 0;
				while (i < str.length()) {
					if (str.charAt(i) == ' ') {
						out.add(new Token(" ", s, null, null, false, true, false));
						i++;
					} else {
						int j = str.indexOf(' ', i);
						if (j < 0) j = str.length();
						// a non-breaking space joins words; it is drawn as a plain space
						word(out, str.substring(i, j).replace('\u00A0', ' '), s);
						i = j;
					}
				}
			}
		}
		return out;
	}

	/**
	 * Adds a word (no spaces in it). Chinese and Japanese have no spaces: a line may break between two such characters,
	 * except before closing punctuation (\uFF0C\u3002\uFF09\u2026) and after opening punctuation (\uFF08\u300C\u2026).
	 */
	private static void word(List<Token> out, String word, Style s) {
		int start = 0;
		boolean first = true;
		for (int k = 1; k < word.length(); k++) {
			if (breakBetween(word.charAt(k - 1), word.charAt(k))) {
				out.add(new Token(word.substring(start, k), s, null, null, false, false, first && touches(out)));
				first = false;
				start = k;
			}
		}
		out.add(new Token(word.substring(start), s, null, null, false, false, first && touches(out)));
	}

	private static final String NO_BREAK_BEFORE = "\uFF0C\u3002\u3001\uFF1B\uFF1A\uFF01\uFF1F\uFF09\u300D\u300F\u3011\u300B\u3009\u3015\u2026\u00B7\uFF05,.;:!?)]}%";
	private static final String NO_BREAK_AFTER = "\uFF08\u300C\u300E\u3010\u300A\u3008\u3014([{";

	static boolean breakBetween(char a, char b) {
		return (cjk(a) || cjk(b)) && NO_BREAK_BEFORE.indexOf(b) < 0 && NO_BREAK_AFTER.indexOf(a) < 0;
	}

	private static boolean cjk(char c) {
		Character.UnicodeScript script = Character.UnicodeScript.of(c);
		if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) {
			return true;
		}
		Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
		return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
	}

	private static boolean touches(List<Token> before) {
		if (before.isEmpty()) return false;
		Token last = before.get(before.size() - 1);
		return !last.space() && !last.br();
	}

	private int width(Token t) {
		if (t.image() != null) return inlineSize(t.image())[0];
		return m.width(t.text(), t.style());
	}

	/** A piece of a line waiting to be emitted: text or picture at {@code x} relative to the line start. */
	private record Piece(Token token, String text, int x, int w, int h) {}

	/** Wraps a line of inlines into {@code w}; returns the y below the last line. */
	private int text(List<Inline> line, int x, int y, int w, int bg, boolean boldText, Integer defaultColor) {
		if (line.isEmpty()) return y;
		List<Piece> cur = new ArrayList<>();
		int cx = 0;
		List<Token> tokens = tokens(line, boldText);
		for (int k = 0; k < tokens.size(); k++) {
			Token t = tokens.get(k);
			if (t.br()) {
				y = flush(cur, x, y, bg, defaultColor);
				cx = 0;
				continue;
			}
			if (t.space() && cx == 0) continue;
			int tw = width(t);
			if (cx > 0 && !t.space() && !t.glued()) {
				// move a whole glued group to the next line rather than break inside it, if it fits there
				int group = tw;
				for (int j = k + 1; j < tokens.size() && tokens.get(j).glued(); j++) group += width(tokens.get(j));
				if (group <= w && cx + group > w) {
					y = flush(cur, x, y, bg, defaultColor);
					cx = 0;
				}
			}
			if (cx > 0 && cx + tw > w) {
				y = flush(cur, x, y, bg, defaultColor);
				cx = 0;
				if (t.space()) continue;
			}
			if (t.image() == null && tw > w) {
				// a word wider than the whole line: break it wherever it has to
				String s = t.text();
				while (!s.isEmpty()) {
					int n = Math.max(1, m.fit(s, t.style(), w - cx));
					String part = s.substring(0, n);
					int pw = m.width(part, t.style());
					cur.add(new Piece(t, part, cx, pw, lh));
					cx += pw;
					s = s.substring(n);
					if (!s.isEmpty()) {
						y = flush(cur, x, y, bg, defaultColor);
						cx = 0;
					}
				}
				continue;
			}
			int th = t.image() != null ? inlineSize(t.image())[1] : lh;
			cur.add(new Piece(t, t.text(), cx, tw, th));
			cx += tw;
		}
		if (!cur.isEmpty()) y = flush(cur, x, y, bg, defaultColor);
		return y;
	}

	/** Emits one line (bottom-aligned, so a tall picture pushes the text down) and returns the next line's y. */
	private int flush(List<Piece> line, int x, int y, int bg, Integer defaultColor) {
		while (!line.isEmpty() && line.get(line.size() - 1).token().space()) line.remove(line.size() - 1);
		int lineH = lh;
		for (Piece p : line) lineH = Math.max(lineH, p.h());
		Run last = null;
		for (Piece p : line) {
			Token t = p.token();
			if (t.image() != null) {
				items.add(new Pic(x + p.x(), y + lineH - p.h() - (p.h() < lh ? 1 : 0), p.w(), p.h(), t.image(), t.imageLink()));
				last = null;
				continue;
			}
			int ty = y + lineH - lh;
			if (last != null && last.style().equals(t.style()) && last.x() + last.w() == x + p.x()) {
				last = new Run(last.x(), ty, last.w() + p.w(), lh, last.text() + p.text(), last.style(), last.color());
				items.set(items.size() - 1, last);
			} else {
				last = new Run(x + p.x(), ty, p.w(), lh, p.text(), t.style(), colorFor(t.style(), bg, defaultColor));
				items.add(last);
			}
		}
		line.clear();
		return y + lineH;
	}

	// ---- pictures -----------------------------------------------------------------------------------------

	/** Size in wiki pixels: from the page if given, else from the loaded picture, else null. */
	private int[] natural(Image img) {
		if (img.width() > 0 && img.height() > 0) return new int[] {img.width(), img.height()};
		int[] loaded = m.naturalSize(img);
		if (loaded == null) unknownSizes = true;
		return loaded;
	}

	/** A picture in text: icons at text height, bigger pictures at {@link #IMAGE_SCALE}. */
	private int[] inlineSize(Image img) {
		int[] n = natural(img);
		int icon = lh - 1;
		if (n == null || n[1] <= ICON_MAX) {
			int w = n == null ? icon : Math.max(1, Math.round((float) icon * n[0] / n[1]));
			return new int[] {w, icon};
		}
		return scaled(n, Integer.MAX_VALUE);
	}

	/** A picture on its own: {@link #IMAGE_SCALE}, no wider than {@code maxW}; a 32×32 placeholder while unknown. */
	private int[] blockSize(Image img, int maxW) {
		int[] n = natural(img);
		if (n == null) return new int[] {Math.min(32, maxW), 32};
		return scaled(n, maxW);
	}

	private static int[] scaled(int[] n, int maxW) {
		int w = Math.max(1, (int) Math.round(n[0] * IMAGE_SCALE));
		int h = Math.max(1, (int) Math.round(n[1] * IMAGE_SCALE));
		if (w > maxW) {
			h = Math.max(1, (int) ((long) h * maxW / w));
			w = maxW;
		}
		return new int[] {w, h};
	}

	// ---- colour -------------------------------------------------------------------------------------------

	/** The wiki's colour if it is readable on {@code bg}, otherwise ours (links, missing links, plain text). */
	static int colorFor(Style s, int bg, Integer defaultColor) {
		boolean light = luminance(bg) > 0.4;
		Link link = s.link();
		int fallback;
		if (link instanceof Link.Missing) fallback = LINK_MISSING;
		else if (link != null) fallback = light ? LINK_ON_LIGHT : LINK;
		else if (defaultColor != null && !light) fallback = defaultColor;
		else fallback = light ? TEXT_ON_LIGHT : TEXT;
		if (s.color() == null) return fallback;
		int c = 0xFF000000 | s.color();
		return contrast(c, bg) < 2.0 ? fallback : c;
	}

	static double luminance(int rgb) {
		return 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
	}

	private static double channel(int v) {
		double c = (v & 0xFF) / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}
}
