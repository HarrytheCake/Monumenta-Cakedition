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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * config/cakechat/config.json. Plain Java (no Minecraft classes), so tests can load it.
 * Fields missing from the file keep the defaults below; unknown fields are ignored.
 */
public final class ChatConfig {
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Bumped when a field changes meaning, so {@link #load} can migrate old files. */
	public int configVersion = 2;
	public History history = new History();
	public Compact compact = new Compact();
	public Timestamps timestamps = new Timestamps();
	public Scroll scroll = new Scroll();
	/** The first window is the main one (where the delayed-message bar shows); there is always at least one. */
	public List<Window> windows = new ArrayList<>(List.of(Window.main()));
	/** The player's own chat channel per server address (see {@link Channels}), so All can join it again after a restart. */
	public Map<String, String> channelMemory = new HashMap<>();

	public static final class History {
		/** Messages kept; 0 = no limit (what More Chat History gave the pack). */
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

	/**
	 * A chat box. Sizes and positions are GUI pixels; -1 = follow Minecraft's Chat Settings, which the first
	 * window does until it's resized.
	 */
	public static final class Window {
		/** From the screen's left edge to the box's left edge. */
		public int x = 0;
		/** From the screen's bottom edge to the box's bottom edge (vanilla: 40, above the input line). */
		public int bottom = 40;
		public int width = -1;
		/** While the chat is open. */
		public int height = -1;
		/** While it's closed. */
		public int unfocusedHeight = -1;
		public int selectedTab = 0;
		public List<Tab> tabs = new ArrayList<>(List.of(new Tab()));

		/** The default main window: Monumenta's channels as tabs (shown only on Monumenta) after one for everything. */
		public static Window main() {
			Window w = new Window();
			w.tabs = new ArrayList<>(List.of(
					Tab.named(Tab.KEY_PREFIX + "all", ""),
					Tab.monumenta("global", "^<g> ", "g"),
					Tab.monumenta("lfg", "^<lfg> ", "lfg"),
					Tab.monumenta("trade", "^<tr> ", "tr"),
					Tab.monumenta("zh", "^<zh> ", "zh"),
					// a guild's channel: any <name> except the shared ones, then a [guild tag]
					Tab.monumenta("guild", "^(?!<(?:lfg|tr|zh|es|mh)>)<.{2,5}.*>.*\\[.{2,5}\\]", ""),
					Tab.monumenta("whisper", "whispers? to", "")));
			return w;
		}

		/** A second (third…) window: to the right of the main one, with its own empty-pattern tab. */
		public static Window extra(int index) {
			Window w = new Window();
			w.x = 340;
			w.bottom = 40 + (index - 1) * 110;
			w.width = 240;
			w.height = 90;
			w.unfocusedHeight = 90;
			return w;
		}
	}

	/** Which messages a tab shows; see {@link TabRules} for how tabs share them. */
	public static final class Tab {
		public String name = KEY_PREFIX + "all";
		/** Java regex, found anywhere in the message's text; empty = every message. */
		public String pattern = "";
		/** Higher first. */
		public int priority = 0;
		/** A message this tab takes isn't offered to lower-priority tabs in the same window… */
		public boolean skipOthers = false;
		/** …except to tabs with this on. */
		public boolean alwaysAdd = false;
		/** Put in front of what you send from this tab (e.g. "/g "); commands you type are sent unchanged. */
		public String prefix = "";
		/** Server chat channel this tab switches to ({@code /join <channel>}; back on a tab without one: {@code /leave}). See {@link Channels}. */
		public String channel = "";
		/** Java regex found in the server's address; the tab only shows (and joins) there. Empty = every server. */
		public String server = "";

		/** A name starting with this is a translation key (the default tabs), shown in the game's language. */
		public static final String KEY_PREFIX = "cakechat.tab.";
		static final String MONUMENTA = "(^|\\.)playmonumenta\\.com(:\\d+)?$";

		public static Tab named(String name, String pattern) {
			Tab t = new Tab();
			t.name = name;
			t.pattern = pattern;
			return t;
		}

		static Tab monumenta(String key, String pattern, String channel) {
			Tab t = named(KEY_PREFIX + key, pattern);
			t.channel = channel;
			t.server = MONUMENTA;
			return t;
		}
	}

	/** Reads the file, or returns the defaults when it doesn't exist. A broken file is kept aside as {@code .broken}. */
	public static ChatConfig load(Path file) {
		if (!Files.exists(file)) {
			return new ChatConfig();
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			ChatConfig config = GSON.fromJson(in, ChatConfig.class);
			return config == null ? new ChatConfig() : config.fillMissing().migrate();
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

	/** Older files: 1 → 2 adds the Chinese channel tab after Trade in a main window that has the default tabs. */
	private ChatConfig migrate() {
		if (configVersion < 2) {
			List<Tab> tabs = windows.get(0).tabs;
			int trade = -1;
			boolean hasZh = false;
			for (int i = 0; i < tabs.size(); i++) {
				String name = tabs.get(i).name;
				if (name.equals(Tab.KEY_PREFIX + "trade")) trade = i;
				if (name.equals(Tab.KEY_PREFIX + "zh")) hasZh = true;
			}
			if (trade >= 0 && !hasZh) {
				tabs.add(trade + 1, Tab.monumenta("zh", "^<zh> ", "zh"));
				if (windows.get(0).selectedTab > trade) windows.get(0).selectedTab++;
			}
			configVersion = 2;
		}
		return this;
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
		if (channelMemory == null) channelMemory = new HashMap<>();
		if (windows == null) windows = new ArrayList<>();
		windows.removeIf(Objects::isNull);
		if (windows.isEmpty()) windows.add(Window.main());
		for (Window w : windows) {
			if (w.tabs == null) w.tabs = new ArrayList<>();
			w.tabs.removeIf(Objects::isNull);
			if (w.tabs.isEmpty()) w.tabs.add(new Tab());
			for (Tab t : w.tabs) {
				if (t.name == null) t.name = "Tab";
				if (t.pattern == null) t.pattern = "";
				if (t.prefix == null) t.prefix = "";
				if (t.channel == null) t.channel = "";
				if (t.server == null) t.server = "";
			}
			w.selectedTab = Math.max(0, Math.min(w.selectedTab, w.tabs.size() - 1));
		}
		return this;
	}
}
