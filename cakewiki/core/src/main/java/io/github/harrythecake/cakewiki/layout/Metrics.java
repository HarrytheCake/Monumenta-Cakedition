package io.github.harrythecake.cakewiki.layout;

import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline.Style;

/** What the layout needs to know about the screen: text widths, line height, picture sizes, UI labels. */
public interface Metrics {
	int width(String text, Style style);

	int lineHeight();

	/** The picture's real pixel size once it has loaded ({@code {w, h}}), or null while unknown. */
	int[] naturalSize(Image image);

	/** A translated UI label, e.g. {@code cakewiki.show}. */
	String label(String key);

	/** How many leading characters of {@code s} fit in {@code width} (at least 0). */
	default int fit(String s, Style style, int width) {
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (width(s.substring(0, mid), style) <= width) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}
}
