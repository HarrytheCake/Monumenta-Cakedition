package io.github.harrythecake.cakebook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTest {
	private static Library sample() {
		Library.Builder builder = new Library.Builder()
				.info("pack", "{\"title\": {\"en_us\": \"Pack guide\", \"zh_cn\": \"整合包指南\"}, \"order\": 0, \"icon\": \"minecraft:cake\"}")
				.page("pack", "en_us/index.md", "# Welcome")
				.page("pack", "en_us/keys.md", "---\norder: 1\n---\n# Keys")
				.page("pack", "en_us/mods/index.md", "---\norder: 2\n---\n# Mods")
				.page("pack", "en_us/mods/sodium.md", "# Sodium")
				.page("pack", "en_us/mods/iris.md", "# Iris")
				.page("pack", "en_us/mods/secret.md", "---\nhidden: true\n---\n# Secret")
				.page("pack", "zh_cn/mods/sodium.md", "# 钠")
				.page("pack", "README.md", "not a page")
				.file("pack", "images/map.png", "res:x:cakebook/pack/images/map.png")
				.page("help", "en_us/index.md", "# Help");
		return builder.build();
	}

	@Test
	void booksSortByOrderThenId() {
		assertEquals(List.of("pack", "help"), sample().books().stream().map(b -> b.id).toList());
	}

	@Test
	void titlesAndPagesFallBackByLanguage() {
		Book pack = sample().book("pack");
		assertEquals("整合包指南", pack.title("zh_cn"));
		assertEquals("整合包指南", pack.title("zh_tw"));
		assertEquals("Pack guide", pack.title("de_de"));
		assertEquals("钠", pack.page("mods/sodium", "zh_tw").title());
		assertEquals("Iris", pack.page("mods/iris", "zh_cn").title());
		assertEquals("Help", sample().book("help").title("en_us"));
		assertNull(pack.page("nope", "en_us"));
		assertEquals("minecraft:cake", pack.icon);
		assertEquals("res:x:cakebook/pack/images/map.png", pack.file("images/map.png"));
	}

	@Test
	void contentsAreFoldersThenOrderThenTitle() {
		List<Book.TocEntry> toc = sample().book("pack").toc("en_us");
		assertEquals(List.of(
				new Book.TocEntry("keys", "Keys", 0, null),
				new Book.TocEntry("mods/index", "Mods", 0, "mods"),
				new Book.TocEntry("mods/iris", "Iris", 1, null),
				new Book.TocEntry("mods/sodium", "Sodium", 1, null)), toc);
	}

	@Test
	void folderWithoutIndexGetsItsName() {
		Library library = new Library.Builder()
				.page("b", "en_us/index.md", "# B")
				.page("b", "en_us/boss-fights/kaul.md", "# Kaul")
				.build();
		assertEquals(new Book.TocEntry(null, "Boss fights", 0, "boss-fights"), library.book("b").toc("en_us").get(0));
	}

	@Test
	void laterFilesReplaceEarlierOnesAndBadBooksAreReported() {
		Library.Builder builder = new Library.Builder()
				.add("pack", "en_us/index.md", () -> "# Old", "res:a")
				.add("pack", "en_us/index.md", () -> "# New", "file:b")
				.add("Bad Name", "en_us/index.md", () -> "# x", "file:c")
				.add("empty", "book.json", () -> "{}", "file:d");
		Library library = builder.build();
		assertEquals("New", library.book("pack").page("index", "en_us").title());
		assertNull(library.book("empty"));
		assertEquals(2, builder.problems().size());
		assertTrue(builder.problems().get(0).contains("Bad Name"));
	}
}
