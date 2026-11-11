package io.github.harrythecake.caketext.core.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Rebuilding styled text run by run: split it, add a hover, recolour it. */
public final class TextUtil {
	private record Run(Style style, String text) {
	}

	private TextUtil() {
	}

	private static List<Run> runs(Component text) {
		List<Run> runs = new ArrayList<>();
		text.visit((style, s) -> {
			if (!s.isEmpty()) {
				runs.add(new Run(style, s));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return runs;
	}

	private static MutableComponent build(List<Run> runs, UnaryOperator<Style> restyle) {
		MutableComponent root = Component.empty();
		for (Run r : runs) {
			root.append(Component.literal(r.text).setStyle(restyle.apply(r.style)));
		}
		return root;
	}

	/** {@code [before, after]} split at {@code index} of {@code text.getString()}, styles kept. */
	public static MutableComponent[] split(Component text, int index) {
		List<Run> before = new ArrayList<>();
		List<Run> after = new ArrayList<>();
		int pos = 0;
		for (Run r : runs(text)) {
			int end = pos + r.text.length();
			if (end <= index) {
				before.add(r);
			} else if (pos >= index) {
				after.add(r);
			} else {
				before.add(new Run(r.style, r.text.substring(0, index - pos)));
				after.add(new Run(r.style, r.text.substring(index - pos)));
			}
			pos = end;
		}
		return new MutableComponent[] {build(before, s -> s), build(after, s -> s)};
	}

	/** Same text, with {@code hover} on every part that has no hover of its own (item links keep theirs). */
	public static MutableComponent withHover(Component text, Component hover) {
		HoverEvent event = new HoverEvent.ShowText(hover);
		return build(runs(text), s -> s.getHoverEvent() == null ? s.withHoverEvent(event) : s);
	}

	/** Same text in one colour (fonts kept, so resource-pack icons still show). */
	public static MutableComponent recolor(Component text, ChatFormatting color) {
		return build(runs(text), s -> s.withColor(color));
	}
}
