package io.github.harrythecake.cakeupdater;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The chat line pointing to the pack guide's "Cakedition's own mods" chapter (user's request 2026-11-03): on the first
 * launch, and once after every pack update however it was installed. The pack version it was last shown for is kept
 * in config/cakeupdater/guide-shown.txt. Only when CakeBook is installed and the pack ships the page, so another pack
 * using this mod never sees it. Plain Java (no Minecraft classes), so it can be tested on its own.
 */
final class GuideNotice {
	enum Kind { WELCOME, UPDATED }

	/** The CakeBook page the button opens ({@code /cakebook open <PAGE>}). */
	static final String PAGE = "cakedition/cake/index";
	/** Where the pack ships that page (any language will do; en_us always exists). */
	private static final String PAGE_FILE = "cakebook/books/cakedition/en_us/cake/index.md";
	private static final String SHOWN = "guide-shown.txt";

	private GuideNotice() {}

	/** What to show now, or null; records the version. {@code version} null (unknown) counts as "?". */
	static Kind check(Path gameDir, String version, boolean cakeBookLoaded) {
		if (!cakeBookLoaded || !Files.isRegularFile(gameDir.resolve(PAGE_FILE))) {
			return null;
		}
		String current = version == null || version.isBlank() ? "?" : version.strip();
		Path file = gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve(SHOWN);
		try {
			String shown = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : null;
			if (current.equals(shown)) {
				return null;
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, current, StandardCharsets.UTF_8);
			return shown == null ? Kind.WELCOME : Kind.UPDATED;
		} catch (Exception e) {
			// can't remember it: don't nag on every start
			return null;
		}
	}
}
