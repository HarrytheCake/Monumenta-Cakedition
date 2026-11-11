package io.github.harrythecake.cakecosmetic;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * config/cakecosmetic/unlocked.json: what this install has unlocked. Per install (no player names
 * or UUIDs), permanent, never shipped (YOSBR_EXCLUDE).
 */
final class UnlockState {
	static final String FILE = "unlocked.json";

	/** Backgrounds unlocked for good. */
	Set<String> backgrounds = new LinkedHashSet<>();
	/** Earned advancements that some rule names (kept so rules needing several advancements add up). */
	Set<String> advancements = new LinkedHashSet<>();

	static UnlockState load(Path dir) {
		UnlockState s = JsonFiles.read(dir.resolve(FILE), UnlockState.class, UnlockState::new);
		if (s.backgrounds == null) {
			s.backgrounds = new LinkedHashSet<>();
		}
		if (s.advancements == null) {
			s.advancements = new LinkedHashSet<>();
		}
		return s;
	}

	void save(Path dir) {
		JsonFiles.write(dir.resolve(FILE), this);
	}
}
