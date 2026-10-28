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
 * The chat box CakeChat draws instead of vanilla's. Size, scale, opacity and line spacing still come from
 * Minecraft's own Chat Settings, and the layout and fade match vanilla, so mouse handling that vanilla's
 * {@code ChatScreen} does from those settings (the delayed-message bar) lines up.
 */
public final class ChatView {
	private record Line(ChatEntry entry, OrderedText text, boolean endOfEntry) {
	}

	private final MinecraftClient client;
	/** Wrapped lines, oldest first; {@link #newest} indexes from the bottom like vanilla's visibleMessages. */
	private final List<Line> lines = new ArrayList<>();
	private ChatStore store;
	private int scrolledLines;
	private boolean hasUnreadNewMessages;
	/** Width the lines were wrapped for; -1 = wrap everything again before the next draw. */
	private int wrapWidth = -1;
	private String timePattern;
	private DateTimeFormatter timeFormat;

	ChatView(MinecraftClient client) {
		this.client = client;
	}

	void attach(ChatStore store) {
		this.store = store;
	}

	/** Settings changed: wrap everything again (timestamps, counters) before the next draw. */
	public void invalidate() {
		wrapWidth = -1;
	}

	// ---- store events ----

	void added(ChatEntry entry) {
		if (wrapWidth < 0) {
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
			replace(entry, wrap(entry));
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
		int width = MathHelper.floor(hud().getWidth() / hud().getChatScale());
		if (width == wrapWidth) {
			return;
		}
		wrapWidth = width;
		lines.clear();
		for (ChatEntry entry : store.entries()) {
			lines.addAll(wrap(entry));
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

	// ---- drawing (vanilla's ChatHud.render, on CakeChat's lines) ----

	public void render(DrawContext context, int currentTick, int mouseX, int mouseY) {
		if (isChatHidden()) {
			return;
		}
		ensureWrapped();
		ChatHud hud = hud();
		int visibleLineCount = hud.getVisibleLineCount();
		int lineCount = lines.size();
		if (lineCount <= 0) {
			return;
		}
		boolean focused = isChatFocused();
		float scale = (float) hud.getChatScale();
		int width = MathHelper.ceil(hud.getWidth() / scale);
		context.getMatrices().push();
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(4.0F, 0.0F, 0.0F);
		int bottom = MathHelper.floor((context.getScaledWindowHeight() - 40) / scale);
		int hoveredEntry = messageIndex(toChatLineX(mouseX), toChatLineY(mouseY));
		double textOpacity = client.options.getChatOpacity().getValue() * 0.9F + 0.1F;
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

		long queued = client.getMessageHandler().getUnprocessedMessageCount();
		if (queued > 0L) {
			int alpha = (int) (128.0 * textOpacity);
			int backgroundAlpha = (int) (255.0 * backgroundOpacity);
			context.getMatrices().push();
			context.getMatrices().translate(0.0F, (float) bottom, 50.0F);
			context.fill(-2, 0, width + 4, 9, backgroundAlpha << 24);
			context.getMatrices().translate(0.0F, 0.0F, 50.0F);
			context.drawTextWithShadow(client.textRenderer, Text.translatable("chat.queue", queued), 0, 1, 0xFFFFFF + (alpha << 24));
			context.getMatrices().pop();
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

	private static double opacityMultiplier(int age) {
		double d = MathHelper.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
		return d * d;
	}

	// ---- input (vanilla's ChatHud methods, on CakeChat's lines) ----

	public void scroll(int amount) {
		scrolledLines += amount;
		int max = lines.size() - hud().getVisibleLineCount();
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
		return x / hud().getChatScale() - 4.0;
	}

	private double toChatLineY(double y) {
		double fromBottom = client.getWindow().getScaledHeight() - y - 40.0;
		return fromBottom / (hud().getChatScale() * lineHeight());
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
		if (lineX < -4.0 || lineX > MathHelper.floor(hud().getWidth() / hud().getChatScale())) {
			return -1;
		}
		int shown = Math.min(hud().getVisibleLineCount(), lines.size());
		if (lineY >= 0.0 && lineY < shown) {
			int j = MathHelper.floor(lineY + scrolledLines);
			if (j >= 0 && j < lines.size()) {
				return j;
			}
		}
		return -1;
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
