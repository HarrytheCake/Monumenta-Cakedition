package io.github.harrythecake.cakeupdater;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * A player's ChatPlus setup ({@code config/chatplus/chatplus-v*.json}, the newest version) turned into CakeChat
 * settings, for the import from another install ({@link MoveIn}). Moved here from CakeChat 0.3.0 (user, 2026-11-03).
 *
 * <p>The result is a patch for CakeChat's {@code config/cakechat/config.json}: windows and tabs (they replace CakeChat's
 * when ChatPlus has at least one window that isn't disabled), history, compacting, timestamps, scrolling and find.
 * Settings ChatPlus doesn't have stay as they are. Only ChatPlus's config <em>format</em> is used, never its code
 * (GPL-3.0); its files are only read. Plain Java (Gson), so tests can run it and the pre-launch apply can use it.
 * The field names are CakeChat's {@code ChatConfig}: keep them in step.
 */
final class ChatPlusImport {
	/** What came over, for the message to the player. Keys are Minecraft key names ({@code key.keyboard.h}). */
	record Result(JsonObject patch, int windows, int tabs, List<String> formattedTabs, List<String> skippedTabs,
			@Nullable String peekKey, @Nullable String hideKey) {
	}

	/** CakeChat's key bindings as options.txt names them. */
	static final String PEEK_OPTION = "key_key.cakechat.peek";
	static final String HIDE_OPTION = "key_key.cakechat.hide";

	private static final Pattern FILE = Pattern.compile("chatplus-v(\\d+(?:\\.\\d+)*)\\.json");
	private static final Pattern MATCH_ALL = Pattern.compile("(\\(\\?s\\))?\\.\\*");
	private static final Pattern CODES = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");
	private static final Pattern TIME = Pattern.compile("\\{([^}]*)}");
	private static final String UNBOUND = "key.keyboard.unknown";
	/** ChatPlus's own default peek key: not taken over, so CakeChat's default (Left Alt) stays. */
	private static final String CHATPLUS_DEFAULT_PEEK = "key.keyboard.p";
	/** CakeChat's default tab name for "everything" (a translation key, shown in the game's language). */
	private static final String ALL_TAB = "cakechat.tab.all";

	private ChatPlusImport() {
	}

	/** The newest {@code chatplus-v<version>.json} in the folder (by version number, not file date). */
	static Optional<Path> newestFile(Path dir) {
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
	static Optional<JsonObject> read(Path file) {
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
	static boolean worthImporting(JsonObject root) {
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
	 * The CakeChat settings for this ChatPlus setup.
	 *
	 * @param screenHeight the GUI's height now, to turn ChatPlus's window position (the box's bottom edge from the top)
	 *                     into CakeChat's (from the bottom); 0 if unknown
	 */
	static Result convert(JsonObject root, int screenHeight) {
		JsonObject patch = new JsonObject();

		if (root.has("maxMessages")) {
			int maxMessages = integer(root, "maxMessages", 0);
			section(patch, "history").addProperty("maxMessages", Math.max(maxMessages, 0));
		}

		JsonObject compact = new JsonObject();
		putBool(compact, "enabled", root, "compactMessagesEnabled");
		String format = string(root, "compactMessagesFormat", null);
		if (format != null) {
			String converted = CODES.matcher(format).replaceAll("").replace("%VALUE%", "{count}");
			if (converted.contains("{count}")) {
				compact.addProperty("format", converted);
			}
		}
		String mode = string(root, "compactMessagesMode", null);
		if (mode != null) {
			compact.addProperty("mode", mode.equalsIgnoreCase("LATEST") ? "LATEST" : "WITHIN_SECONDS");
		}
		if (root.has("compactMessagesSearchAmount")) {
			compact.addProperty("searchLines", clamp(integer(root, "compactMessagesSearchAmount", 1), 1, 100));
		}
		if (root.has("compactMessagesTimeSeconds")) {
			compact.addProperty("seconds", clamp(integer(root, "compactMessagesTimeSeconds", 60), 1, 3600));
		}
		JsonObject compactSettings = object(root, "compactMessageSettings");
		if (compactSettings != null) {
			putBool(compact, "compareStyle", compactSettings, "style");
		}
		addIfAny(patch, "compact", compact);

		JsonObject timestamps = object(root, "timestampSettings");
		if (timestamps != null) {
			JsonObject t = new JsonObject();
			putBool(t, "enabled", timestamps, "enabled");
			Matcher m = TIME.matcher(string(timestamps, "timestampFormat", ""));
			if (m.find() && validTime(m.group(1))) {
				t.addProperty("pattern", m.group(1));
			}
			String placement = string(timestamps, "chatTimestampModeType", "HOVER").toUpperCase(Locale.ROOT);
			t.addProperty("placement", placement.contains("HOVER") ? "HOVER"
					: placement.contains("AFTER") || placement.contains("SUFFIX") ? "AFTER" : "BEFORE");
			patch.add("timestamps", t);
		}

		JsonObject scroll = new JsonObject();
		putBool(scroll, "invert", root, "invertedScrolling");
		putBool(scroll, "scrollbar", root, "scrollbarEnabled");
		addIfAny(patch, "scroll", scroll);

		JsonObject find = new JsonObject();
		if (isBool(root, "findMessageIgnoreCase")) {
			find.addProperty("matchCase", !root.get("findMessageIgnoreCase").getAsBoolean());
		}
		String findMode = string(root, "findMessageDefaultMode", null);
		if (findMode != null) {
			find.addProperty("regex", findMode.equalsIgnoreCase("REGEX"));
		}
		addIfAny(patch, "find", find);

		String peek = string(root, "keyPeekChat", UNBOUND);
		String peekKey = peek.equals(UNBOUND) || peek.equals(CHATPLUS_DEFAULT_PEEK) ? null : peek;
		String hideKey = null;
		JsonObject hide = object(root, "hideChatToggleKey");
		if (hide != null && integer(hide, "modifier", 0) == 0 && !string(hide, "key", UNBOUND).equals(UNBOUND)) {
			hideKey = string(hide, "key", UNBOUND);
		}

		List<String> formatted = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		JsonArray windows = new JsonArray();
		int tabCount = 0;
		for (JsonObject w : windows(root)) {
			JsonObject general = object(w, "generalSettings");
			if (general != null && bool(general, "disabled", false)) {
				continue;
			}
			JsonObject window = window(w, general, screenHeight);
			JsonArray tabs = new JsonArray();
			for (JsonObject t : tabs(w)) {
				List<JsonObject> settings = settings(t);
				JsonObject first = settings.get(0);
				String name = string(first, "name", "").trim();
				name = name.isEmpty() ? "Tab" : name;
				String pattern = importPattern(combinedPattern(t));
				if (!validRegex(pattern)) {
					skipped.add(name);
					continue;
				}
				if (settings.stream().anyMatch(s -> bool(s, "formatted", false))) {
					formatted.add(name);
				}
				String server = importPattern(serverPattern(first));
				tabs.add(tab(name, pattern, integer(first, "priority", 0), bool(first, "skipOthers", false),
						bool(first, "alwaysAdd", false), string(first, "autoPrefix", ""), validRegex(server) ? server : ""));
			}
			if (tabs.isEmpty()) {
				tabs.add(tab(ALL_TAB, "", 0, false, false, "", ""));
			}
			JsonObject tabSettings = object(w, "tabSettings");
			int selected = tabSettings == null ? 0 : integer(tabSettings, "selectedTabIndex", 0);
			window.addProperty("selectedTab", clamp(selected, 0, tabs.size() - 1));
			window.add("tabs", tabs);
			tabCount += tabs.size();
			windows.add(window);
		}
		if (!windows.isEmpty()) {
			patch.add("windows", windows);
		}
		return new Result(patch, windows.size(), tabCount, formatted, skipped, peekKey, hideKey);
	}

	/** Puts the patch into CakeChat's config: objects are merged key by key, anything else (windows) is replaced. */
	static void merge(JsonObject config, JsonObject patch) {
		for (Map.Entry<String, JsonElement> e : patch.entrySet()) {
			JsonElement current = config.get(e.getKey());
			if (e.getValue().isJsonObject() && current != null && current.isJsonObject()) {
				merge(current.getAsJsonObject(), e.getValue().getAsJsonObject());
			} else {
				config.add(e.getKey(), e.getValue().deepCopy());
			}
		}
	}

	private static JsonObject window(JsonObject w, @Nullable JsonObject general, int screenHeight) {
		int x = 0;
		int bottom = 40;
		int width = -1;
		int height = -1;
		int unfocusedHeight = -1;
		JsonObject renderer = object(w, "renderer");
		if (renderer != null) {
			int rw = integer(renderer, "width", -1);
			int rh = integer(renderer, "height", -1);
			x = Math.max(0, integer(renderer, "x", 0));
			width = rw > 0 ? Math.max(60, rw) : -1;
			if (rh > 0) {
				height = Math.max(20, rh);
				double unfocused = general == null ? 1.0 : number(general, "unfocusedHeight", 1.0);
				unfocusedHeight = clamp((int) Math.round(height * unfocused), 1, height);
			}
			int y = integer(renderer, "y", -1);
			if (screenHeight > 0 && y > 0) {
				int boxHeight = Math.max(height, 0);
				bottom = clamp(screenHeight - y, 28, Math.max(28, screenHeight - boxHeight));
			}
		}
		JsonObject window = new JsonObject();
		window.addProperty("x", x);
		window.addProperty("bottom", bottom);
		window.addProperty("width", width);
		window.addProperty("height", height);
		window.addProperty("unfocusedHeight", unfocusedHeight);
		return window;
	}

	private static JsonObject tab(String name, String pattern, int priority, boolean skipOthers, boolean alwaysAdd,
			String prefix, String server) {
		JsonObject tab = new JsonObject();
		tab.addProperty("name", name);
		tab.addProperty("pattern", pattern);
		tab.addProperty("priority", priority);
		tab.addProperty("skipOthers", skipOthers);
		tab.addProperty("alwaysAdd", alwaysAdd);
		tab.addProperty("prefix", prefix);
		tab.addProperty("channel", "");
		tab.addProperty("server", server);
		return tab;
	}

	/** "All" (empty, or .* / (?s).*) becomes CakeChat's empty pattern; anything else must match the whole text, like in ChatPlus. */
	static String importPattern(String pattern) {
		if (pattern == null || pattern.isBlank() || MATCH_ALL.matcher(pattern.trim()).matches()) {
			return "";
		}
		return "^(?:" + pattern + ")$";
	}

	private static boolean validRegex(String regex) {
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	private static boolean validTime(String pattern) {
		try {
			DateTimeFormatter.ofPattern(pattern);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
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

	private static JsonObject section(JsonObject patch, String key) {
		if (!patch.has(key)) {
			patch.add(key, new JsonObject());
		}
		return patch.getAsJsonObject(key);
	}

	private static void addIfAny(JsonObject patch, String key, JsonObject section) {
		if (section.size() > 0) {
			patch.add(key, section);
		}
	}

	private static void putBool(JsonObject to, String key, JsonObject from, String fromKey) {
		if (isBool(from, fromKey)) {
			to.addProperty(key, from.get(fromKey).getAsBoolean());
		}
	}

	private static boolean isBool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
	}

	private static List<JsonObject> objects(@Nullable JsonElement e) {
		List<JsonObject> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement item : e.getAsJsonArray()) {
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
		return isBool(o, key) ? o.get(key).getAsBoolean() : fallback;
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
