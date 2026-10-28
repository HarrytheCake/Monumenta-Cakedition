package io.github.harrythecake.cakeupdater;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Warns once when the game's maximum memory (the launcher's setting) is outside the configured range, and again
 * only after the setting changes. The value warned about is kept in config/cakeupdater/memory-warned.txt.
 * Plain Java (no Minecraft classes), so it can be tested on its own.
 */
final class MemoryCheck {
	enum Problem { LOW, HIGH }

	/** A JVM given -Xmx3G can report a little less than 3 GB, so "low" leaves this much room. */
	private static final double LOW_TOLERANCE = 0.95;

	private MemoryCheck() {}

	static Problem problem(long maxMb, int minGb, int maxGb) {
		if (minGb > 0 && maxMb < minGb * 1024L * LOW_TOLERANCE) {
			return Problem.LOW;
		}
		if (maxGb > 0 && maxMb > maxGb * 1024L) {
			return Problem.HIGH;
		}
		return null;
	}

	/** The problem to warn about now, or null: none, or this same setting was already warned about. Records it. */
	static Problem warnOnce(Path gameDir, long maxMb, int minGb, int maxGb) {
		Problem problem = problem(maxMb, minGb, maxGb);
		Path file = gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve("memory-warned.txt");
		try {
			String warned = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : "";
			if (problem == null) {
				// back in range: a later change out of range is warned about again
				Files.deleteIfExists(file);
				return null;
			}
			if (warned.equals(String.valueOf(maxMb))) {
				return null;
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, String.valueOf(maxMb), StandardCharsets.UTF_8);
		} catch (Exception e) {
			// can't remember it: warn anyway, at worst once per start
		}
		return problem;
	}

	/** "3.5" for 3584 MB. */
	static String gb(long mb) {
		return String.format(Locale.ROOT, "%.1f", mb / 1024.0).replace(".0", "");
	}
}
