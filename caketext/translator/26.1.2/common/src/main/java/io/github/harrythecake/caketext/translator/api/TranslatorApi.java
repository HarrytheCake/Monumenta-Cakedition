package io.github.harrythecake.caketext.translator.api;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.translator.TranslationService;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import org.jetbrains.annotations.Nullable;

/**
 * For other mods: translate their own text with CakeText Translator's string files and engine. Only plain Java
 * types, so a mod can call it by reflection without depending on CakeText (CakeWiki does). Call it on the game thread.
 * Nothing here throws: on any problem the answer is "keep the original".
 *
 * <p>A text goes in as parts: {@code keep[i]} parts (names, links: the words players look up) stay exactly as they
 * are and reach no engine; the rest is translated. A miss is queued for the engine and answered null; poll
 * {@link #changes()} and ask again when it moves.
 *
 * <p>Version {@value #VERSION}. Methods are only ever added.
 */
public final class TranslatorApi {
	public static final int VERSION = 1;

	private TranslatorApi() {
	}

	public static int version() {
		return VERSION;
	}

	/** Is translation on for {@code surface} (e.g. {@code "wiki"})? False when the player turned it off. */
	public static boolean active(String surface) {
		TranslationService s = TranslatorMod.service();
		return s != null && s.config().enabled && s.config().surface(surface);
	}

	/** The target language: {@code zh_cn} or {@code zh_tw}. */
	public static String language() {
		TranslationService s = TranslatorMod.service();
		return s == null ? "zh_cn" : s.config().language;
	}

	/** Goes up whenever a translation may have changed (an engine result arrived, files were reloaded). */
	public static long changes() {
		TranslationService s = TranslatorMod.service();
		return s == null ? 0 : s.changes();
	}

	/** Lines waiting for the engine (all surfaces). */
	public static int pending() {
		TranslationService s = TranslatorMod.service();
		return s == null ? 0 : s.machine().pending();
	}

	/**
	 * The translation of {@code parts} on {@code surface}: Strings (translated text) and Integers (the index of a
	 * {@code keep} part, placed where the translation puts it), or null to show the original (not translated yet:
	 * then it's queued).
	 */
	public static @Nullable Object[] translate(String surface, String[] parts, boolean[] keep) {
		TranslationService s = TranslatorMod.service();
		if (s == null || parts == null || keep == null) {
			return null;
		}
		try {
			return s.translateParts(surface, parts, keep, true);
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Translation for another mod failed on {}", surface, e);
			return null;
		}
	}
}
