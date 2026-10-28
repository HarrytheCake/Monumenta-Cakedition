package io.github.harrythecake.cakechat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;

/**
 * config/cakechat/config.json. Plain Java (no Minecraft classes), so tests can load it.
 * Fields missing from the file keep the defaults below; unknown fields are ignored.
 */
public final class ChatConfig {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Bumped when a field changes meaning, so {@link #load} can migrate old files. */
	public int configVersion = 1;
	public History history = new History();
	public Compact compact = new Compact();
	public Timestamps timestamps = new Timestamps();
	public Scroll scroll = new Scroll();

	public static final class History {
		/** Lines kept per window; 0 = no limit (what More Chat History gave the pack). */
		public int maxMessages = 0;
		/** Keep chat when leaving a world or server (what Don't Clear Chat History gave the pack). */
		public boolean keepOnDisconnect = true;
	}

	/** Repeated lines collapse into one with a counter. */
	public static final class Compact {
		public boolean enabled = true;
		/** Appended to the kept line; {count} = how many times it arrived. */
		public String format = " (x{count})";
		public Mode mode = Mode.LATEST;
		/** LATEST: how many of the newest lines are compared with a new one. */
		public int searchLines = 1;
		/** WITHIN_SECONDS: a repeat counts only if the earlier copy arrived this recently. */
		public int seconds = 60;
		/** Also compare colours and formatting, not only the words. */
		public boolean compareStyle = false;

		public enum Mode { LATEST, WITHIN_SECONDS }
	}

	public static final class Timestamps {
		public boolean enabled = false;
		/** java.time.format.DateTimeFormatter pattern. */
		public String pattern = "HH:mm";
		public Placement placement = Placement.HOVER;

		public enum Placement { HOVER, BEFORE, AFTER }

		public static boolean validPattern(String pattern) {
			try {
				DateTimeFormatter.ofPattern(pattern);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
	}

	public static final class Scroll {
		/** Lines per mouse-wheel notch (Shift: always 1). Vanilla: 7. */
		public int linesPerNotch = 7;
		public boolean invert = false;
		public boolean scrollbar = true;
	}

	/** Reads the file, or returns the defaults when it doesn't exist. A broken file is kept aside as {@code .broken}. */
	public static ChatConfig load(Path file) {
		if (!Files.exists(file)) {
			return new ChatConfig();
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			ChatConfig config = GSON.fromJson(in, ChatConfig.class);
			return config == null ? new ChatConfig() : config.fillMissing();
		} catch (IOException | JsonParseException e) {
			LoggerFactory.getLogger(CakeChat.MOD_ID).warn("Unreadable {}, using defaults: {}", file, e.toString());
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// the next save overwrites it anyway
			}
			return new ChatConfig();
		}
	}

	/** Writes through a temp file, so a crash mid-write never leaves half a config. */
	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(this, out);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** A section written as {@code null} (or an unknown enum name, which Gson reads as null) falls back to its default. */
	private ChatConfig fillMissing() {
		ChatConfig defaults = new ChatConfig();
		if (history == null) history = defaults.history;
		if (compact == null) compact = defaults.compact;
		if (compact.mode == null) compact.mode = defaults.compact.mode;
		if (compact.format == null) compact.format = defaults.compact.format;
		if (timestamps == null) timestamps = defaults.timestamps;
		if (timestamps.placement == null) timestamps.placement = defaults.timestamps.placement;
		if (timestamps.pattern == null) timestamps.pattern = defaults.timestamps.pattern;
		if (scroll == null) scroll = defaults.scroll;
		return this;
	}
}
