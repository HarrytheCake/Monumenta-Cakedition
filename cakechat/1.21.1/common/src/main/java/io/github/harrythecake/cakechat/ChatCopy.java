package io.github.harrythecake.cakechat;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** A message as text for the clipboard: plain, with § colour codes, or as Minecraft's JSON text. */
public final class ChatCopy {
	public enum Format { TEXT, CODES, JSON }

	private ChatCopy() {
	}

	public static String copy(Component text, Format format) {
		return switch (format) {
			case TEXT -> text.getString();
			case CODES -> codes(text);
			case JSON -> Component.Serializer.toJson(text, CakeChat.registries());
		};
	}

	/**
	 * § codes for each run whose style differs from the one before: §r, the colour (a named colour's code, a hex colour
	 * as §x§R§R§G§G§B§B like Bukkit's), then bold/italic/underline/strikethrough/obfuscated.
	 */
	static String codes(Component text) {
		StringBuilder out = new StringBuilder();
		Style[] last = {Style.EMPTY};
		text.visit((style, string) -> {
			if (string.isEmpty()) {
				return Optional.empty();
			}
			if (!sameLook(style, last[0])) {
				if (!last[0].isEmpty()) {
					out.append("§r");
				}
				appendColor(out, style.getColor());
				if (style.isBold()) out.append("§l");
				if (style.isItalic()) out.append("§o");
				if (style.isUnderlined()) out.append("§n");
				if (style.isStrikethrough()) out.append("§m");
				if (style.isObfuscated()) out.append("§k");
				last[0] = style;
			}
			out.append(string);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private static boolean sameLook(Style a, Style b) {
		return Objects.equals(a.getColor(), b.getColor()) && a.isBold() == b.isBold() && a.isItalic() == b.isItalic()
				&& a.isUnderlined() == b.isUnderlined() && a.isStrikethrough() == b.isStrikethrough() && a.isObfuscated() == b.isObfuscated();
	}

	private static void appendColor(StringBuilder out, TextColor color) {
		if (color == null) {
			return;
		}
		ChatFormatting named = ChatFormatting.getByName(color.serialize());
		if (named != null && named.isColor()) {
			out.append('§').append(named.getChar());
			return;
		}
		out.append("§x");
		for (char c : String.format(Locale.ROOT, "%06x", color.getValue()).toCharArray()) {
			out.append('§').append(c);
		}
	}
}
