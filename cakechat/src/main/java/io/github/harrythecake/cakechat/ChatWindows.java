package io.github.harrythecake.cakechat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Style;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * All chat windows: one {@link ChatView} per configured window, the tab rules, unread counts per tab, and the mouse
 * (which window a hover, click or scroll is for; moving, resizing and tab clicks while the chat is open).
 */
public final class ChatWindows {
	private enum Drag { NONE, TAB, MOVE, RESIZE }

	/** A press on a tab turns into a move once the mouse travels this far. */
	private static final int DRAG_THRESHOLD = 3;

	private final MinecraftClient client;
	private final List<ChatView> views = new ArrayList<>();
	private final Channels channels = new Channels();
	/** Address of the server we're on ("" in singleplayer), for the tabs' {@code server} patterns. */
	private String server = "";
	private final Map<String, Pattern> serverPatterns = new HashMap<>();
	private TabRules rules;
	private int[] unread;
	/** The window last clicked; its selected tab's prefix goes in front of what you send. */
	private int active;

	private Drag drag = Drag.NONE;
	private ChatView dragView;
	private int dragTab;
	private double pressX;
	private double pressY;
	private int startX;
	private int startBottom;
	private int startWidth;
	private int startHeight;

	ChatWindows(MinecraftClient client) {
		this.client = client;
	}

	/** The windows or tabs in the config changed: new views and rules, every message sorted into tabs again. */
	public void rebuild() {
		List<ChatConfig.Window> config = CakeChat.config().windows;
		views.clear();
		for (ChatConfig.Window w : config) {
			w.selectedTab = Math.max(0, Math.min(w.selectedTab, w.tabs.size() - 1));
		}
		for (int i = 0; i < config.size(); i++) {
			views.add(new ChatView(client, this, i));
		}
		rules = TabRules.of(config);
		serverPatterns.clear();
		unread = new int[rules.size()];
		active = Math.min(active, views.size() - 1);
		drag = Drag.NONE;
		CakeChat.store().reroute();
	}

	TabRules rules() {
		if (rules == null) {
			rebuild();
		}
		return rules;
	}

	int unread(int window, int tab) {
		return unread[rules().index(window, tab)];
	}

	/** Re-wrap every window (timestamps, counters or Chat Settings changed). */
	public void invalidate() {
		views.forEach(ChatView::invalidate);
	}

	// ---- store events ----

	void added(ChatEntry entry) {
		List<ChatConfig.Window> config = CakeChat.config().windows;
		for (int w = 0; w < views.size(); w++) {
			ChatView view = views.get(w);
			view.added(entry);
			int selected = view.selected();
			for (int t = 0; t < config.get(w).tabs.size(); t++) {
				int i = rules.index(w, t);
				if (t != selected && entry.tabs.get(i)) {
					unread[i]++;
				}
			}
		}
	}

	void removed(ChatEntry entry) {
		views.forEach(v -> v.removed(entry));
	}

	void changed(ChatEntry entry) {
		views.forEach(v -> v.changed(entry));
	}

	void cleared() {
		views.forEach(ChatView::cleared);
		Arrays.fill(unread, 0);
	}

	// ---- drawing ----

	public void render(DrawContext context, int currentTick, int mouseX, int mouseY) {
		rules();
		for (ChatView view : views) {
			view.render(context, currentTick, mouseX, mouseY, drag != Drag.NONE && drag != Drag.TAB && view == dragView);
		}
	}

	// ---- vanilla ChatHud calls, sent to the right window ----

	/** Mouse wheel and Page Up/Down: the window under the mouse, else the one last clicked. */
	public void scroll(int amount) {
		target().scroll(amount);
	}

	public void resetScroll() {
		views.forEach(ChatView::resetScroll);
	}

	/** Page Up/Down scroll by the visible lines of the window they go to. */
	public int visibleLineCount() {
		return target().visibleLineCount();
	}

	@Nullable
	public Style styleAt(double x, double y) {
		ChatView view = viewAt(x, y);
		return view == null ? null : view.styleAt(x, y);
	}

	@Nullable
	public MessageIndicator indicatorAt(double x, double y) {
		ChatView view = viewAt(x, y);
		return view == null ? null : view.indicatorAt(x, y);
	}

	// ---- mouse in the open chat (ChatScreen) ----

	/** True when CakeChat used the click (tab, tab bar, grip, delayed-message bar). */
	public boolean mouseClicked(double x, double y, int button) {
		rules();
		if (button != 0) {
			return false;
		}
		for (int i = views.size() - 1; i >= 0; i--) {
			ChatView view = views.get(i);
			if (view.inQueueBar(x, y)) {
				client.getMessageHandler().process();
				return true;
			}
			if (view.inGrip(x, y)) {
				startDrag(Drag.RESIZE, view, x, y);
				return true;
			}
			int tab = view.tabAt(x, y);
			if (tab >= 0) {
				startDrag(Drag.TAB, view, x, y);
				dragTab = tab;
				return true;
			}
			if (view.inTabBar(x, y)) {
				startDrag(Drag.MOVE, view, x, y);
				return true;
			}
			if (view.inBox(x, y)) {
				active = i;
				return false;
			}
		}
		return false;
	}

	public boolean mouseDragged(double x, double y, int button) {
		if (drag == Drag.NONE || button != 0) {
			return false;
		}
		int dx = (int) Math.round(x - pressX);
		int dy = (int) Math.round(y - pressY);
		if (drag == Drag.TAB && (Math.abs(dx) >= DRAG_THRESHOLD || Math.abs(dy) >= DRAG_THRESHOLD)) {
			drag = Drag.MOVE;
		}
		if (drag == Drag.MOVE) {
			dragView.move(startX + dx, startBottom - dy);
		} else if (drag == Drag.RESIZE) {
			dragView.resize(startWidth + dx, startHeight - dy);
		}
		return true;
	}

	public boolean mouseReleased(double x, double y, int button) {
		if (drag == Drag.NONE || button != 0) {
			return false;
		}
		if (drag == Drag.TAB) {
			select(dragView, dragTab);
		}
		drag = Drag.NONE;
		CakeChat.saveConfigFile();
		return true;
	}

	private void startDrag(Drag kind, ChatView view, double x, double y) {
		drag = kind;
		dragView = view;
		active = view.index;
		pressX = x;
		pressY = y;
		startX = view.boxX();
		startBottom = view.window().bottom;
		startWidth = view.boxWidth();
		startHeight = view.boxHeight(true);
	}

	private void select(ChatView view, int tab) {
		ChatConfig.Window window = view.window();
		window.selectedTab = tab;
		unread[rules.index(view.index, tab)] = 0;
		view.resetScroll();
		view.invalidate();
		if (client.player != null) {
			for (String command : channels.select(window.tabs.get(tab).channel, System.currentTimeMillis())) {
				client.player.networkHandler.sendChatCommand(command);
			}
		}
	}

	/** A command the player typed (without the slash): their own {@code /join} becomes their channel. */
	public void typedCommand(String command) {
		channels.typed(command);
		rememberChannel();
	}

	/** A line from the server, before anything shows it: true = the reply to CakeChat's own /join, hide it. */
	public boolean hideServerLine(String text) {
		boolean hide = channels.serverLine(text, System.currentTimeMillis());
		rememberChannel();
		return hide;
	}

	/** Joined a world or server. Another server than before: start from that server's remembered channel. */
	public void joined(String address) {
		if (!address.equalsIgnoreCase(server)) {
			channels.reset(CakeChat.config().channelMemory.get(address.toLowerCase(Locale.ROOT)));
		}
		server = address;
		invalidate();
	}

	/** Keeps the player's own channel for this server in the config (a few bytes), so it survives a restart. */
	private void rememberChannel() {
		if (server.isEmpty()) {
			return;
		}
		Map<String, String> memory = CakeChat.config().channelMemory;
		String key = server.toLowerCase(Locale.ROOT);
		String own = channels.own();
		if (!Objects.equals(memory.get(key), own)) {
			if (own == null) {
				memory.remove(key);
			} else {
				memory.put(key, own);
			}
			CakeChat.saveConfigFile();
		}
	}

	/** Whether a tab's {@code server} pattern allows the current server (empty = every server, invalid = none). */
	boolean onServer(String pattern) {
		if (pattern.isEmpty()) {
			return true;
		}
		Pattern p = serverPatterns.computeIfAbsent(pattern, regex -> {
			try {
				return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			} catch (PatternSyntaxException e) {
				return null;
			}
		});
		return p != null && p.matcher(server).find();
	}

	/** What goes in front of a chat message sent now: the prefix of the active window's selected tab. */
	public String prefix() {
		if (views.isEmpty()) {
			return "";
		}
		ChatView view = views.get(Math.min(active, views.size() - 1));
		return view.window().tabs.get(view.selected()).prefix;
	}

	// ---- which window ----

	@Nullable
	private ChatView viewAt(double x, double y) {
		rules();
		for (int i = views.size() - 1; i >= 0; i--) {
			if (views.get(i).inBox(x, y)) {
				return views.get(i);
			}
		}
		return null;
	}

	private ChatView target() {
		rules();
		double x = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
		double y = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
		ChatView view = viewAt(x, y);
		return view != null ? view : views.get(Math.min(active, views.size() - 1));
	}
}
