package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.joml.Matrix3x2f;

/**
 * One chat window: the messages of its selected tab, drawn like vanilla's chat box (layout, fade, indicator bar),
 * at the window's own position and size, with a tab bar under it while the chat is open.
 * Scale, opacity, background and line spacing come from Minecraft's Chat Settings for every window.
 */
public final class ChatView {
	/** Height of the tab bar under the box, in GUI pixels. */
	static final int TAB_BAR_HEIGHT = 12;
	/** Size of the resize grip at the box's top-right corner (drawn only while window editing is on). */
	static final int GRIP = 6;
	private static final int MIN_WIDTH = 60;
	private static final int MIN_HEIGHT = 20;
	private static final int MIN_BOTTOM = 14 + TAB_BAR_HEIGHT + 2;

	private record Line(ChatEntry entry, FormattedCharSequence text, boolean endOfEntry) {
	}

	private final Minecraft client;
	private final ChatWindows windows;
	final int index;
	/** Wrapped lines of the selected tab, oldest first; {@link #newest} indexes from the bottom like vanilla's visibleMessages. */
	private final List<Line> lines = new ArrayList<>();
	private int scrolledLines;
	private boolean hasUnreadNewMessages;
	/** Width the lines were wrapped for; -1 = wrap everything again before the next draw. */
	private int wrapWidth = -1;
	private String timePattern;
	private DateTimeFormatter timeFormat;

	ChatView(Minecraft client, ChatWindows windows, int index) {
		this.client = client;
		this.windows = windows;
		this.index = index;
	}

	ChatConfig.Window window() {
		return CakeChat.config().windows.get(index);
	}

	/** Settings or the selected tab changed: wrap everything again before the next draw. */
	public void invalidate() {
		wrapWidth = -1;
	}

	/** In the selected tab, and found by the find bar while it searches. */
	private boolean shows(ChatEntry entry) {
		if (!entry.tabs.get(windows.rules().index(index, selected()))) {
			return false;
		}
		FindBar findBar = windows.findBar();
		return !findBar.searching() || findBar.find().matches(entry.content.getString());
	}

	/** Whether the tab shows on this server (its {@code server} pattern). */
	boolean tabShown(int tab) {
		return windows.onServer(window().tabs.get(tab).server);
	}

	/** The selected tab, or the first shown one when it's hidden on this server. */
	int selected() {
		ChatConfig.Window w = window();
		if (tabShown(w.selectedTab)) {
			return w.selectedTab;
		}
		for (int t = 0; t < w.tabs.size(); t++) {
			if (tabShown(t)) {
				return t;
			}
		}
		return w.selectedTab;
	}

	// ---- geometry (GUI pixels) ----

	int boxX() {
		return window().x;
	}

	/** GUI y of the box's bottom edge. */
	int boxBottom() {
		return client.getWindow().getGuiScaledHeight() - window().bottom;
	}

	int boxWidth() {
		int w = window().width;
		return w >= 0 ? w : ChatComponent.getWidth(client.options.chatWidth().get());
	}

	int boxHeight(boolean focused) {
		int h = focused ? window().height : window().unfocusedHeight;
		if (h >= 0) {
			return h;
		}
		double option = focused ? client.options.chatHeightFocused().get() : client.options.chatHeightUnfocused().get();
		return (int) (ChatComponent.getHeight(option) * scale());
	}

	int boxTop() {
		return boxBottom() - boxHeight(true);
	}

	private int visibleLineCount(boolean focused) {
		return Math.max(1, (int) (boxHeight(focused) / (lineHeight() * scale())));
	}

	int visibleLineCount() {
		return visibleLineCount(isChatFocused() || windows.peeking());
	}

	boolean inBox(double x, double y) {
		return x >= boxX() && x < boxX() + boxWidth() && y >= boxTop() && y < boxBottom();
	}

	/** Only while window editing is on (the grip is drawn only then). */
	boolean inGrip(double x, double y) {
		if (!CakeChat.config().windowEditing) {
			return false;
		}
		int right = boxX() + boxWidth();
		return x >= right - GRIP && x < right + 2 && y >= boxTop() - 2 && y < boxTop() + GRIP;
	}

	boolean inTabBar(double x, double y) {
		int top = boxBottom() + 1;
		return y >= top && y < top + TAB_BAR_HEIGHT && x >= boxX() && x < boxX() + Math.max(boxWidth(), tabsWidth());
	}

	/** The tab under the point, or -1. */
	int tabAt(double x, double y) {
		if (!inTabBar(x, y)) {
			return -1;
		}
		int left = boxX();
		List<ChatConfig.Tab> tabs = window().tabs;
		for (int t = 0; t < tabs.size(); t++) {
			if (!tabShown(t)) {
				continue;
			}
			int w = tabWidth(t);
			if (x >= left && x < left + w) {
				return t;
			}
			left += w + 1;
		}
		return -1;
	}

	/** Rectangle of the delayed-message bar ("N chat messages waiting"), main window only: below the tab bar. */
	/** Only while the bar is drawn: processing an empty queue throws (vanilla checks the count too). */
	boolean inQueueBar(double x, double y) {
		if (client.getChatListener().queueSize() <= 0L) {
			return false;
		}
		int top = boxBottom() + 1 + TAB_BAR_HEIGHT + 1;
		return index == 0 && x >= boxX() && x < boxX() + boxWidth() && y >= top && y < top + 9;
	}

	void move(int x, int bottom) {
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		ChatConfig.Window w = window();
		w.x = Mth.clamp(x, 0, Math.max(0, screenW - boxWidth()));
		// keep the box on screen and its tab bar above the input line (14 px high at the bottom)
		w.bottom = Mth.clamp(bottom, MIN_BOTTOM, Math.max(MIN_BOTTOM, screenH - boxHeight(true)));
	}

	void resize(int width, int height) {
		ChatConfig.Window w = window();
		int screenW = client.getWindow().getGuiScaledWidth();
		// a window that followed Chat Settings keeps its closed height
		int unfocused = boxHeight(false);
		w.width = Mth.clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, screenW - w.x));
		w.height = Mth.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, boxBottom()));
		w.unfocusedHeight = Math.min(unfocused, w.height);
		invalidate();
	}

	// ---- store events ----

	void added(ChatEntry entry) {
		if (wrapWidth < 0 || !shows(entry)) {
			return;
		}
		List<Line> wrapped = wrap(entry);
		if (isChatFocused() && scrolledLines > 0) {
			// keep the lines being read in place, like vanilla
			hasUnreadNewMessages = true;
			scrolledLines += wrapped.size();
		}
		lines.addAll(wrapped);
	}

	void removed(ChatEntry entry) {
		replace(entry, List.of());
	}

	void changed(ChatEntry entry) {
		if (wrapWidth >= 0) {
			replace(entry, shows(entry) ? wrap(entry) : List.of());
		}
	}

	void cleared() {
		lines.clear();
		resetScroll();
	}

	/** Swaps an entry's lines (they're contiguous), keeping the scroll position on the same text. */
	private void replace(ChatEntry entry, List<Line> with) {
		int to = -1;
		if (!lines.isEmpty() && lines.get(0).entry == entry) {
			// history limit: the oldest entry goes
			to = 0;
			while (to < lines.size() && lines.get(to).entry == entry) {
				to++;
			}
		} else {
			for (int i = lines.size() - 1; i >= 0; i--) {
				if (lines.get(i).entry == entry) {
					to = i + 1;
					break;
				}
			}
		}
		if (to < 0) {
			return;
		}
		int from = to - 1;
		while (from > 0 && lines.get(from - 1).entry == entry) {
			from--;
		}
		int belowFromBottom = lines.size() - to;
		lines.subList(from, to).clear();
		lines.addAll(from, with);
		if (belowFromBottom < scrolledLines) {
			scrolledLines = Math.max(0, scrolledLines + with.size() - (to - from));
		}
	}

	private void ensureWrapped() {
		int width = Mth.floor(boxWidth() / scale());
		if (width == wrapWidth) {
			return;
		}
		wrapWidth = width;
		lines.clear();
		for (ChatEntry entry : CakeChat.store().entries()) {
			if (shows(entry)) {
				lines.addAll(wrap(entry));
			}
		}
		scroll(0);
	}

	private List<Line> wrap(ChatEntry entry) {
		int width = wrapWidth;
		if (entry.indicator != null && entry.indicator.icon() != null) {
			width -= entry.indicator.icon().width + 4 + 2;
		}
		List<FormattedCharSequence> wrapped = ComponentRenderUtils.wrapComponents(display(entry), width, client.font);
		List<Line> out = new ArrayList<>(wrapped.size());
		for (int j = 0; j < wrapped.size(); j++) {
			out.add(new Line(entry, wrapped.get(j), j == wrapped.size() - 1));
		}
		return out;
	}

	/** The entry as shown: find matches highlighted, compact counter and timestamp added. */
	private Component display(ChatEntry entry) {
		ChatConfig config = CakeChat.config();
		Component text = entry.content;
		if (windows.findBar().searching()) {
			text = highlight(text, windows.findBar().find().ranges(text.getString()));
		}
		if (entry.count > 1) {
			text = Component.empty().append(text)
					.append(Component.literal(Compactor.counter(config.compact.format, entry.count)).withStyle(ChatFormatting.GRAY));
		}
		if (config.timestamps.enabled) {
			String time = time(entry.millis, config.timestamps.pattern);
			text = switch (config.timestamps.placement) {
				case BEFORE -> Component.empty().append(Component.literal(time + " ").withStyle(ChatFormatting.GRAY)).append(text);
				case AFTER -> Component.empty().append(text).append(Component.literal(" " + time).withStyle(ChatFormatting.GRAY));
				case HOVER -> withHoverWhereMissing(text, new HoverEvent.ShowText(Component.literal(time)));
			};
		}
		return text;
	}

	private String time(long millis, String pattern) {
		if (!pattern.equals(timePattern)) {
			timePattern = pattern;
			timeFormat = ChatConfig.Timestamps.validPattern(pattern) ? DateTimeFormatter.ofPattern(pattern) : DateTimeFormatter.ofPattern("HH:mm");
		}
		try {
			return timeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
		} catch (RuntimeException e) {
			// a field the pattern can't take from a time (hand-edited config)
			return "";
		}
	}

	/** Yellow and underlined where the find bar found something; clicks and hovers stay. */
	private static Component highlight(Component text, List<Find.Range> ranges) {
		if (ranges.isEmpty()) {
			return text;
		}
		MutableComponent out = Component.empty();
		int[] at = {0};
		text.visit((style, string) -> {
			int start = at[0];
			int end = start + string.length();
			int pos = start;
			for (Find.Range range : ranges) {
				int from = Math.max(range.start(), start);
				int to = Math.min(range.end(), end);
				if (from >= to) {
					continue;
				}
				if (from > pos) {
					out.append(Component.literal(string.substring(pos - start, from - start)).setStyle(style));
				}
				out.append(Component.literal(string.substring(from - start, to - start))
						.setStyle(style.withColor(ChatFormatting.YELLOW).withUnderlined(true)));
				pos = to;
			}
			if (pos < end) {
				out.append(Component.literal(string.substring(pos - start)).setStyle(style));
			}
			at[0] = end;
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Puts {@code hover} on every run that has no hover of its own (item links, CakeText translations keep theirs). */
	private static Component withHoverWhereMissing(Component text, HoverEvent hover) {
		MutableComponent out = Component.empty();
		text.visit((style, string) -> {
			out.append(Component.literal(string).setStyle(style.getHoverEvent() == null ? style.withHoverEvent(hover) : style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ---- drawing (vanilla's ChatHud.render, on this window's lines and box) ----

	public void render(GuiGraphicsExtractor context, int currentTick, int mouseX, int mouseY, boolean highlighted) {
		if (isChatHidden()) {
			return;
		}
		boolean open = isChatFocused();
		// peeking draws the chat as if it were open (no fade, full height), without the tab bar
		boolean focused = open || windows.peeking();
		if (!focused && windows.hidden()) {
			return;
		}
		ensureWrapped();
		// 26.1 has no depth: what's drawn later is on top, so the lines first, then the bars and the grip
		renderLines(context, currentTick, mouseX, mouseY, open, focused);
		if (open) {
			renderTabBar(context, mouseX, mouseY);
			if (CakeChat.config().windowEditing) {
				renderGrip(context, mouseX, mouseY, highlighted);
			}
		}
		if (index == 0) {
			renderQueueBar(context);
		}
	}

	private void renderLines(GuiGraphicsExtractor context, int currentTick, int mouseX, int mouseY, boolean open, boolean focused) {
		int visibleLineCount = visibleLineCount(focused);
		int lineCount = lines.size();
		if (lineCount <= 0) {
			return;
		}
		float scale = (float) scale();
		int width = Mth.ceil(boxWidth() / scale);
		context.pose().pushMatrix();
		context.pose().translate((float) boxX(), 0.0F);
		context.pose().scale(scale, scale);
		context.pose().translate(4.0F, 0.0F);
		// in the open chat the text shows its hover tooltips (and the hand cursor), like vanilla's chat
		ActiveTextCollector text = context.textRenderer(open ? GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR
				: GuiGraphicsExtractor.HoveredTextEffects.NONE);
		int bottom = Mth.floor(boxBottom() / scale);
		int hoveredEntry = messageIndex(toChatLineX(mouseX), toChatLineY(mouseY));
		double textOpacity = textOpacity();
		double backgroundOpacity = client.options.textBackgroundOpacity().get();
		double spacing = client.options.chatLineSpacing().get();
		int lineHeight = lineHeight();
		int textOffset = (int) Math.round(-8.0 * (spacing + 1.0) + 4.0 * spacing);
		int drawn = 0;

		for (int r = 0; r + scrolledLines < lineCount && r < visibleLineCount; r++) {
			int s = r + scrolledLines;
			Line line = newest(s);
			int age = currentTick - line.entry.tick;
			if (age < 200 || focused) {
				double fade = focused ? 1.0 : opacityMultiplier(age);
				int textAlpha = (int) (255.0 * fade * textOpacity);
				int backgroundAlpha = (int) (255.0 * fade * backgroundOpacity);
				drawn++;
				if (textAlpha > 3) {
					int y = bottom - r * lineHeight;
					int textY = y + textOffset;
					context.fill(-4, y - lineHeight, width + 4 + 4, y, backgroundAlpha << 24);
					GuiMessageTag indicator = line.entry.indicator;
					if (indicator != null) {
						context.fill(-4, y - lineHeight, -2, y, (indicator.indicatorColor() & 0xFFFFFF) | textAlpha << 24);
						if (s == hoveredEntry && indicator.icon() != null) {
							GuiMessageTag.Icon icon = indicator.icon();
							icon.extractRenderState(context, indicatorX(line), textY + 9 - icon.height - 1);
						}
					}
					text.accept(TextAlignment.LEFT, 0, textY, text.defaultParameters().withOpacity(textAlpha / 255.0F), line.text);
				}
			}
		}

		if (focused && CakeChat.config().scroll.scrollbar) {
			int total = lineCount * lineHeight;
			int shown = drawn * lineHeight;
			int top = scrolledLines * shown / lineCount - bottom;
			int barHeight = shown * shown / total;
			if (total != shown) {
				int alpha = top > 0 ? 170 : 96;
				int color = hasUnreadNewMessages ? 0xCC3333 : 0x3333AA;
				int x = width + 4;
				context.fill(x, -top, x + 2, -top - barHeight, color + (alpha << 24));
				context.fill(x + 2, -top, x + 1, -top - barHeight, 0xCCCCCC + (alpha << 24));
			}
		}
		context.pose().popMatrix();
	}

	/** The open chat looks for clicked text (26.1): this window's visible lines, where they're drawn. */
	void captureClickableText(ActiveTextCollector out) {
		if (!isChatFocused() || isChatHidden() || client.options.hideGui) {
			return;
		}
		ensureWrapped();
		float scale = (float) scale();
		Matrix3x2f pose = new Matrix3x2f(out.defaultParameters().pose()).translate((float) boxX(), 0.0F).scale(scale, scale).translate(4.0F, 0.0F);
		ActiveTextCollector.Parameters parameters = out.defaultParameters().withPose(pose);
		int bottom = Mth.floor(boxBottom() / scale);
		double spacing = client.options.chatLineSpacing().get();
		int lineHeight = lineHeight();
		int textOffset = (int) Math.round(-8.0 * (spacing + 1.0) + 4.0 * spacing);
		int shown = Math.min(visibleLineCount(true), lines.size() - scrolledLines);
		for (int r = 0; r < shown; r++) {
			int y = bottom - r * lineHeight;
			out.accept(TextAlignment.LEFT, 0, y + textOffset, parameters, newest(r + scrolledLines).text);
		}
	}

	private void renderTabBar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		int top = boxBottom() + 1;
		int left = boxX();
		int background = (int) (255.0 * client.options.textBackgroundOpacity().get());
		List<ChatConfig.Tab> tabs = window().tabs;
		int hovered = tabAt(mouseX, mouseY);
		int selectedTab = selected();
		for (int t = 0; t < tabs.size(); t++) {
			if (!tabShown(t)) {
				continue;
			}
			int w = tabWidth(t);
			boolean selected = t == selectedTab;
			int alpha = Math.min(255, background + (selected ? 96 : t == hovered ? 48 : 0));
			context.fill(left, top, left + w, top + TAB_BAR_HEIGHT, alpha << 24);
			if (selected) {
				context.fill(left, top + TAB_BAR_HEIGHT - 1, left + w, top + TAB_BAR_HEIGHT, 0xFFFFFFFF);
			}
			int textX = left + 4;
			String name = CakeChat.tabName(tabs.get(t));
			context.text(client.font, name, textX, top + 2, selected ? 0xFFFFFFFF : 0xFFAAAAAA);
			int unread = windows.unread(index, t);
			if (unread > 0) {
				context.text(client.font, unreadLabel(unread), textX + client.font.width(name) + 3, top + 2, 0xFFFFFF55);
			}
			left += w + 1;
		}
	}

	private void renderGrip(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean highlighted) {
		int right = boxX() + boxWidth();
		int top = boxTop();
		boolean hot = highlighted || inGrip(mouseX, mouseY);
		int color = hot ? 0xFFFFFFFF : 0x80FFFFFF;
		context.fill(right - GRIP, top, right, top + 1, color);
		context.fill(right - 1, top, right, top + GRIP, color);
		if (highlighted) {
			// the whole box while it's being moved or resized
			int left = boxX();
			int bottom = boxBottom();
			context.fill(left, top, right, top + 1, 0x60FFFFFF);
			context.fill(left, bottom - 1, right, bottom, 0x60FFFFFF);
			context.fill(left, top, left + 1, bottom, 0x60FFFFFF);
			context.fill(right - 1, top, right, bottom, 0x60FFFFFF);
		}
	}

	private void renderQueueBar(GuiGraphicsExtractor context) {
		long queued = client.getChatListener().queueSize();
		if (queued <= 0L) {
			return;
		}
		int top = boxBottom() + 1 + TAB_BAR_HEIGHT + 1;
		int alpha = (int) (128.0 * textOpacity());
		int background = (int) (255.0 * client.options.textBackgroundOpacity().get());
		context.fill(boxX(), top, boxX() + boxWidth(), top + 9, background << 24);
		context.text(client.font, Component.translatable("chat.queue", queued), boxX() + 2, top + 1, 0xFFFFFF + (alpha << 24));
	}

	private int tabWidth(int tab) {
		ChatConfig.Tab t = window().tabs.get(tab);
		int w = client.font.width(CakeChat.tabName(t)) + 8;
		int unread = windows.unread(index, tab);
		if (unread > 0) {
			w += client.font.width(unreadLabel(unread)) + 3;
		}
		return w;
	}

	private int tabsWidth() {
		int w = 0;
		for (int t = 0; t < window().tabs.size(); t++) {
			if (tabShown(t)) {
				w += tabWidth(t) + 1;
			}
		}
		return w;
	}

	private static String unreadLabel(int unread) {
		return unread > 99 ? "99+" : Integer.toString(unread);
	}

	private double textOpacity() {
		return client.options.chatOpacity().get() * 0.9F + 0.1F;
	}

	private static double opacityMultiplier(int age) {
		double d = Mth.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
		return d * d;
	}

	// ---- input (vanilla's ChatHud methods, on this window) ----

	public void scroll(int amount) {
		scrolledLines += amount;
		int max = lines.size() - visibleLineCount();
		if (scrolledLines > max) {
			scrolledLines = max;
		}
		if (scrolledLines <= 0) {
			scrolledLines = 0;
			hasUnreadNewMessages = false;
		}
	}

	public void resetScroll() {
		scrolledLines = 0;
		hasUnreadNewMessages = false;
	}

	/** The message under the point in the open chat (for the copy menu), or null. */
	@Nullable
	ChatEntry entryAt(double x, double y) {
		int i = lineIndex(toChatLineX(x), toChatLineY(y));
		return i >= 0 ? newest(i).entry : null;
	}

	@Nullable
	public GuiMessageTag indicatorAt(double x, double y) {
		double lineX = toChatLineX(x);
		int i = messageIndex(lineX, toChatLineY(y));
		if (i < 0) {
			return null;
		}
		Line line = newest(i);
		GuiMessageTag indicator = line.entry.indicator;
		if (indicator == null) {
			return null;
		}
		if (lineX < 0.0) {
			return indicator;
		}
		GuiMessageTag.Icon icon = indicator.icon();
		int iconX = indicatorX(line);
		return icon != null && lineX >= iconX && lineX <= iconX + icon.width ? indicator : null;
	}

	private int indicatorX(Line line) {
		return client.font.width(line.text) + 4;
	}

	private Line newest(int i) {
		return lines.get(lines.size() - 1 - i);
	}

	private double toChatLineX(double x) {
		return (x - boxX()) / scale() - 4.0;
	}

	private double toChatLineY(double y) {
		return (boxBottom() - y) / (scale() * lineHeight());
	}

	/** The bottom line of the entry under the point (where vanilla draws the indicator icon), or -1. */
	private int messageIndex(double lineX, double lineY) {
		int i = lineIndex(lineX, lineY);
		if (i < 0) {
			return -1;
		}
		while (i >= 0 && !newest(i).endOfEntry) {
			i--;
		}
		return i;
	}

	private int lineIndex(double lineX, double lineY) {
		if (!isChatFocused() || client.options.hideGui || isChatHidden()) {
			return -1;
		}
		if (lineX < -4.0 || lineX > Mth.floor(boxWidth() / scale())) {
			return -1;
		}
		int shown = Math.min(visibleLineCount(true), lines.size());
		if (lineY >= 0.0 && lineY < shown) {
			int j = Mth.floor(lineY + scrolledLines);
			if (j >= 0 && j < lines.size()) {
				return j;
			}
		}
		return -1;
	}

	private double scale() {
		return client.options.chatScale().get();
	}

	private int lineHeight() {
		return (int) (9.0 * (client.options.chatLineSpacing().get() + 1.0));
	}

	private boolean isChatHidden() {
		return client.options.chatVisibility().get() == ChatVisiblity.HIDDEN;
	}

	private boolean isChatFocused() {
		return client.screen instanceof ChatScreen;
	}

	private ChatComponent hud() {
		return client.gui.getChat();
	}
}
