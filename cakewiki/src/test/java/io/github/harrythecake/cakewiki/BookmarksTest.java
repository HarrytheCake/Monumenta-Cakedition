package io.github.harrythecake.cakewiki;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookmarksTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("config/cakewiki/bookmarks.json");
	}

	private static List<String> titles(Bookmarks b) {
		return b.all().stream().map(Bookmarks.Bookmark::title).toList();
	}

	@Test
	void toggleAddsNewestFirstAndRemoves() throws IOException {
		Bookmarks b = new Bookmarks(file());
		assertTrue(b.toggle("Sierhaven"));
		assertTrue(b.toggle("Moonscarred Clockblade"));
		assertEquals(List.of("Moonscarred Clockblade", "Sierhaven"), titles(b));
		assertFalse(b.toggle("Sierhaven"));
		assertEquals(List.of("Moonscarred Clockblade"), titles(b));
	}

	@Test
	void matchesTitlesLikeTheWiki() throws IOException {
		Bookmarks b = new Bookmarks(file());
		b.toggle("King's Valley");
		assertTrue(b.contains("king's_Valley"));
		assertFalse(b.contains("King's valley"), "only the first letter ignores case");
		assertFalse(b.toggle("King's_Valley"), "same page: removed, not added twice");
	}

	@Test
	void survivesARestart() throws IOException {
		Bookmarks b = new Bookmarks(file());
		b.toggle("Sierhaven");
		b.toggle("Celsian Isles");
		Bookmarks again = new Bookmarks(file());
		assertEquals(List.of("Celsian Isles", "Sierhaven"), titles(again));
		assertEquals(b.all().get(0).added(), again.all().get(0).added());
		again.remove("Sierhaven");
		assertEquals(List.of("Celsian Isles"), titles(new Bookmarks(file())));
	}

	@Test
	void unreadableFileIsKeptAside() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ not json");
		Bookmarks b = new Bookmarks(file());
		assertTrue(b.all().isEmpty());
		assertTrue(Files.exists(file().resolveSibling("bookmarks.json.bad")), "the player's broken file isn't lost");
		b.toggle("Sierhaven");
		assertEquals(List.of("Sierhaven"), titles(new Bookmarks(file())));
	}
}
