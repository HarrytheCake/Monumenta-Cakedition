package io.github.harrythecake.caketext.core;

import java.nio.file.Path;
import java.util.function.Predicate;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Paths and hooks shared by CakeText Collector and CakeText Translator. */
public final class CakeText {
	public static final Logger LOG = LoggerFactory.getLogger("CakeText");

	/**
	 * Set by the translator: does the dictionary already translate this template?
	 * The collector uses it to leave translated strings out of exports.
	 */
	public static volatile Predicate<String> isTranslated = template -> false;

	private CakeText() {
	}

	/** {@code config/caketext}: everything both mods write lives here. */
	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("caketext");
	}

	/** Where the translator reads dictionaries for one language, e.g. {@code translations/zh_cn}. */
	public static Path translations(String lang) {
		return dir().resolve("translations").resolve(lang);
	}
}
