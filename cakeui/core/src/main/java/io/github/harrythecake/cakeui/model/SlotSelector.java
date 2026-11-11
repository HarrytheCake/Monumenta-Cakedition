package io.github.harrythecake.cakeui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Which slots of a container screen an entry of the `slots` port is for: `"*"` (every slot), `"player"` (the
 * player's inventory slots), `"container"` (every other slot), a slot number (`"0"`), a range (`"3-5"`) or a
 * list of those (`"0,2,4-6"`). Numbers are the slot's place in the menu (its index), as dev mode's ids show.
 * When several entries match a slot, each property comes from the most specific one: a single number, then a
 * range or list, then `player`/`container`, then `*`.
 */
public final class SlotSelector {
	/** How specific a selector is: higher wins. */
	public static final int ANY = 0;
	public static final int GROUP = 1;
	public static final int SET = 2;
	public static final int ONE = 3;

	private final String text;
	private final int specificity;
	/** "player" or "container" for a group, else null. */
	private final String group;
	/** {from, to} pairs, inclusive. */
	private final int[] ranges;

	private SlotSelector(String text, int specificity, String group, int[] ranges) {
		this.text = text;
		this.specificity = specificity;
		this.group = group;
		this.ranges = ranges;
	}

	/** The selector for a key of the `slots` port, or null when the key isn't one (the Loader reports it). */
	public static SlotSelector parse(String key) {
		String k = key.trim();
		if (k.equals("*")) {
			return new SlotSelector(key, ANY, null, null);
		}
		if (k.equals("player") || k.equals("container")) {
			return new SlotSelector(key, GROUP, k, null);
		}
		if (k.isEmpty()) {
			return null;
		}
		List<int[]> parts = new ArrayList<>();
		for (String part : k.split(",", -1)) {
			String p = part.trim();
			int dash = p.indexOf('-');
			try {
				if (dash < 0) {
					int n = number(p);
					parts.add(new int[] {n, n});
				} else {
					int a = number(p.substring(0, dash).trim());
					int b = number(p.substring(dash + 1).trim());
					if (b < a) {
						return null;
					}
					parts.add(new int[] {a, b});
				}
			} catch (NumberFormatException e) {
				return null;
			}
		}
		int[] flat = new int[parts.size() * 2];
		for (int i = 0; i < parts.size(); i++) {
			flat[i * 2] = parts.get(i)[0];
			flat[i * 2 + 1] = parts.get(i)[1];
		}
		boolean one = parts.size() == 1 && flat[0] == flat[1];
		return new SlotSelector(key, one ? ONE : SET, null, flat);
	}

	private static int number(String s) {
		if (s.isEmpty() || s.length() > 4 || !s.chars().allMatch(Character::isDigit)) {
			throw new NumberFormatException(s);
		}
		return Integer.parseInt(s);
	}

	public int specificity() {
		return specificity;
	}

	/** Whether this selector covers the slot with this index; {@code player}: it holds the player's inventory. */
	public boolean matches(int index, boolean player) {
		if (specificity == ANY) {
			return true;
		}
		if (group != null) {
			return group.equals("player") == player;
		}
		for (int i = 0; i < ranges.length; i += 2) {
			if (index >= ranges[i] && index <= ranges[i + 1]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return text;
	}
}
