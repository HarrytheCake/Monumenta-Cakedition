package io.github.harrythecake.caketext.translator;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Where a translation came from: a book icon for string (dictionary) translations, a chip for the local model, and
 * the hover card shown over translated chat text. Glyphs live in our own font ({@code caketext_translator:icons}), so
 * they look the same with any resource pack.
 */
public final class Markers {
	private static final Identifier FONT = new Identifier("caketext_translator", "icons");
	private static final String BOOK = "";
	private static final String CHIP = "";

	private Markers() {
	}

	public static MutableText icon(boolean machine) {
		return Text.literal(machine ? CHIP : BOOK).setStyle(Style.EMPTY.withFont(FONT).withColor(Formatting.WHITE));
	}

	/** "String translation" or "Machine translation: opus-mt-en-zh int8". */
	public static MutableText label(Dictionary.Hit hit) {
		return hit.machine() ? Text.translatable("caketext_translator.marker.machine", hit.engine())
				: Text.translatable("caketext_translator.marker.dictionary");
	}

	/**
	 * Hover card: the source, then the other version of the line. {@code other} is the original when the
	 * translation is shown, or the translation when the original is shown.
	 */
	public static MutableText card(Dictionary.Hit hit, Text other, boolean otherIsOriginal) {
		MutableText card = label(hit).formatted(hit.machine() ? Formatting.GOLD : Formatting.GREEN);
		if (hit.machine()) {
			card.append("\n").append(Text.translatable("caketext_translator.marker.machine_note").formatted(Formatting.GRAY));
		} else {
			card.append("\n").append(Text.translatable("caketext_translator.marker.file", hit.file()).formatted(Formatting.DARK_GRAY));
		}
		card.append("\n\n").append(Text.translatable(otherIsOriginal ? "caketext_translator.marker.original"
				: "caketext_translator.marker.translation").formatted(Formatting.GRAY))
				.append("\n").append(other);
		return card;
	}

	/** The icon, with the same card when hovered. */
	public static MutableText hoverIcon(Dictionary.Hit hit, Text card) {
		return icon(hit.machine()).styled(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, card)));
	}
}
