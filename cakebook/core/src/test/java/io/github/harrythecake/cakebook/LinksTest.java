package io.github.harrythecake.cakebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinksTest {
	private static Links.Target page(String book, String path) {
		return Links.Target.page(book, path);
	}

	@Test
	void relativeLinksWorkLikeOnGitHub() {
		assertEquals(page("pack", "mods/iris"), Links.resolve("pack", "mods/sodium", "iris.md"));
		assertEquals(page("pack", "mods/iris"), Links.resolve("pack", "mods/sodium", "./iris"));
		assertEquals(page("pack", "keys"), Links.resolve("pack", "mods/sodium", "../keys.md#zoom"));
		assertEquals(page("pack", "index"), Links.resolve("pack", "mods/sodium", "../"));
		assertEquals(page("pack", "mods/index"), Links.resolve("pack", "mods/sodium", "./"));
		assertEquals(page("pack", "keys"), Links.resolve("pack", "mods/sodium", "/keys"));
		assertEquals(page("pack", "mods/sodium"), Links.resolve("pack", "mods/sodium", "#settings"));
		assertNull(Links.resolve("pack", "keys", "../../outside"));
	}

	@Test
	void otherBooksNotesAndWeb() {
		assertEquals(page("cakedition", "index"), Links.resolve("pack", "x", "book:cakedition"));
		assertEquals(page("cakedition", "mods/sodium"), Links.resolve("pack", "x", "book:cakedition/mods/sodium.md"));
		assertEquals(new Links.Target(Links.Kind.NOTE, null, "kaul-route"), Links.resolve("pack", "x", "note:kaul-route"));
		assertEquals(Links.Kind.URL, Links.resolve("pack", "x", "https://example.org/a").kind());
		assertNull(Links.resolve("pack", "x", "mailto:someone@example.org"));
	}

	@Test
	void inNotesABareNameIsANote() {
		assertEquals(new Links.Target(Links.Kind.NOTE, null, "loot"), Links.resolve(null, "", "loot.md"));
		assertNull(Links.resolve(null, "", "a/b"));
		assertEquals(page("cakedition", "index"), Links.resolve(null, "", "book:cakedition"));
	}

	@Test
	void canonicalLinksRoundTrip() {
		for (String link : new String[] {"book:cakedition", "book:cakedition/mods/sodium", "note:x", "https://e.org"}) {
			assertEquals(link, Links.resolve(null, "", link).toLink());
		}
	}

	@Test
	void images() {
		assertEquals(new Links.Image("images/map.png", null), Links.image("en_us/mods/xaero.md", "../../images/map.png"));
		assertEquals(new Links.Image("images/map.png", null), Links.image("en_us/index.md", "/images/map.png"));
		assertEquals(new Links.Image(null, "minecraft:textures/item/diamond.png"),
				Links.image("en_us/index.md", "minecraft:textures/item/diamond.png"));
		assertNull(Links.image("en_us/index.md", "https://example.org/x.png"));
		assertNull(Links.image("en_us/index.md", "../../x.png"));
	}
}
