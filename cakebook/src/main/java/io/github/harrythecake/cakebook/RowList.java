package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;

/** A scrolling list of clickable rows: the book's sidebar (contents, notes, search results) and the link picker. */
final class RowList {
	private static final int ROW = 12;
	private static final int ICON_ROW = 18;
	private static final int ARROW = 9;

	/**
	 * @param expanded null for no arrow; otherwise the arrow shows ▼/▶ and a click on it runs {@code toggle}
	 * @param tooltip  shown when the mouse rests on the row (a search hit's snippet), or null
	 */
	record Row(Text text, int indent, @Nullable ItemStack icon, @Nullable Runnable action, @Nullable Runnable toggle,
			@Nullable Boolean expanded, boolean selected, @Nullable Text tooltip) {
		static Row page(Text text, int indent, Runnable action, boolean selected) {
			return new Row(text, indent, null, action, null, null, selected, null);
		}

		int height() {
			return icon != null ? ICON_ROW : ROW;
		}
	}

	private final TextRenderer textRenderer;
	private final List<Row> rows = new ArrayList<>();
	int x;
	int y;
	int width;
	int height;
	private double scroll;

	RowList(TextRenderer textRenderer) {
		this.textRenderer = textRenderer;
	}

	void bounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		clampScroll();
	}

	void set(List<Row> newRows) {
		rows.clear();
		rows.addAll(newRows);
		clampScroll();
	}

	void resetScroll() {
		scroll = 0;
	}

	/** Scrolls so the selected row is in view (after opening a page from elsewhere). */
	void revealSelected() {
		int top = 0;
		for (Row row : rows) {
			if (row.selected()) {
				if (top < scroll) {
					scroll = top;
				} else if (top + row.height() > scroll + height) {
					scroll = top + row.height() - height;
				}
				clampScroll();
				return;
			}
			top += row.height();
		}
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	void scroll(double amount) {
		scroll -= amount * ROW * 3;
		clampScroll();
	}

	private int contentHeight() {
		int total = 0;
		for (Row row : rows) {
			total += row.height();
		}
		return total;
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight() - height)));
	}

	void render(DrawContext context, int mouseX, int mouseY) {
		context.fill(x, y, x + width, y + height, 0x60000000);
		context.enableScissor(x, y, x + width, y + height);
		int top = y - (int) scroll;
		Row hovered = null;
		for (Row row : rows) {
			int h = row.height();
			if (top + h > y && top < y + height) {
				boolean over = contains(mouseX, mouseY) && mouseY >= top && mouseY < top + h;
				if (row.selected()) {
					context.fill(x, top, x + width, top + h, 0x50FFFFFF);
				} else if (over && (row.action() != null || row.toggle() != null)) {
					context.fill(x, top, x + width, top + h, 0x28FFFFFF);
				}
				if (over) {
					hovered = row;
				}
				int left = x + 3 + row.indent() * 8;
				if (row.expanded() != null) {
					context.drawText(textRenderer, row.expanded() ? "▼" : "▶", left, top + (h - 8) / 2, 0xFFA0A0A0, false);
					left += ARROW;
				}
				if (row.icon() != null) {
					context.drawItem(row.icon(), left, top + 1);
					left += 18;
				}
				int room = x + width - left - 3;
				context.drawText(textRenderer, trim(row.text(), room), left, top + (h - 8) / 2, 0xFFFFFFFF, false);
			}
			top += h;
		}
		context.disableScissor();
		int content = contentHeight();
		if (content > height) {
			int barHeight = Math.max(16, height * height / content);
			int barTop = y + (int) ((height - barHeight) * scroll / (content - height));
			context.fill(x + width - 2, barTop, x + width, barTop + barHeight, 0x80FFFFFF);
		}
		if (hovered != null && hovered.tooltip() != null) {
			context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(hovered.tooltip(), 220), mouseX, mouseY);
		}
	}

	private OrderedText trim(Text text, int room) {
		if (textRenderer.getWidth(text) <= room) {
			return text.asOrderedText();
		}
		StringVisitable cut = textRenderer.trimToWidth(text, Math.max(0, room - textRenderer.getWidth("…")));
		return Language.getInstance().reorder(StringVisitable.concat(cut, Text.literal("…")));
	}

	/** Runs the clicked row's action (or its arrow's toggle); false if the click hit no row. */
	boolean click(double mouseX, double mouseY) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		int top = y - (int) scroll;
		for (Row row : rows) {
			int h = row.height();
			if (mouseY >= top && mouseY < top + h) {
				int arrowLeft = x + 3 + row.indent() * 8;
				boolean onArrow = row.expanded() != null && mouseX < arrowLeft + ARROW + 1;
				Runnable run = onArrow || row.action() == null ? row.toggle() : row.action();
				if (run != null) {
					run.run();
					return true;
				}
				return false;
			}
			top += h;
		}
		return false;
	}
}
