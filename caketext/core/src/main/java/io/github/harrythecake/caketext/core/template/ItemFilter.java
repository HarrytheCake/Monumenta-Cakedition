package io.github.harrythecake.caketext.core.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Item types the translator leaves untranslated (the collector still collects them): by default every sign, book and
 * shulker box (their text is written by players, or lists other items). Patterns are item ids with {@code *} wildcards;
 * without a namespace they match the id's path, so {@code *sign} covers {@code minecraft:oak_sign} and
 * {@code minecraft:oak_hanging_sign}.
 */
public final class ItemFilter {
	/** Signs and hanging signs, every kind of book, every shulker box. */
	public static final List<String> DEFAULT = List.of("*sign", "*book", "*shulker_box");

	private final List<Pattern> patterns = new ArrayList<>();

	private ItemFilter(List<String> globs) {
		for (String glob : globs) {
			String g = glob.strip().toLowerCase(Locale.ROOT);
			if (g.isEmpty()) {
				continue;
			}
			String regex = Arrays.stream(g.split("\\*", -1)).map(Pattern::quote).reduce((a, b) -> a + ".*" + b).orElse("");
			// no namespace: match the path, in any namespace
			patterns.add(Pattern.compile(g.contains(":") ? regex : "[^:]*:" + regex));
		}
	}

	public static ItemFilter of(List<String> globs) {
		return new ItemFilter(globs == null ? List.of() : globs);
	}

	/** "a, b, c" → [a, b, c], for a text box. */
	public static List<String> parse(String commaSeparated) {
		return Arrays.stream(commaSeparated.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
	}

	public boolean matches(String itemId) {
		String id = itemId.toLowerCase(Locale.ROOT);
		for (Pattern p : patterns) {
			if (p.matcher(id).matches()) {
				return true;
			}
		}
		return false;
	}

	public boolean matches(ItemStack stack) {
		return stack != null && !stack.isEmpty() && matches(Registries.ITEM.getId(stack.getItem()).toString());
	}
}
