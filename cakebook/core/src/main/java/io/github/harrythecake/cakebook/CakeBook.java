package io.github.harrythecake.cakebook;

import io.github.harrythecake.cakebook.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's id, log and loaded state (config, books, notes), without Minecraft classes. The game side
 * ({@code CakeBookClient} in each version's common part) fills it in.
 */
public final class CakeBook {
	public static final String MOD_ID = "cakebook";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Config config = new Config();
	private static volatile Library library = Library.EMPTY;
	private static Notes notes;

	private CakeBook() {}

	/** Reads config/cakebook/config.json5 and opens config/cakebook/notes/. Once, at client start. */
	public static void init() {
		config = Config.load();
		notes = new Notes(Services.PLATFORM.configDir().resolve(MOD_ID).resolve("notes"));
	}

	/** Reads config/cakebook/config.json5 again (/cakebook reload, CakeConfighub). */
	public static void reloadConfig() {
		config = Config.load();
	}

	static Config config() {
		return config;
	}

	static Library library() {
		return library;
	}

	static void library(Library loaded) {
		library = loaded;
	}

	static Notes notes() {
		return notes;
	}
}
