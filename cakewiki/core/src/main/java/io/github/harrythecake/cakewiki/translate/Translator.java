package io.github.harrythecake.cakewiki.translate;

/**
 * Translates a text given in parts, where {@code keep[i]} parts stay exactly as they are (CakeText Translator through
 * {@link CakeTextBridge}; a fake in tests). No Minecraft classes.
 */
public interface Translator {
	/**
	 * Strings (translated text) and Integers (the index of a kept part, wherever the translation puts it), or null
	 * to show the original for now (not translated yet: the translator works on it and {@link #changes()} moves).
	 */
	Object[] translate(String[] parts, boolean[] keep);

	/** Goes up whenever translations may have changed. */
	long changes();
}
