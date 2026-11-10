package io.github.harrythecake.cakewiki;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookmarksTest {
	private static final String M = "monumenta", W = "otherwiki";

	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("config/cakewiki/bookmarks.json");
	}

	private static List<String> titles(Bookmarks b) {
		return b.all(M).stream().map(Bookmarks.Bookmark::title).toList();
	}

	@Test
	void toggleAddsNewestFirstAndRemoves() throws IOException {
		Bookmarks b = new Bookmarks(file());
		assertTrue(b.toggle(M, "Sierhaven"));
		assertTrue(b.toggle(M, "Moonscarred Clockblade"));
		assertEquals(List.of("Moonscarred Clockblade", "Sierhaven"), titles(b));
		assertFalse(b.toggle(M, "Sierhaven"));
		assertEquals(List.of("Moonscarred Clockblade"), titles(b));
	}

	@Test
	void matchesTitlesLikeTheWiki() throws IOException {
		Bookmarks b = new Bookmarks(file());
		b.toggle(M, "King's Valley");
		assertTrue(b.contains(M, "king's_Valley"));
		assertFalse(b.contains(M, "King's valley"), "only the first letter ignores case");
		assertFalse(b.toggle(M, "King's_Valley"), "same page: removed, not added twice");
	}

	@Test
	void survivesARestart() throws IOException {
		Bookmarks b = new Bookmarks(file());
		b.toggle(M, "Sierhaven");
		b.toggle(M, "Celsian Isles");
		Bookmarks again = new Bookmarks(file());
		assertEquals(List.of("Celsian Isles", "Sierhaven"), titles(again));
		assertEquals(b.all(M).get(0).added(), again.all(M).get(0).added());
		again.remove(M, "Sierhaven");
		assertEquals(List.of("Celsian Isles"), titles(new Bookmarks(file())));
	}

	@Test
	void unreadableFileIsKeptAside() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ not json");
		Bookmarks b = new Bookmarks(file());
		assertTrue(b.all(M).isEmpty());
		assertTrue(Files.exists(file().resolveSibling("bookmarks.json.bad")), "the player's broken file isn't lost");
		b.toggle(M, "Sierhaven");
		assertEquals(List.of("Sierhaven"), titles(new Bookmarks(file())));
	}

	@Test
	void eachSiteHasItsOwnBookmarks() throws IOException {
		Bookmarks b = new Bookmarks(file());
		b.toggle(M, "Sierhaven");
		b.toggle(W, "Sierhaven");
		b.toggle(W, "Rules");
		assertEquals(List.of("Sierhaven"), titles(b));
		assertEquals(List.of("Rules", "Sierhaven"), new Bookmarks(file()).all(W).stream().map(Bookmarks.Bookmark::title).toList());
		b.remove(W, "Sierhaven");
		assertTrue(b.contains(M, "Sierhaven"), "removing on one site leaves the other");
	}

	@Test
	void bookmarksSavedBeforeSitesBelongToTheMonumentaWiki() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{\"version\":1,\"bookmarks\":[{\"title\":\"Sierhaven\",\"added\":\"2026-10-29T10:00:00Z\"}]}");
		Bookmarks b = new Bookmarks(file());
		assertEquals(List.of("Sierhaven"), titles(b));
		assertTrue(b.all(W).isEmpty());
	}
}
