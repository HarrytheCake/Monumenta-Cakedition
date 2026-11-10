package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Link;

import java.util.List;
import java.util.Map;

/**
 * A page laid out for one width: things to draw in order (later items on top), in page coordinates
 * (0,0 = top left of the page), plus where each heading anchor is and the total height.
 * {@code unknownSizes}: some picture was placed at a guessed size, so lay out again once pictures load.
 */
public record Laid(List<Item> items, Map<String, Integer> anchors, int height, boolean unknownSizes) {
	public sealed interface Item {
		int x();
		int y();
		int w();
		int h();

		Item shift(int dx, int dy);

		default boolean contains(double px, double py) {
			return px >= x() && px < x() + w() && py >= y() && py < y() + h();
		}
	}

	/** One line of text in one style; {@code color} is the final ARGB colour to draw with. */
	public record Run(int x, int y, int w, int h, String text, Style style, int color) implements Item {
		public Run shift(int dx, int dy) { return new Run(x + dx, y + dy, w, h, text, style, color); }
	}

	/** A picture drawn at {@code w}×{@code h}; {@code link} is null when it isn't clickable. */
	public record Pic(int x, int y, int w, int h, Image image, Link link) implements Item {
		public Pic shift(int dx, int dy) { return new Pic(x + dx, y + dy, w, h, image, link); }
	}

	/** A filled rectangle (backgrounds, borders, rules), ARGB. */
	public record Fill(int x, int y, int w, int h, int argb) implements Item {
		public Fill shift(int dx, int dy) { return new Fill(x + dx, y + dy, w, h, argb); }
	}

	/** A click area that changes the view: toggles a collapsible ({@code value} -1) or picks tab {@code value} of {@code key}. */
	public record Control(int x, int y, int w, int h, Object key, int value) implements Item {
		public Control shift(int dx, int dy) { return new Control(x + dx, y + dy, w, h, key, value); }
	}

	/** The link under a point, or null. Text and picture links win over controls. */
	public Link linkAt(double px, double py) {
		for (int i = items.size() - 1; i >= 0; i--) {
			Item it = items.get(i);
			if (!it.contains(px, py)) continue;
			if (it instanceof Run r && r.style().link() != null) return r.style().link();
			if (it instanceof Pic p && p.link() != null) return p.link();
		}
		return null;
	}

	public Control controlAt(double px, double py) {
		for (int i = items.size() - 1; i >= 0; i--) {
			if (items.get(i) instanceof Control c && c.contains(px, py)) return c;
		}
		return null;
	}
}
