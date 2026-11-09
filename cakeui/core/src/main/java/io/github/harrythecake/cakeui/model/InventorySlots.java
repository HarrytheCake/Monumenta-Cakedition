package io.github.harrythecake.cakeui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which of the player's own inventory slots an {@code item} or {@code inventory} element shows (E4).
 * Indices are the player inventory's: 0–8 hotbar, 9–35 main, 36 feet, 37 legs, 38 chest, 39 head, 40 offhand.
 */
public final class InventorySlots {
	public static final int MAINHAND = -1;

	private InventorySlots() {
	}

	/**
	 * An inventory element's {@code slots}: hotbar, main, armor (head first), offhand, all (hotbar + main),
	 * a range "9-17", or a list "0,1,40". Empty when the text is none of these.
	 */
	public static List<Integer> parse(String spec) {
		List<Integer> out = new ArrayList<>();
		String s = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
		switch (s) {
			case "hotbar" -> range(out, 0, 8);
			case "main" -> range(out, 9, 35);
			case "all" -> {
				range(out, 9, 35);
				range(out, 0, 8);
			}
			case "armor" -> {
				out.add(39);
				out.add(38);
				out.add(37);
				out.add(36);
			}
			case "offhand" -> out.add(40);
			default -> {
				for (String part : s.split(",")) {
					part = part.trim();
					if (part.isEmpty()) {
						continue;
					}
					try {
						int dash = part.indexOf('-', 1);
						if (dash > 0) {
							range(out, Integer.parseInt(part.substring(0, dash).trim()), Integer.parseInt(part.substring(dash + 1).trim()));
						} else {
							add(out, Integer.parseInt(part));
						}
					} catch (NumberFormatException e) {
						return List.of();
					}
				}
			}
		}
		return out;
	}

	/**
	 * An item element's {@code item} when it names a slot ("slot:mainhand", "slot:offhand", "slot:head",
	 * "slot:chest", "slot:legs", "slot:feet", "slot:12"): the index ({@link #MAINHAND} for the held item),
	 * or null when it is an item id instead (or a bad slot).
	 */
	public static Integer slot(String item) {
		if (item == null || !item.startsWith("slot:")) {
			return null;
		}
		String s = item.substring(5).trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "mainhand", "hand" -> MAINHAND;
			case "offhand" -> 40;
			case "head" -> 39;
			case "chest" -> 38;
			case "legs" -> 37;
			case "feet" -> 36;
			default -> {
				try {
					int i = Integer.parseInt(s);
					yield i >= 0 && i <= 40 ? i : null;
				} catch (NumberFormatException e) {
					yield null;
				}
			}
		};
	}

	private static void range(List<Integer> out, int a, int b) {
		for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
			add(out, i);
		}
	}

	private static void add(List<Integer> out, int i) {
		if (i >= 0 && i <= 40) {
			out.add(i);
		}
	}
}
