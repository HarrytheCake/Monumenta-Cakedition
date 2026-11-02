package io.github.harrythecake.cakeupdater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuideNoticeTest {
	@TempDir
	Path game;

	private void shipPage() throws IOException {
		Path page = game.resolve("cakebook/books/cakedition/en_us/cake/index.md");
		Files.createDirectories(page.getParent());
		Files.writeString(page, "# Cakedition's own mods\n");
	}

	@Test
	void welcomeOnceThenOnceAfterEachUpdate() throws IOException {
		shipPage();
		assertEquals(GuideNotice.Kind.WELCOME, GuideNotice.check(game, "2.5.0", true));
		assertNull(GuideNotice.check(game, "2.5.0", true), "same version: nothing");
		assertEquals(GuideNotice.Kind.UPDATED, GuideNotice.check(game, "2.6.0", true));
		assertNull(GuideNotice.check(game, "2.6.0", true));
		assertEquals(GuideNotice.Kind.UPDATED, GuideNotice.check(game, null, true), "unknown version counts as a change once");
		assertNull(GuideNotice.check(game, "", true));
	}

	@Test
	void onlyWithCakeBookAndThePage() throws IOException {
		assertNull(GuideNotice.check(game, "2.5.0", true), "another pack: no page");
		shipPage();
		assertNull(GuideNotice.check(game, "2.5.0", false), "no CakeBook");
		assertEquals(GuideNotice.Kind.WELCOME, GuideNotice.check(game, "2.5.0", true), "nothing was recorded before");
	}
}
