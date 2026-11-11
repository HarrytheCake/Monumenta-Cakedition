package io.github.harrythecake.cakebook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAndNotesTest {
	@TempDir
	Path dir;

	private static Library library() {
		return new Library.Builder()
				.page("pack", "en_us/index.md", "# Welcome\n\nThis pack is for Monumenta.")
				.page("pack", "en_us/mods/sodium.md", "# Sodium\n\nMakes rendering faster. Sodium settings are in Video Settings.")
				.page("pack", "en_us/mods/zoom.md", "# Zoomify\n\nPress the zoom key. Works with Sodium.")
				.page("pack", "zh_cn/mods/sodium.md", "# 钠\n\n让渲染更快。")
				.build();
	}

	@Test
	void titleHitsRankFirstAndEveryWordMustMatch() {
		List<Search.Hit> hits = Search.find(library(), List.of(), "en_us", "sodium", 10);
		assertEquals(List.of("mods/sodium", "mods/zoom"), hits.stream().map(Search.Hit::path).toList());
		assertEquals(List.of("mods/zoom"),
				Search.find(library(), List.of(), "en_us", "SODIUM zoom", 10).stream().map(Search.Hit::path).toList());
		assertTrue(Search.find(library(), List.of(), "en_us", "", 10).isEmpty());
	}

	@Test
	void chineseMatchesInsideWords() {
		List<Search.Hit> hits = Search.find(library(), List.of(), "zh_cn", "渲染", 10);
		assertEquals(1, hits.size());
		assertEquals("钠", hits.get(0).title());
		assertEquals("让渲染更快。", hits.get(0).snippet());
	}

	@Test
	void notesAreSearchedToo() {
		Notes.Note note = new Notes.Note("route", "Kaul route", "Bring **fire** resistance", 0);
		List<Search.Hit> hits = Search.find(library(), List.of(note), "en_us", "fire", 10);
		assertEquals(1, hits.size());
		assertEquals(new Links.Target(Links.Kind.NOTE, null, "route"), hits.get(0).target());
	}

	@Test
	void snippetsAreCutAroundTheMatch() {
		String line = "a".repeat(100) + " needle " + "b".repeat(100);
		String snippet = Search.snippet(line, line.indexOf("needle"));
		assertTrue(snippet.startsWith("…") && snippet.endsWith("…") && snippet.contains("needle"), snippet);
	}

	@Test
	void slugs() {
		assertEquals("kaul-route-2", Notes.slug("Kaul route #2", Set.of()));
		assertEquals("note", Notes.slug("凯尔路线", Set.of()));
		assertEquals("note-3", Notes.slug("凯尔路线", Set.of("note", "note-2")));
	}

	@Test
	void notesRoundTripThroughFiles() throws Exception {
		Notes notes = new Notes(dir.resolve("notes"));
		assertTrue(notes.list().isEmpty());
		Notes.Note created = notes.create("My \"best\" route: part 1");
		Notes.Note saved = notes.save(created.withText(created.title(), "- step one\n- [map](book:pack/mods/zoom)"));
		Notes.Note read = notes.get(created.id());
		assertEquals("my-best-route-part-1", read.id());
		assertEquals("My \"best\" route: part 1", read.title());
		assertEquals("- step one\n- [map](book:pack/mods/zoom)\n", read.body());
		assertEquals(saved.body(), read.body());
		assertEquals(1, notes.list().size());
		assertFalse(Files.exists(dir.resolve("notes").resolve(created.id() + ".md.tmp")));

		assertTrue(notes.delete(created.id()));
		assertNull(notes.get(created.id()));
		assertNull(notes.get("../escape"));
	}

	@Test
	void handWrittenNotesWithoutFrontMatterStillLoad() throws Exception {
		Path notesDir = dir.resolve("notes");
		Files.createDirectories(notesDir);
		Files.writeString(notesDir.resolve("plain.md"), "just text\r\n", StandardCharsets.UTF_8);
		Notes.Note note = new Notes(notesDir).get("plain");
		assertEquals("plain", note.title());
		assertEquals("just text\n", note.body());
	}
}
