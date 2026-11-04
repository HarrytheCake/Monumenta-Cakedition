package io.github.harrythecake.cakeupdater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogTest {
	@TempDir
	Path game;

	private void write(String version, String lang, String text) throws Exception {
		Path file = Changelog.dir(game).resolve(version).resolve(lang + ".md");
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	private static List<String> versions(List<Changelog.Entry> entries) {
		return entries.stream().map(Changelog.Entry::version).toList();
	}

	@Test
	void versionsOrderByNumberNotText() {
		assertTrue(Changelog.compare("2.10.0", "2.9.1") > 0);
		assertTrue(Changelog.compare("2.3.0", "2.3") == 0);
		assertTrue(Changelog.compare("2.3.0-beta", "2.3.0") < 0);
		assertTrue(Changelog.compare("2.3.0", "2.2.2") > 0);
	}

	@Test
	void skippedVersionsAreAllShownNewestFirst() throws Exception {
		for (String v : List.of("2.2.2", "2.3.0", "2.3.1", "2.3.2", "2.4.0")) {
			write(v, "en_us", "- change in " + v);
		}
		assertEquals(List.of("2.3.2", "2.3.1", "2.3.0"), versions(Changelog.between(game, "2.2.2", "2.3.2", "en_us")));
	}

	@Test
	void unknownPreviousVersionShowsOnlyTheNewOne() throws Exception {
		write("2.3.0", "en_us", "- a");
		write("2.3.1", "en_us", "- b");
		assertEquals(List.of("2.3.1"), versions(Changelog.between(game, "?", "2.3.1", "en_us")));
		assertEquals(List.of(), Changelog.between(game, "2.3.0", "?", "en_us"));
		assertEquals(List.of("2.3.1", "2.3.0"), versions(Changelog.between(game, null, null, "en_us")));
	}

	@Test
	void sameVersionHasNoChangelog() throws Exception {
		write("2.3.0", "en_us", "- a");
		assertEquals(List.of(), Changelog.between(game, "2.3.0", "2.3.0", "en_us"));
	}

	@Test
	void traditionalChineseFallsBackToSimplifiedThenEnglish() throws Exception {
		write("2.3.0", "en_us", "- english");
		write("2.3.0", "zh_cn", "- 简体");
		assertEquals(List.of("简体"), Changelog.read(game, "2.3.0", "zh_tw").items());
		assertEquals(List.of("简体"), Changelog.read(game, "2.3.0", "zh_hk").items());
		assertEquals(List.of("english"), Changelog.read(game, "2.3.0", "de_de").items());
		write("2.3.0", "zh_tw", "- 繁體");
		assertEquals(List.of("繁體"), Changelog.read(game, "2.3.0", "zh_tw").items());
		assertEquals(List.of("zh_hk", "zh_tw", "zh_cn", "en_us"), Changelog.fallback("zh_hk"));
		assertNull(Changelog.read(game, "9.9.9", "en_us"));
	}

	@Test
	void parsesHeadingsItemsAndText() {
		List<Changelog.Line> lines = Changelog.parse("""
				**Updates now happen inside the game.** More text.

				**Added**
				- [Cakedition Updater](https://example.com) (our own): `pack` updates.
				* Monumenta Addons
				## Removed
				- **Very Many Players**

				---
				""");
		assertEquals(List.of(
				new Changelog.Line(Changelog.Kind.TEXT, "Updates now happen inside the game. More text."),
				new Changelog.Line(Changelog.Kind.HEADING, "Added"),
				new Changelog.Line(Changelog.Kind.ITEM, "Cakedition Updater (our own): pack updates."),
				new Changelog.Line(Changelog.Kind.ITEM, "Monumenta Addons"),
				new Changelog.Line(Changelog.Kind.HEADING, "Removed"),
				new Changelog.Line(Changelog.Kind.ITEM, "Very Many Players")), lines);
	}
}
