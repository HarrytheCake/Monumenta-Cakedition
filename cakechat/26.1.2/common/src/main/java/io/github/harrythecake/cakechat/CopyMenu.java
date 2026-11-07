package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Right-click a message in the open chat: a small menu to copy it as plain text, with § colour codes or as JSON.
 * The copy is the message itself, without CakeChat's timestamp, counter or find highlights.
 */
public final class CopyMenu {
	private static final int ITEM_HEIGHT = 12;
	private static final long COPIED_MILLIS = 1200;
	private static final ChatCopy.Format[] ITEMS = ChatCopy.Format.values();

	private final Minecraft client;
	@Nullable
	private ChatEntry entry;
	private int x;
	private int y;
	/** "Copied" shows where the menu was until then. */
	private long copiedUntil;

	CopyMenu(Minecraft client) {
		this.client = client;
	}

	public boolean isOpen() {
		return entry != null;
	}

	void open(ChatEntry entry, double x, double y) {
		this.entry = entry;
		int width = width();
		int height = ITEMS.length * ITEM_HEIGHT;
		// on screen, and above the input line
		this.x = (int) Math.min(x, client.getWindow().getGuiScaledWidth() - width - 2);
		this.y = (int) Math.min(y, client.getWindow().getGuiScaledHeight() - 14 - height - 2);
	}

	public void close() {
		entry = null;
	}

	/** The message left the history (history limit, compacting): nothing to copy any more. */
	void removed(ChatEntry removed) {
		if (entry == removed) {
			entry = null;
		}
	}

	/** True when the click was on an item (and copied). */
	boolean click(double mouseX, double mouseY) {
		int item = itemAt(mouseX, mouseY);
		if (entry == null || item < 0) {
			return false;
		}
		client.keyboardHandler.setClipboard(ChatCopy.copy(entry.content, ITEMS[item]));
		copiedUntil = Util.getMillis() + COPIED_MILLIS;
		return true;
	}

	private int itemAt(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + width() || mouseY < y) {
			return -1;
		}
		int item = (int) ((mouseY - y) / ITEM_HEIGHT);
		return item < ITEMS.length ? item : -1;
	}

	private int width() {
		int w = 0;
		for (ChatCopy.Format format : ITEMS) {
			w = Math.max(w, client.font.width(label(format)));
		}
		return w + 8;
	}

	private static Component label(ChatCopy.Format format) {
		return Component.translatable("cakechat.copy." + format.name().toLowerCase(Locale.ROOT));
	}

	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		if (entry == null && Util.getMillis() >= copiedUntil) {
			return;
		}
		// above the chat, the input line and the find bar
		context.nextStratum();
		if (entry != null) {
			int width = width();
			int hovered = itemAt(mouseX, mouseY);
			context.fill(x - 1, y - 1, x + width + 1, y + ITEMS.length * ITEM_HEIGHT + 1, 0xFFAAAAAA);
			for (int i = 0; i < ITEMS.length; i++) {
				int top = y + i * ITEM_HEIGHT;
				context.fill(x, top, x + width, top + ITEM_HEIGHT, i == hovered ? 0xFF505050 : 0xFF202020);
				context.text(client.font, label(ITEMS[i]), x + 4, top + 2, 0xFFFFFFFF);
			}
		} else if (Util.getMillis() < copiedUntil) {
			Component copied = Component.translatable("cakechat.copy.done");
			int width = client.font.width(copied) + 8;
			context.fill(x, y, x + width, y + ITEM_HEIGHT, 0xFF205020);
			context.text(client.font, copied, x + 4, y + 2, 0xFFFFFFFF);
		}
	}
}
