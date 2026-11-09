package io.github.harrythecake.cakecosmetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** What a place shows, from the player's choice and what they can pick (pure: no game state). */
final class Picker {
	private Picker() {
	}

	/**
	 * The pool a place draws from. ONE: the chosen background if it can still be picked (or the
	 * Vanilla tile). SHUFFLE: the ticked ones that can be picked; none ticked (or none left) = every
	 * pickable background. Empty only when nothing at all can be picked (then vanilla shows).
	 */
	static List<String> pool(Choices.Choice c, Collection<String> pickable) {
		if (c.mode == Choices.Mode.ONE) {
			if (Choices.VANILLA.equals(c.one) || pickable.contains(c.one)) {
				return List.of(c.one);
			}
		} else {
			List<String> ticked = new ArrayList<>();
			for (String id : c.shuffle) {
				if (Choices.VANILLA.equals(id) || pickable.contains(id)) {
					ticked.add(id);
				}
			}
			if (!ticked.isEmpty()) {
				return ticked;
			}
		}
		return List.copyOf(pickable);
	}

	/** One pick for the main menu, avoiding the previous one when there is a choice. */
	static String pickOne(List<String> pool, String previous, Random random) {
		if (pool.isEmpty()) {
			return Choices.VANILLA;
		}
		if (pool.size() == 1) {
			return pool.get(0);
		}
		List<String> rest = new ArrayList<>(pool);
		rest.remove(previous);
		return rest.get(random.nextInt(rest.size()));
	}

	/** The loading screens' slideshow order: the pool shuffled. */
	static List<String> order(List<String> pool, Random random) {
		List<String> out = new ArrayList<>(pool);
		Collections.shuffle(out, random);
		return out;
	}
}
