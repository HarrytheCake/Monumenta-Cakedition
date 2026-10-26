package io.github.harrythecake.caketext.translator;

import net.minecraft.text.Text;

/**
 * Something that turns an English line into the target language: the built-in opus-mt model, or an LLM running in a
 * local server. Called on the translator's single worker thread, one chunk of plain text at a time (placeholders are
 * already replaced by stand-in numbers that must come back unchanged).
 */
public interface TranslationEngine {
	/** File-safe id; the engine's cache is {@code translations/<lang>/_machine/<id>.json.gz}. */
	String id();

	/** Shown to players in the hover card and footer, e.g. {@code opus-mt-en-zh int8}. */
	String label();

	/** Can it translate right now? */
	boolean ready();

	/** A status line for the settings screen and {@code /cttranslate status}. */
	Text describe();

	/** Translates {@code text}; throws when the engine fails (the line is then retried later). */
	String translate(String text, TranslatorConfig config) throws Exception;
}
