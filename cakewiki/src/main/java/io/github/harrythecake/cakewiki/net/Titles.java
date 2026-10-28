package io.github.harrythecake.cakewiki.net;

import java.util.Locale;

/** MediaWiki title rules the mod relies on. */
public final class Titles {
	private Titles() {}

	/** The form MediaWiki treats as the same page: underscores are spaces, runs of spaces collapse, the first letter is upper case. */
	public static String normalize(String title) {
		String t = title.replace('_', ' ').trim().replaceAll(" +", " ");
		return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
	}
}
