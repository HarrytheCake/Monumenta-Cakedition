package io.github.harrythecake.caketext.translator;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Where a translation came from: a book icon for string (dictionary) translations, a chip for the local model, and
 * the hover card shown over translated chat text. Glyphs live in our own font ({@code caketext_translator:icons}), so
 * they look the same with any resource pack.
 */
public final class Markers {
	private static final ResourceLocation FONT = new ResourceLocation("caketext_translator", "icons");
	private static final String BOOK = "";
	private static final String CHIP = "";

	private Markers() {
	}

	public static MutableComponent icon(boolean machine) {
		return Component.literal(machine ? CHIP : BOOK).setStyle(Style.EMPTY.withFont(FONT).withColor(ChatFormatting.WHITE));
	}

	/** "String translation" or "Machine translation: opus-mt-en-zh int8". */
	public static MutableComponent label(Dictionary.Hit hit) {
		return hit.machine() ? Component.translatable("caketext_translator.marker.machine", hit.engine())
				: Component.translatable("caketext_translator.marker.dictionary");
	}

	/**
	 * Hover card: the source, then the other version of the line. {@code other} is the original when the
	 * translation is shown, or the translation when the original is shown.
	 */
	public static MutableComponent card(Dictionary.Hit hit, Component other, boolean otherIsOriginal) {
		MutableComponent card = label(hit).withStyle(hit.machine() ? ChatFormatting.GOLD : ChatFormatting.GREEN);
		if (hit.machine()) {
			card.append("\n").append(Component.translatable("caketext_translator.marker.machine_note").withStyle(ChatFormatting.GRAY));
		} else {
			card.append("\n").append(Component.translatable("caketext_translator.marker.file", hit.file()).withStyle(ChatFormatting.DARK_GRAY));
		}
		card.append("\n\n").append(Component.translatable(otherIsOriginal ? "caketext_translator.marker.original"
				: "caketext_translator.marker.translation").withStyle(ChatFormatting.GRAY))
				.append("\n").append(other);
		return card;
	}

	/** The icon, with the same card when hovered. */
	public static MutableComponent hoverIcon(Dictionary.Hit hit, Component card) {
		return icon(hit.machine()).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, card)));
	}
}
