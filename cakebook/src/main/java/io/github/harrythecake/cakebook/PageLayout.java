package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * A page laid out for one width: text lines, background boxes and images at positions relative to the page's top-left
 * corner. Rebuilt when the screen size or the page changes. Links carry their raw target in the style's insertion
 * (prefixed with {@link #LINK}), which {@link BookScreen} resolves on click.
 */
final class PageLayout {
	static final String LINK = "cakebook:link:";

	static final int LINE = 10;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int H1 = 0xFFFFAA00;
	private static final int H2 = 0xFFFFD866;
	private static final int H3 = 0xFFFFFFFF;
	private static final int QUOTE_TEXT = 0xFFB8B8B8;
	private static final int QUOTE_BAR = 0xFF6A8CAF;
	private static final int CODE_TEXT = 0xE8B864;
	private static final int CODE_BOX = 0x50000000;
	private static final int RULE = 0x40FFFFFF;
	private static final int LINK_COLOR = 0x6FB4FF;
	private static final int KEY_COLOR = 0xFFC94D;

	record Line(OrderedText text, int x, int y, int color) {}

	record Box(int x, int y, int width, int height, int color) {}

	record Picture(Images.Image image, int x, int y, int width, int height) {}

	final List<Line> lines = new ArrayList<>();
	final List<Box> boxes = new ArrayList<>();
	final List<Picture> pictures = new ArrayList<>();
	int height;

	private final TextRenderer textRenderer;
	private final int width;
	private int y;

	private PageLayout(TextRenderer textRenderer, int width) {
		this.textRenderer = textRenderer;
		this.width = width;
	}

	/**
	 * @param title  drawn as a level-1 heading above the page (notes keep their title outside the text), or null
	 * @param images turns an image's {@code src} into a locator for {@link Images}, or null when it can't be shown
	 */
	static PageLayout of(Markdown.Doc doc, @Nullable String title, Function<String, String> images,
			TextRenderer textRenderer, int width) {
		PageLayout layout = new PageLayout(textRenderer, Math.max(width, 40));
		if (title != null) {
			layout.heading(1, Text.literal(title));
		}
		List<Markdown.Block> blocks = doc.blocks();
		for (int i = 0; i < blocks.size(); i++) {
			Markdown.Block block = blocks.get(i);
			Markdown.Block next = i + 1 < blocks.size() ? blocks.get(i + 1) : null;
			layout.block(block, next, images);
		}
		layout.height = Math.max(0, layout.y - 4);
		return layout;
	}

	private void block(Markdown.Block block, @Nullable Markdown.Block next, Function<String, String> images) {
		switch (block.kind()) {
			case HEADING -> heading(block.level(), text(block.spans()));
			case PARAGRAPH -> {
				wrap(text(block.spans()), 0, width, TEXT);
				y += 5;
			}
			case ITEM -> {
				int indent = block.level() * 10;
				String marker = block.marker() == null ? "•" : block.marker();
				int markerWidth = Math.max(textRenderer.getWidth(marker), 6);
				lines.add(new Line(Text.literal(marker).asOrderedText(), indent, y, TEXT));
				wrap(text(block.spans()), indent + markerWidth + 4, width - indent - markerWidth - 4, TEXT);
				y += next != null && next.kind() == Markdown.Kind.ITEM ? 1 : 5;
			}
			case QUOTE -> {
				int top = y;
				wrap(text(block.spans()), 8, width - 8, QUOTE_TEXT);
				boxes.add(new Box(0, top - 1, 2, y - top, QUOTE_BAR));
				y += 5;
			}
			case CODE -> {
				int top = y;
				y += 4;
				for (String codeLine : block.spans().get(0).text().split("\n", -1)) {
					wrap(Text.literal(codeLine.isEmpty() ? " " : codeLine).styled(s -> s.withColor(CODE_TEXT)), 5, width - 10, TEXT);
				}
				y += 3;
				boxes.add(new Box(0, top, width, y - top, CODE_BOX));
				y += 5;
			}
			case RULE -> {
				boxes.add(new Box(0, y + 2, width, 1, RULE));
				y += 9;
			}
			case IMAGE -> {
				String alt = block.plainText();
				String locator = block.src() == null ? null : images.apply(block.src());
				Images.Image image = locator == null ? null : Images.get(locator);
				if (image == null) {
					wrap(Text.translatable("cakebook.image.missing", alt.isEmpty() ? block.src() : alt)
							.styled(s -> s.withItalic(true)), 0, width, QUOTE_TEXT);
				} else {
					int w = Math.min(block.width() > 0 ? block.width() : image.width(), width);
					int h = Math.max(1, Math.round((float) w * image.height() / image.width()));
					pictures.add(new Picture(image, (width - w) / 2, y, w, h));
					y += h + 2;
				}
				y += 5;
			}
		}
	}

	private void heading(int level, MutableText text) {
		if (y > 0) {
			y += level <= 2 ? 6 : 3;
		}
		int color = level == 1 ? H1 : level == 2 ? H2 : H3;
		wrap(text.styled(s -> s.withBold(level <= 3)), 0, width, color);
		if (level == 1) {
			boxes.add(new Box(0, y, width, 1, 0x60FFAA00));
			y += 4;
		} else {
			y += 3;
		}
	}

	private void wrap(Text text, int x, int lineWidth, int color) {
		for (OrderedText line : textRenderer.wrapLines(text, Math.max(lineWidth, 20))) {
			lines.add(new Line(line, x, y, color));
			y += LINE;
		}
	}

	/** One block's spans as game text: styles, links (underlined, target in the insertion) and key names. */
	static MutableText text(List<Markdown.Span> spans) {
		MutableText text = Text.empty();
		for (Markdown.Span span : spans) {
			MutableText part = span.key() != null ? Text.keybind(span.key()) : Text.literal(span.text());
			Style style = Style.EMPTY;
			int s = span.style();
			if ((s & Markdown.BOLD) != 0) {
				style = style.withBold(true);
			}
			if ((s & Markdown.ITALIC) != 0) {
				style = style.withItalic(true);
			}
			if ((s & Markdown.STRIKE) != 0) {
				style = style.withStrikethrough(true);
			}
			if ((s & Markdown.CODE) != 0) {
				style = style.withColor(CODE_TEXT);
			}
			if (span.key() != null) {
				style = style.withColor(KEY_COLOR)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("cakebook.key.hover")));
			}
			if (span.link() != null) {
				style = style.withColor(LINK_COLOR).withUnderline(true).withInsertion(LINK + span.link());
				String lower = span.link().toLowerCase(java.util.Locale.ROOT);
				if (lower.startsWith("http://") || lower.startsWith("https://")) {
					style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, span.link()))
							.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(span.link())));
				}
			}
			text.append(part.setStyle(style));
		}
		return text;
	}

	void render(DrawContext context, int left, int top) {
		for (Box box : boxes) {
			context.fill(left + box.x(), top + box.y(), left + box.x() + box.width(), top + box.y() + box.height(), box.color());
		}
		for (Picture picture : pictures) {
			context.drawTexture(picture.image().texture(), left + picture.x(), top + picture.y(), picture.width(),
					picture.height(), 0, 0, picture.image().width(), picture.image().height(), picture.image().width(),
					picture.image().height());
		}
		for (Line line : lines) {
			context.drawText(textRenderer, line.text(), left + line.x(), top + line.y(), line.color(), true);
		}
	}

	/** The style under a point given relative to the page's top-left corner, or null over nothing. */
	@Nullable Style styleAt(double x, double y) {
		for (Line line : lines) {
			if (y >= line.y() && y < line.y() + LINE && x >= line.x()) {
				Style style = textRenderer.getTextHandler().getStyleAt(line.text(), (int) x - line.x());
				if (style != null) {
					return style;
				}
			}
		}
		return null;
	}
}
