package io.github.harrythecake.cakecosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Plain JSON state files (choices.json, unlocked.json): read with a fallback, written atomically. */
final class JsonFiles {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	static <T> T read(Path file, Class<T> type, Supplier<T> fallback) {
		if (!Files.exists(file)) {
			return fallback.get();
		}
		try {
			T t = GSON.fromJson(Files.readString(file), type);
			return t == null ? fallback.get() : t;
		} catch (IOException | JsonParseException e) {
			CakeCosmeticCore.LOG.warn("CakeCosmetic: can't read {}, starting fresh: {}", file.getFileName(), e.toString());
			return fallback.get();
		}
	}

	static void write(Path file, Object value) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(value) + "\n");
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			CakeCosmeticCore.LOG.warn("CakeCosmetic: can't write {}: {}", file, e.toString());
		}
	}
}
