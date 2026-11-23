package io.github.harrythecake.cakemonu;

import java.util.List;

/**
 * What CakeMonu needs from the ability bar it bridges (today: the Unofficial Monumenta Mod, in the Fabric
 * 1.20.4 part). Everything else (layout, screen, HUD editor) only talks to this. Render thread.
 */
public interface AbilitySource {
	/** One ability of the player's current class. {@code id} = UMM's order id ("class/ability name", lower case). */
	record Ability(String id, String name, String className) {
	}

	enum State {
		/** CakeMonu can take icons out of the bar. */
		READY,
		/** The Unofficial Monumenta Mod isn't installed. */
		NO_UMM,
		/** UMM is there, but a version CakeMonu wasn't checked against: CakeMonu leaves its bar alone. */
		UNSUPPORTED
	}

	State state();

	/** UMM's version (for the "not supported" line), or "". */
	default String version() {
		return "";
	}

	/** The current class's abilities, in UMM's order (empty until Monumenta sent a class). */
	List<Ability> current();

	/** UMM's ability bar is switched on in its settings. */
	boolean barEnabled();

	int iconSize();

	int iconGap();

	boolean horizontal();

	/** UMM's bar box {x, y, w, h} in GUI pixels right now, or null. */
	double[] barBox(int screenWidth, int screenHeight);

	AbilitySource NONE = new AbilitySource() {
		@Override
		public State state() {
			return State.NO_UMM;
		}

		@Override
		public List<Ability> current() {
			return List.of();
		}

		@Override
		public boolean barEnabled() {
			return false;
		}

		@Override
		public int iconSize() {
			return 32;
		}

		@Override
		public int iconGap() {
			return 0;
		}

		@Override
		public boolean horizontal() {
			return true;
		}

		@Override
		public double[] barBox(int screenWidth, int screenHeight) {
			return null;
		}
	};
}
