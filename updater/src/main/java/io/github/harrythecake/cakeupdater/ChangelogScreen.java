package io.github.harrythecake.cakeupdater;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/** Every change in the versions an update brought (/cakeupdate changelog, the [Changelog] button), newest first. */
final class ChangelogScreen extends Screen {
	private static final int TOP = 32;
	private static final int LINE = 11;
	private static final int INDENT = 10;

	/** One line of text; the first line of an item also gets a bullet in front of it. */
	private record Row(OrderedText text, int x, boolean bullet) {}

	private final @Nullable Screen parent;
	private final List<Changelog.Entry> entries;
	private final List<Row> rows = new ArrayList<>();
	private double scroll;

	ChangelogScreen(@Nullable Screen parent, List<Changelog.Entry> entries) {
		super(Text.translatable("cakeupdater.changelog.title"));
		this.parent = parent;
		this.entries = entries;
	}

	@Override
	protected void init() {
		rows.clear();
		int textWidth = Math.min(width - 40, 400);
		int left = (width - textWidth) / 2;
		for (Changelog.Entry entry : entries) {
			if (!rows.isEmpty()) {
				rows.add(new Row(OrderedText.EMPTY, left, false));
			}
			add(Text.literal(entry.version()).formatted(Formatting.GOLD, Formatting.BOLD), left, textWidth, false);
			for (Changelog.Line line : entry.lines()) {
				switch (line.kind()) {
					case HEADING -> add(Text.literal(line.text()).formatted(Formatting.YELLOW), left, textWidth, false);
					case ITEM -> add(Text.literal(line.text()), left + INDENT, textWidth - INDENT, true);
					case TEXT -> add(Text.literal(line.text()).formatted(Formatting.GRAY), left, textWidth, false);
				}
			}
		}
		scroll = Math.min(scroll, maxScroll());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void add(Text text, int x, int textWidth, boolean bullet) {
		List<OrderedText> lines = textRenderer.wrapLines(text, textWidth);
		for (int i = 0; i < lines.size(); i++) {
			rows.add(new Row(lines.get(i), x, bullet && i == 0));
		}
	}

	private int bottom() {
		return height - 36;
	}

	private double maxScroll() {
		return Math.max(0, rows.size() * LINE - (bottom() - TOP));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - verticalAmount * LINE * 3));
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
		if (entries.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cakeupdater.changelog.none"), width / 2, TOP + 6, 0xAAAAAA);
			return;
		}
		context.enableScissor(0, TOP, width, bottom());
		int y = TOP - (int) scroll;
		for (Row row : rows) {
			if (row.bullet()) {
				context.drawTextWithShadow(textRenderer, Text.literal("•"), row.x() - INDENT, y, 0xFFFFFF);
			}
			context.drawTextWithShadow(textRenderer, row.text(), row.x(), y, 0xFFFFFF);
			y += LINE;
		}
		context.disableScissor();
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
