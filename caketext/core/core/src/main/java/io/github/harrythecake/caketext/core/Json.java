package io.github.harrythecake.caketext.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.harrythecake.caketext.core.strings.StringsFile;

/** Loads and saves the small JSON settings files under {@code config/caketext}. */
public final class Json {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Json() {
	}

	/** Reads {@code path}, or returns {@code fallback} (and logs) when missing or broken. Missing fields keep their defaults. */
	public static <T> T load(Path path, Class<T> type, Supplier<T> fallback) {
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				T value = GSON.fromJson(r, type);
				if (value != null) {
					return value;
				}
			} catch (IOException | RuntimeException e) {
				CakeText.LOG.warn("Could not read {}, using defaults", path, e);
			}
		}
		return fallback.get();
	}

	/** Deep copy through JSON, for editing settings without touching the live object. */
	public static <T> T copy(T value, Class<T> type) {
		return GSON.fromJson(GSON.toJson(value), type);
	}

	public static void save(Path path, Object value) {
		try {
			StringsFile.writeAtomically(path, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			CakeText.LOG.warn("Could not save {}", path, e);
		}
	}
}
