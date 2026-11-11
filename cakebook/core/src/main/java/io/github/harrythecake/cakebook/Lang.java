package io.github.harrythecake.cakebook;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Language fallback, the same as Cakedition Updater's changelog. Plain Java. */
final class Lang {
	static final String ENGLISH = "en_us";

	private Lang() {}

	/** The game language first; Traditional Chinese falls back to Simplified, and everything ends in English. */
	static List<String> fallback(String language) {
		String lang = language == null ? ENGLISH : language.toLowerCase(Locale.ROOT);
		Set<String> order = new LinkedHashSet<>();
		order.add(lang);
		if (lang.equals("zh_hk")) {
			order.add("zh_tw");
		}
		if (lang.startsWith("zh_")) {
			order.add("zh_cn");
		}
		order.add(ENGLISH);
		return List.copyOf(order);
	}

	/** A folder name that is a Minecraft language code (en_us, zh_cn, … and lzh), not an images folder. */
	static boolean isCode(String name) {
		return name.matches("[a-z]{2,3}_[a-z0-9]{2,4}") || name.equals("lzh");
	}
}
