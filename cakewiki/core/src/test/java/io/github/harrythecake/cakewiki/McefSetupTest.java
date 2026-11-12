package io.github.harrythecake.cakewiki;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McefSetupTest {
	private static final String MIRROR = "https://mcef-download.cinemamod.com";

	@Test
	void readyWinsOverEverything() {
		McefSetup s = McefSetup.of(true, true, false, 1f, MIRROR);
		assertEquals(McefSetup.Stage.READY, s.stage());
		assertNull(s.messageKey());
	}

	@Test
	void stillDownloadingShowsPercentAndWaits() {
		McefSetup s = McefSetup.of(false, false, false, 0.426f, MIRROR);
		assertEquals(McefSetup.Stage.WORKING, s.stage());
		assertEquals("cakewiki.mcef.working", s.messageKey());
		assertArrayEquals(new Object[] {43}, s.args());
		assertTrue(s.mayBecomeReady());
	}

	@Test
	void failedDownloadNamesTheSiteAndDoesNotWait() {
		// the case from the screenshot: MCEF's checksum download got Cloudflare's 403 at game start
		McefSetup s = McefSetup.of(false, false, true, 0f, MIRROR);
		assertEquals(McefSetup.Stage.DOWNLOAD_FAILED, s.stage());
		assertEquals("cakewiki.mcef.downloadFailed", s.messageKey());
		assertArrayEquals(new Object[] {"mcef-download.cinemamod.com", "mcef-download.cinemamod.com"}, s.args());
		assertFalse(s.mayBecomeReady());
	}

	@Test
	void failedWinsOverDone() {
		assertEquals(McefSetup.Stage.DOWNLOAD_FAILED, McefSetup.of(false, true, true, 1f, MIRROR).stage());
	}

	@Test
	void downloadedButNotStarted() {
		McefSetup s = McefSetup.of(false, true, false, 1f, MIRROR);
		assertEquals(McefSetup.Stage.NOT_STARTED, s.stage());
		assertEquals("cakewiki.mcef.failed", s.messageKey());
		assertTrue(s.mayBecomeReady()); // MCEF starts Chromium about a second after the download
	}

	@Test
	void hostFromTheMirrorSetting() {
		assertEquals("mirror.example.org", McefSetup.of(false, false, true, 0f, "https://mirror.example.org/mcef/").downloadHost());
		assertEquals(McefSetup.DEFAULT_HOST, McefSetup.of(false, false, true, 0f, null).downloadHost());
		assertEquals(McefSetup.DEFAULT_HOST, McefSetup.of(false, false, true, 0f, "null").downloadHost());
		assertEquals(McefSetup.DEFAULT_HOST, McefSetup.of(false, false, true, 0f, "not a url ::").downloadHost());
	}

	@Test
	void percentIsClamped() {
		assertEquals(0, McefSetup.of(false, false, false, Float.NaN, MIRROR).percent());
		assertEquals(0, McefSetup.of(false, false, false, -1f, MIRROR).percent());
		assertEquals(100, McefSetup.of(false, false, false, 2f, MIRROR).percent());
	}

	@Test
	void unknownKeepsTheOldMessage() {
		assertEquals("cakewiki.mcef.notReady", McefSetup.UNKNOWN.messageKey());
		assertTrue(McefSetup.UNKNOWN.mayBecomeReady());
	}
}
