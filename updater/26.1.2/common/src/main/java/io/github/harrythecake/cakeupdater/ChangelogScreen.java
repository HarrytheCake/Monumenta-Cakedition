package io.github.harrythecake.cakeupdater;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Every change in the versions an update brought (/cakeupdate changelog, the [Changelog] button), newest first. */
final class ChangelogScreen extends Screen {
	private static final int TOP = 32;
	private static final int LINE = 11;
	private static final int INDENT = 10;

	/** One line of text; the first line of an item also gets a bullet in front of it. */
	private record Row(FormattedCharSequence text, int x, boolean bullet) {}

	private final @Nullable Screen parent;
	private final List<Changelog.Entry> entries;
	private final List<Row> rows = new ArrayList<>();
	private double scroll;

	ChangelogScreen(@Nullable Screen parent, List<Changelog.Entry> entries) {
		super(Component.translatable("cakeupdater.changelog.title"));
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
				rows.add(new Row(FormattedCharSequence.EMPTY, left, false));
			}
			add(Component.literal(entry.version()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left, textWidth, false);
			for (Changelog.Line line : entry.lines()) {
				switch (line.kind()) {
					case HEADING -> add(Component.literal(line.text()).withStyle(ChatFormatting.YELLOW), left, textWidth, false);
					case ITEM -> add(Component.literal(line.text()), left + INDENT, textWidth - INDENT, true);
					case TEXT -> add(Component.literal(line.text()).withStyle(ChatFormatting.GRAY), left, textWidth, false);
				}
			}
		}
		scroll = Math.min(scroll, maxScroll());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	private void add(Component text, int x, int textWidth, boolean bullet) {
		List<FormattedCharSequence> lines = font.split(text, textWidth);
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
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		if (entries.isEmpty()) {
			context.centeredText(font, Component.translatable("cakeupdater.changelog.none"), width / 2, TOP + 6, 0xFFAAAAAA);
			return;
		}
		context.enableScissor(0, TOP, width, bottom());
		int y = TOP - (int) scroll;
		for (Row row : rows) {
			if (row.bullet()) {
				context.text(font, Component.literal("•"), row.x() - INDENT, y, 0xFFFFFFFF);
			}
			context.text(font, row.text(), row.x(), y, 0xFFFFFFFF);
			y += LINE;
		}
		context.disableScissor();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
