package io.github.harrythecake.cakecosmetic;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * config/cakecosmetic/choices.json: what the player picked for each place. Per player, never shipped
 * (YOSBR_EXCLUDE). Default for both places: shuffle over everything unlocked.
 */
final class Choices {
	static final String FILE = "choices.json";
	/** The "Vanilla" tile: vanilla's panorama on the main menu, vanilla's background on loading screens. */
	static final String VANILLA = "vanilla";

	enum Mode {
		ONE, SHUFFLE
	}

	/** One place's choice. */
	static final class Choice {
		Mode mode = Mode.SHUFFLE;
		/** The background shown in ONE mode. */
		String one = "";
		/** The backgrounds ticked for SHUFFLE; none ticked = every unlocked background. */
		Set<String> shuffle = new LinkedHashSet<>();

		Choice fix() {
			if (mode == null) {
				mode = Mode.SHUFFLE;
			}
			if (one == null) {
				one = "";
			}
			if (shuffle == null) {
				shuffle = new LinkedHashSet<>();
			}
			return this;
		}
	}

	Choice mainMenu = new Choice();
	Choice loading = new Choice();

	static Choices load(Path dir) {
		Choices c = JsonFiles.read(dir.resolve(FILE), Choices.class, Choices::new);
		c.mainMenu = c.mainMenu == null ? new Choice() : c.mainMenu.fix();
		c.loading = c.loading == null ? new Choice() : c.loading.fix();
		return c;
	}

	void save(Path dir) {
		JsonFiles.write(dir.resolve(FILE), this);
	}
}
