package io.github.harrythecake.cakechat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads a player's ChatPlus setup ({@code config/chatplus/chatplus-v*.json}, the newest version) into CakeChat's config:
 * windows, tabs, history, compacting, timestamps, scrolling, find and keys. Only ChatPlus's config <em>format</em> is
 * used here, never its code (GPL-3.0). ChatPlus's files are only read. Plain Java (Gson), so tests can run it.
 */
public final class ChatPlusImport {
	/** What came over, for the message to the player. */
	public record Result(int windows, int tabs, List<String> formattedTabs, List<String> skippedTabs,
			@Nullable String peekKey, @Nullable String hideKey) {
	}

	private static final Pattern FILE = Pattern.compile("chatplus-v(\\d+(?:\\.\\d+)*)\\.json");
	private static final Pattern MATCH_ALL = Pattern.compile("(\\(\\?s\\))?\\.\\*");
	private static final Pattern CODES = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
	private static final Pattern TIME = Pattern.compile("\\{([^}]*)}");
	private static final String UNBOUND = "key.keyboard.unknown";
	/** ChatPlus's own default peek key: not taken over, so CakeChat's default (Left Alt) stays. */
	private static final String CHATPLUS_DEFAULT_PEEK = "key.keyboard.p";

	private ChatPlusImport() {
	}

	/** The newest {@code chatplus-v<version>.json} in the folder (by version number, not file date). */
	public static Optional<Path> newestFile(Path dir) {
		if (!Files.isDirectory(dir)) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.list(dir)) {
			Path best = null;
			int[] bestVersion = null;
			for (Path file : (Iterable<Path>) files::iterator) {
				Matcher m = FILE.matcher(file.getFileName().toString());
				if (!m.matches() || !Files.isRegularFile(file)) {
					continue;
				}
				int[] version = Arrays.stream(m.group(1).split("\\.")).mapToInt(ChatPlusImport::number).toArray();
				if (bestVersion == null || Arrays.compare(version, bestVersion) > 0) {
					best = file;
					bestVersion = version;
				}
			}
			return Optional.ofNullable(best);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static int number(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	/** The file's JSON, or empty when it can't be read. */
	public static Optional<JsonObject> read(Path file) {
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(in);
			return root.isJsonObject() ? Optional.of(root.getAsJsonObject()) : Optional.empty();
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return Optional.empty();
		}
	}

	/**
	 * Whether importing gives the player more than CakeChat's defaults: more than one window, or a tab that doesn't take
	 * every message. A ChatPlus setup with only its "All" tab would replace CakeChat's Monumenta tabs with less.
	 */
	public static boolean worthOffering(JsonObject root) {
		List<JsonObject> windows = windows(root);
		if (windows.size() > 1) {
			return true;
		}
		for (JsonObject window : windows) {
			List<JsonObject> tabs = tabs(window);
			if (tabs.size() > 1) {
				return true;
			}
			for (JsonObject tab : tabs) {
				if (!importPattern(combinedPattern(tab)).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Puts the ChatPlus setup into {@code config}. Windows and tabs replace CakeChat's (when ChatPlus has at least one
	 * window that isn't disabled); every other setting ChatPlus has is taken over; CakeChat-only settings stay.
	 *
	 * @param screenHeight the GUI's height now, to turn ChatPlus's window position (the box's bottom edge from the top)
	 *                     into CakeChat's (from the bottom); 0 if unknown
	 */
	public static Result apply(JsonObject root, ChatConfig config, int screenHeight) {
		int maxMessages = integer(root, "maxMessages", 0);
		config.history.maxMessages = maxMessages > 0 ? maxMessages : 0;

		config.compact.enabled = bool(root, "compactMessagesEnabled", config.compact.enabled);
		String format = string(root, "compactMessagesFormat", null);
		if (format != null) {
			String converted = CODES.matcher(format).replaceAll("").replace("%VALUE%", "{count}");
			config.compact.format = converted.contains("{count}") ? converted : config.compact.format;
		}
		String mode = string(root, "compactMessagesMode", null);
		if (mode != null) {
			config.compact.mode = mode.equalsIgnoreCase("LATEST") ? ChatConfig.Compact.Mode.LATEST : ChatConfig.Compact.Mode.WITHIN_SECONDS;
		}
		config.compact.searchLines = clamp(integer(root, "compactMessagesSearchAmount", config.compact.searchLines), 1, 100);
		config.compact.seconds = clamp(integer(root, "compactMessagesTimeSeconds", config.compact.seconds), 1, 3600);
		JsonObject compactSettings = object(root, "compactMessageSettings");
		if (compactSettings != null) {
			config.compact.compareStyle = bool(compactSettings, "style", config.compact.compareStyle);
		}

		JsonObject timestamps = object(root, "timestampSettings");
		if (timestamps != null) {
			config.timestamps.enabled = bool(timestamps, "enabled", config.timestamps.enabled);
			String timestampFormat = string(timestamps, "timestampFormat", "");
			Matcher m = TIME.matcher(timestampFormat);
			if (m.find() && ChatConfig.Timestamps.validPattern(m.group(1))) {
				config.timestamps.pattern = m.group(1);
			}
			String placement = string(timestamps, "chatTimestampModeType", "HOVER").toUpperCase(Locale.ROOT);
			config.timestamps.placement = placement.contains("HOVER") ? ChatConfig.Timestamps.Placement.HOVER
					: placement.contains("AFTER") || placement.contains("SUFFIX") ? ChatConfig.Timestamps.Placement.AFTER
					: ChatConfig.Timestamps.Placement.BEFORE;
		}

		config.scroll.invert = bool(root, "invertedScrolling", config.scroll.invert);
		config.scroll.scrollbar = bool(root, "scrollbarEnabled", config.scroll.scrollbar);
		config.find.matchCase = !bool(root, "findMessageIgnoreCase", !config.find.matchCase);
		String findMode = string(root, "findMessageDefaultMode", null);
		if (findMode != null) {
			config.find.regex = findMode.equalsIgnoreCase("REGEX");
		}

		String peek = string(root, "keyPeekChat", UNBOUND);
		String peekKey = peek.equals(UNBOUND) || peek.equals(CHATPLUS_DEFAULT_PEEK) ? null : peek;
		String hideKey = null;
		JsonObject hide = object(root, "hideChatToggleKey");
		if (hide != null && integer(hide, "modifier", 0) == 0 && !string(hide, "key", UNBOUND).equals(UNBOUND)) {
			hideKey = string(hide, "key", UNBOUND);
		}

		List<String> formatted = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		List<ChatConfig.Window> windows = new ArrayList<>();
		int tabCount = 0;
		for (JsonObject w : windows(root)) {
			JsonObject general = object(w, "generalSettings");
			if (general != null && bool(general, "disabled", false)) {
				continue;
			}
			ChatConfig.Window window = window(w, general, screenHeight);
			for (JsonObject t : tabs(w)) {
				List<JsonObject> settings = settings(t);
				JsonObject first = settings.get(0);
				ChatConfig.Tab tab = new ChatConfig.Tab();
				String name = string(first, "name", "").trim();
				tab.name = name.isEmpty() ? "Tab" : name;
				tab.pattern = importPattern(combinedPattern(t));
				if (TabRules.patternError(tab.pattern) != null) {
					skipped.add(tab.name);
					continue;
				}
				if (settings.stream().anyMatch(s -> bool(s, "formatted", false))) {
					formatted.add(tab.name);
				}
				tab.server = importPattern(serverPattern(first));
				if (TabRules.patternError(tab.server) != null) {
					tab.server = "";
				}
				tab.prefix = string(first, "autoPrefix", "");
				tab.priority = integer(first, "priority", 0);
				tab.alwaysAdd = bool(first, "alwaysAdd", false);
				tab.skipOthers = bool(first, "skipOthers", false);
				window.tabs.add(tab);
			}
			if (window.tabs.isEmpty()) {
				window.tabs.add(ChatConfig.Tab.named(ChatConfig.Tab.KEY_PREFIX + "all", ""));
			}
			JsonObject tabSettings = object(w, "tabSettings");
			int selected = tabSettings == null ? 0 : integer(tabSettings, "selectedTabIndex", 0);
			window.selectedTab = clamp(selected, 0, window.tabs.size() - 1);
			tabCount += window.tabs.size();
			windows.add(window);
		}
		if (!windows.isEmpty()) {
			config.windows = windows;
		}
		return new Result(windows.size(), tabCount, formatted, skipped, peekKey, hideKey);
	}

	private static ChatConfig.Window window(JsonObject w, @Nullable JsonObject general, int screenHeight) {
		ChatConfig.Window window = new ChatConfig.Window();
		window.tabs = new ArrayList<>();
		JsonObject renderer = object(w, "renderer");
		if (renderer == null) {
			return window;
		}
		int width = integer(renderer, "width", -1);
		int height = integer(renderer, "height", -1);
		window.x = Math.max(0, integer(renderer, "x", 0));
		window.width = width > 0 ? Math.max(60, width) : -1;
		if (height > 0) {
			window.height = Math.max(20, height);
			double unfocused = general == null ? 1.0 : number(general, "unfocusedHeight", 1.0);
			window.unfocusedHeight = clamp((int) Math.round(window.height * unfocused), 1, window.height);
		}
		int y = integer(renderer, "y", -1);
		if (screenHeight > 0 && y > 0) {
			int boxHeight = window.height > 0 ? window.height : 0;
			window.bottom = clamp(screenHeight - y, 28, Math.max(28, screenHeight - boxHeight));
		}
		return window;
	}

	/** "All" (empty, or .* / (?s).*) becomes CakeChat's empty pattern; anything else must match the whole text, like in ChatPlus. */
	static String importPattern(String pattern) {
		if (pattern == null || pattern.isBlank() || MATCH_ALL.matcher(pattern.trim()).matches()) {
			return "";
		}
		return "^(?:" + pattern + ")$";
	}

	/** A tab can have several rules (ChatPlus 2.7+); CakeChat's tab takes a message any of them matches. */
	private static String combinedPattern(JsonObject tab) {
		List<String> patterns = new ArrayList<>();
		for (JsonObject s : settings(tab)) {
			String p = string(s, "pattern", "");
			if (p.isBlank() || MATCH_ALL.matcher(p.trim()).matches()) {
				return "";
			}
			patterns.add(p);
		}
		if (patterns.size() == 1) {
			return patterns.get(0);
		}
		return String.join("|", patterns.stream().map(p -> "(?:" + p + ")").toList());
	}

	/** 2.7+: {@code serverPattern.pattern}; 2.5: {@code serverTabPatterns} (strings or objects with a pattern). */
	private static String serverPattern(JsonObject setting) {
		JsonObject server = object(setting, "serverPattern");
		if (server != null) {
			return string(server, "pattern", "");
		}
		JsonElement list = setting.get("serverTabPatterns");
		if (list == null || !list.isJsonArray()) {
			return "";
		}
		List<String> patterns = new ArrayList<>();
		for (JsonElement e : list.getAsJsonArray()) {
			String p = e.isJsonPrimitive() ? e.getAsString() : e.isJsonObject() ? string(e.getAsJsonObject(), "pattern", "") : "";
			if (!p.isBlank()) {
				patterns.add("(?:" + p + ")");
			}
		}
		return String.join("|", patterns);
	}

	private static List<JsonObject> windows(JsonObject root) {
		return objects(root.get("chatWindows"));
	}

	private static List<JsonObject> tabs(JsonObject window) {
		JsonObject tabSettings = object(window, "tabSettings");
		return tabSettings == null ? List.of() : objects(tabSettings.get("tabs"));
	}

	/** 2.7+ keeps a tab's rules in {@code settings}; 2.5 has them on the tab itself. */
	private static List<JsonObject> settings(JsonObject tab) {
		List<JsonObject> settings = objects(tab.get("settings"));
		return settings.isEmpty() ? List.of(tab) : settings;
	}

	private static List<JsonObject> objects(@Nullable JsonElement e) {
		List<JsonObject> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			JsonArray array = e.getAsJsonArray();
			for (JsonElement item : array) {
				if (item.isJsonObject()) {
					out.add(item.getAsJsonObject());
				}
			}
		}
		return out;
	}

	@Nullable
	private static JsonObject object(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
	}

	private static int integer(JsonObject o, String key, int fallback) {
		return (int) Math.round(number(o, key, fallback));
	}

	private static double number(JsonObject o, String key, double fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
