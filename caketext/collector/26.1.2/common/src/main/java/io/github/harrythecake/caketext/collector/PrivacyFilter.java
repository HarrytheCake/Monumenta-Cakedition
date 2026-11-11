package io.github.harrythecake.caketext.collector;

import java.util.regex.Pattern;

import java.util.Map;

import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.PlayerChat;

/**
 * Keeps other people's words out of the files. Player names are already {@code {n}} in templates; this drops lines
 * that look like a player talking (servers often send chat as system messages):
 * {@code <{n}> hi}, {@code [Guild] {n}: hi}, {@code {n} » hi}, {@code {n} whispers to you: hi}, {@code From {n}: hi},
 * plus whatever {@link PlayerChat} recognises in the original line.
 */
final class PrivacyFilter {
	private static final Pattern STYLE = Pattern.compile("\\{c\\d+}");
	private static final Pattern SPEAKER = Pattern.compile(
			"(?:^|[\\s\\]>)|])<?\\{n}(?:>|\\s*[:»>|]|\\s+whispers\\b)|\\b(?:from|to)\\s+\\{n}\\s*:",
			Pattern.CASE_INSENSITIVE);

	private PrivacyFilter() {
	}

	/** True if this line must not be stored. {@code original} is the line as shown, null if unknown. */
	static boolean reject(String surface, String template, String original) {
		if (template.length() > 2000) {
			return true;
		}
		if (Surfaces.CHAT.equals(surface) || Surfaces.ACTION_BAR.equals(surface)) {
			return SPEAKER.matcher(STYLE.matcher(template).replaceAll("")).find()
					|| original != null && PlayerChat.bodyStart(original) >= 0;
		}
		return false;
	}

	static boolean reject(String surface, String template) {
		return reject(surface, template, null);
	}

	/**
	 * For lines already in a collected file (only the template is left): rebuilds a stand-in of the shown line
	 * (styles dropped, numbers as 1, names as Player) and checks it like a new one.
	 */
	static boolean rejectStored(StringEntry e) {
		if (e.src == null) {
			return false;
		}
		if (e.src.indexOf('§') >= 0) {
			// made before § colour codes were read as styles; it comes back right the next time it's seen
			return true;
		}
		if (e.contexts == null || e.contexts.isEmpty()) {
			return false;
		}
		String shown = TextTemplate.readable(STYLE.matcher(e.src).replaceAll("")
				.replaceAll("\\{~\\d*}", "1").replaceAll("\\{n\\d*}", "Player"));
		for (Map<String, Object> c : e.contexts) {
			if (reject(String.valueOf(c.get("surface")), e.src, shown) || hasHan(shown)) {
				return true;
			}
		}
		return false;
	}

	/** Chinese characters: not source text (the source language is English), e.g. another mod's own message. */
	static boolean hasHan(String s) {
		return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
	}
}
