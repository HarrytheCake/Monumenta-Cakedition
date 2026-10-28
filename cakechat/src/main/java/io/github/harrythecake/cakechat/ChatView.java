package io.github.harrythecake.cakechat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One chat window: the messages of its selected tab, drawn like vanilla's chat box (layout, fade, indicator bar),
 * at the window's own position and size, with a tab bar under it while the chat is open.
 * Scale, opacity, background and line spacing come from Minecraft's Chat Settings for every window.
 */
public final class ChatView {
	/** Height of the tab bar under the box, in GUI pixels. */
	static final int TAB_BAR_HEIGHT = 12;
	/** Size of the resize grip at the box's top-right corner. */
	static final int GRIP = 6;
	private static final int MIN_WIDTH = 60;
	private static final int MIN_HEIGHT = 20;
	private static final int MIN_BOTTOM = 14 + TAB_BAR_HEIGHT + 2;

	private record Line(ChatEntry entry, OrderedText text, boolean endOfEntry) {
	}

	private final MinecraftClient client;
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

	ChatView(MinecraftClient client, ChatWindows windows, int index) {
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

	private boolean shows(ChatEntry entry) {
		return entry.tabs.get(windows.rules().index(index, selected()));
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
		return client.getWindow().getScaledHeight() - window().bottom;
	}

	int boxWidth() {
		int w = window().width;
		return w >= 0 ? w : hud().getWidth();
	}

	int boxHeight(boolean focused) {
		int h = focused ? window().height : window().unfocusedHeight;
		if (h >= 0) {
			return h;
		}
		double option = focused ? client.options.getChatHeightFocused().getValue() : client.options.getChatHeightUnfocused().getValue();
		return (int) (ChatHud.getHeight(option) * scale());
	}

	int boxTop() {
		return boxBottom() - boxHeight(true);
	}

	private int visibleLineCount(boolean focused) {
		return Math.max(1, (int) (boxHeight(focused) / (lineHeight() * scale())));
	}

	int visibleLineCount() {
		return visibleLineCount(isChatFocused());
	}

	boolean inBox(double x, double y) {
		return x >= boxX() && x < boxX() + boxWidth() && y >= boxTop() && y < boxBottom();
	}

	boolean inGrip(double x, double y) {
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
	boolean inQueueBar(double x, double y) {
		int top = boxBottom() + 1 + TAB_BAR_HEIGHT + 1;
		return index == 0 && x >= boxX() && x < boxX() + boxWidth() && y >= top && y < top + 9;
	}

	void move(int x, int bottom) {
		int screenW = client.getWindow().getScaledWidth();
		int screenH = client.getWindow().getScaledHeight();
		ChatConfig.Window w = window();
		w.x = MathHelper.clamp(x, 0, Math.max(0, screenW - boxWidth()));
		// keep the box on screen and its tab bar above the input line (14 px high at the bottom)
		w.bottom = MathHelper.clamp(bottom, MIN_BOTTOM, Math.max(MIN_BOTTOM, screenH - boxHeight(true)));
	}

	void resize(int width, int height) {
		ChatConfig.Window w = window();
		int screenW = client.getWindow().getScaledWidth();
		// a window that followed Chat Settings keeps its closed height
		int unfocused = boxHeight(false);
		w.width = MathHelper.clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, screenW - w.x));
		w.height = MathHelper.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, boxBottom()));
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
		int width = MathHelper.floor(boxWidth() / scale());
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
		List<OrderedText> wrapped = ChatMessages.breakRenderedChatMessageLines(display(entry), width, client.textRenderer);
		List<Line> out = new ArrayList<>(wrapped.size());
		for (int j = 0; j < wrapped.size(); j++) {
			out.add(new Line(entry, wrapped.get(j), j == wrapped.size() - 1));
		}
		return out;
	}

	/** The entry as shown: compact counter and timestamp added. */
	private Text display(ChatEntry entry) {
		ChatConfig config = CakeChat.config();
		Text text = entry.content;
		if (entry.count > 1) {
			text = Text.empty().append(text)
					.append(Text.literal(Compactor.counter(config.compact.format, entry.count)).formatted(Formatting.GRAY));
		}
		if (config.timestamps.enabled) {
			String time = time(entry.millis, config.timestamps.pattern);
			text = switch (config.timestamps.placement) {
				case BEFORE -> Text.empty().append(Text.literal(time + " ").formatted(Formatting.GRAY)).append(text);
				case AFTER -> Text.empty().append(text).append(Text.literal(" " + time).formatted(Formatting.GRAY));
				case HOVER -> withHoverWhereMissing(text, new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(time)));
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

	/** Puts {@code hover} on every run that has no hover of its own (item links, CakeText translations keep theirs). */
	private static Text withHoverWhereMissing(Text text, HoverEvent hover) {
		MutableText out = Text.empty();
		text.visit((style, string) -> {
			out.append(Text.literal(string).setStyle(style.getHoverEvent() == null ? style.withHoverEvent(hover) : style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ---- drawing (vanilla's ChatHud.render, on this window's lines and box) ----

	public void render(DrawContext context, int currentTick, int mouseX, int mouseY, boolean highlighted) {
		if (isChatHidden()) {
			return;
		}
		ensureWrapped();
		boolean focused = isChatFocused();
		if (focused) {
			renderTabBar(context, mouseX, mouseY);
			renderGrip(context, mouseX, mouseY, highlighted);
		}
		if (index == 0) {
			renderQueueBar(context);
		}
		int visibleLineCount = visibleLineCount(focused);
		int lineCount = lines.size();
		if (lineCount <= 0) {
			return;
		}
		float scale = (float) scale();
		int width = MathHelper.ceil(boxWidth() / scale);
		context.getMatrices().push();
		context.getMatrices().translate((float) boxX(), 0.0F, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(4.0F, 0.0F, 0.0F);
		int bottom = MathHelper.floor(boxBottom() / scale);
		int hoveredEntry = messageIndex(toChatLineX(mouseX), toChatLineY(mouseY));
		double textOpacity = textOpacity();
		double backgroundOpacity = client.options.getTextBackgroundOpacity().getValue();
		double spacing = client.options.getChatLineSpacing().getValue();
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
					context.getMatrices().push();
					context.getMatrices().translate(0.0F, 0.0F, 50.0F);
					context.fill(-4, y - lineHeight, width + 4 + 4, y, backgroundAlpha << 24);
					MessageIndicator indicator = line.entry.indicator;
					if (indicator != null) {
						context.fill(-4, y - lineHeight, -2, y, indicator.indicatorColor() | textAlpha << 24);
						if (s == hoveredEntry && indicator.icon() != null) {
							MessageIndicator.Icon icon = indicator.icon();
							icon.draw(context, indicatorX(line), textY + 9 - icon.height - 1);
						}
					}
					context.getMatrices().translate(0.0F, 0.0F, 50.0F);
					context.drawTextWithShadow(client.textRenderer, line.text, 0, textY, 0xFFFFFF + (textAlpha << 24));
					context.getMatrices().pop();
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
				context.fill(x, -top, x + 2, -top - barHeight, 100, color + (alpha << 24));
				context.fill(x + 2, -top, x + 1, -top - barHeight, 100, 0xCCCCCC + (alpha << 24));
			}
		}
		context.getMatrices().pop();
	}

	private void renderTabBar(DrawContext context, int mouseX, int mouseY) {
		int top = boxBottom() + 1;
		int left = boxX();
		int background = (int) (255.0 * client.options.getTextBackgroundOpacity().getValue());
		List<ChatConfig.Tab> tabs = window().tabs;
		int hovered = tabAt(mouseX, mouseY);
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 100.0F);
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
			context.drawTextWithShadow(client.textRenderer, name, textX, top + 2, selected ? 0xFFFFFF : 0xAAAAAA);
			int unread = windows.unread(index, t);
			if (unread > 0) {
				context.drawTextWithShadow(client.textRenderer, unreadLabel(unread), textX + client.textRenderer.getWidth(name) + 3, top + 2, 0xFFFF55);
			}
			left += w + 1;
		}
		context.getMatrices().pop();
	}

	private void renderGrip(DrawContext context, int mouseX, int mouseY, boolean highlighted) {
		int right = boxX() + boxWidth();
		int top = boxTop();
		boolean hot = highlighted || inGrip(mouseX, mouseY);
		int color = hot ? 0xFFFFFFFF : 0x80FFFFFF;
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 100.0F);
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
		context.getMatrices().pop();
	}

	private void renderQueueBar(DrawContext context) {
		long queued = client.getMessageHandler().getUnprocessedMessageCount();
		if (queued <= 0L) {
			return;
		}
		int top = boxBottom() + 1 + TAB_BAR_HEIGHT + 1;
		int alpha = (int) (128.0 * textOpacity());
		int background = (int) (255.0 * client.options.getTextBackgroundOpacity().getValue());
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 100.0F);
		context.fill(boxX(), top, boxX() + boxWidth(), top + 9, background << 24);
		context.drawTextWithShadow(client.textRenderer, Text.translatable("chat.queue", queued), boxX() + 2, top + 1, 0xFFFFFF + (alpha << 24));
		context.getMatrices().pop();
	}

	private int tabWidth(int tab) {
		ChatConfig.Tab t = window().tabs.get(tab);
		int w = client.textRenderer.getWidth(CakeChat.tabName(t)) + 8;
		int unread = windows.unread(index, tab);
		if (unread > 0) {
			w += client.textRenderer.getWidth(unreadLabel(unread)) + 3;
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
		return client.options.getChatOpacity().getValue() * 0.9F + 0.1F;
	}

	private static double opacityMultiplier(int age) {
		double d = MathHelper.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
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

	@Nullable
	public Style styleAt(double x, double y) {
		double lineX = toChatLineX(x);
		int i = lineIndex(lineX, toChatLineY(y));
		return i >= 0 ? client.textRenderer.getTextHandler().getStyleAt(newest(i).text, MathHelper.floor(lineX)) : null;
	}

	@Nullable
	public MessageIndicator indicatorAt(double x, double y) {
		double lineX = toChatLineX(x);
		int i = messageIndex(lineX, toChatLineY(y));
		if (i < 0) {
			return null;
		}
		Line line = newest(i);
		MessageIndicator indicator = line.entry.indicator;
		if (indicator == null) {
			return null;
		}
		if (lineX < 0.0) {
			return indicator;
		}
		MessageIndicator.Icon icon = indicator.icon();
		int iconX = indicatorX(line);
		return icon != null && lineX >= iconX && lineX <= iconX + icon.width ? indicator : null;
	}

	private int indicatorX(Line line) {
		return client.textRenderer.getWidth(line.text) + 4;
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
		if (!isChatFocused() || client.options.hudHidden || isChatHidden()) {
			return -1;
		}
		if (lineX < -4.0 || lineX > MathHelper.floor(boxWidth() / scale())) {
			return -1;
		}
		int shown = Math.min(visibleLineCount(true), lines.size());
		if (lineY >= 0.0 && lineY < shown) {
			int j = MathHelper.floor(lineY + scrolledLines);
			if (j >= 0 && j < lines.size()) {
				return j;
			}
		}
		return -1;
	}

	private double scale() {
		return hud().getChatScale();
	}

	private int lineHeight() {
		return (int) (9.0 * (client.options.getChatLineSpacing().getValue() + 1.0));
	}

	private boolean isChatHidden() {
		return client.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN;
	}

	private boolean isChatFocused() {
		return client.currentScreen instanceof ChatScreen;
	}

	private ChatHud hud() {
		return client.inGameHud.getChatHud();
	}
}
