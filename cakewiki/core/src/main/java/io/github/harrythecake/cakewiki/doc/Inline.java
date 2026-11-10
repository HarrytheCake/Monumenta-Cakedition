package io.github.harrythecake.cakewiki.doc;

/** A run inside a line of text: styled text, a small picture, or a forced line break. */
public sealed interface Inline {
	record Text(String text, Style style) implements Inline {}

	record InlineImage(Image image, Link link) implements Inline {}

	record LineBreak() implements Inline {}

	/** {@code color} is 0xRRGGBB or null for the default colour; {@code link} is null for plain text. */
	record Style(Integer color, boolean bold, boolean italic, boolean underline, boolean strikethrough, Link link) {
		public static final Style PLAIN = new Style(null, false, false, false, false, null);

		public Style withColor(Integer c) { return new Style(c, bold, italic, underline, strikethrough, link); }
		public Style withBold() { return new Style(color, true, italic, underline, strikethrough, link); }
		public Style withItalic() { return new Style(color, bold, true, underline, strikethrough, link); }
		public Style withUnderline() { return new Style(color, bold, italic, true, strikethrough, link); }
		public Style withStrikethrough() { return new Style(color, bold, italic, underline, true, link); }
		public Style withLink(Link l) { return new Style(color, bold, italic, underline, strikethrough, l); }
	}
}
