package io.github.harrythecake.cakewiki.net;

import io.github.harrythecake.cakewiki.net.WikiClientTest.FakeTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class PagesTest {
	@TempDir
	Path dir;

	private static final String PAGE = "{\"parse\":{\"title\":\"Sierhaven\",\"displaytitle\":\"Sierhaven\",\"revid\":7,\"text\":\"<p>v7</p>\"}}";

	/** A clock the test moves forward by hand. */
	static final class TestClock extends Clock {
		Instant now = Instant.parse("2026-10-28T12:00:00Z");

		@Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}

	/** Fails every request while {@code down} is set. */
	static final class SwitchTransport implements WikiClient.Transport {
		final FakeTransport up = new FakeTransport();
		boolean down;
		int calls;

		@Override
		public CompletableFuture<byte[]> get(URI uri) {
			calls++;
			return down ? CompletableFuture.failedFuture(new java.io.IOException("offline")) : up.get(uri);
		}
	}

	private Pages pages(SwitchTransport t, TestClock clock, List<Throwable> errors) {
		return new Pages(new WikiClient("https://wiki.test", t), new PageCache(dir), clock, Duration.ofHours(24), errors::add);
	}

	@Test
	void freshCacheSkipsTheNetworkAndOldCacheRefetches() {
		SwitchTransport t = new SwitchTransport();
		t.up.reply("action=parse", PAGE);
		TestClock clock = new TestClock();
		Pages p = pages(t, clock, new ArrayList<>());

		assertEquals("<p>v7</p>", p.load("Sierhaven").join().page().html());
		assertEquals(1, t.calls);
		clock.now = clock.now.plus(Duration.ofHours(23));
		assertFalse(p.load("sierhaven").join().stale(), "first letter case doesn't matter");
		assertEquals(1, t.calls, "still fresh: no request");
		clock.now = clock.now.plus(Duration.ofHours(2));
		p.load("Sierhaven").join();
		assertEquals(2, t.calls, "older than 24 h: asked again");
	}

	@Test
	void offlineFallsBackToStaleCopyButNotForMissingPages() {
		SwitchTransport t = new SwitchTransport();
		t.up.reply("action=parse", PAGE);
		TestClock clock = new TestClock();
		Pages p = pages(t, clock, new ArrayList<>());
		p.load("Sierhaven").join();

		clock.now = clock.now.plus(Duration.ofDays(3));
		t.down = true;
		Pages.Loaded loaded = p.load("Sierhaven").join();
		assertTrue(loaded.stale());
		assertEquals("<p>v7</p>", loaded.page().html());

		assertThrows(CompletionException.class, () -> p.load("Never Fetched").join());

		SwitchTransport gone = new SwitchTransport();
		gone.up.reply("action=parse", "{\"error\":{\"code\":\"missingtitle\",\"info\":\"\"}}");
		Pages p2 = pages(gone, clock, new ArrayList<>());
		CompletionException e = assertThrows(CompletionException.class, () -> p2.load("Sierhaven").join());
		assertEquals("missingtitle", ((WikiException) e.getCause()).code());
	}

	@Test
	void redirectIsCachedUnderBothTitles() {
		SwitchTransport t = new SwitchTransport();
		t.up.reply("action=parse", PAGE);
		Pages p = pages(t, new TestClock(), new ArrayList<>());
		p.load("Sier Haven").join();
		PageCache cache = new PageCache(dir);
		assertTrue(cache.get("Sier Haven").isPresent());
		assertEquals("Sierhaven", cache.get("Sierhaven").orElseThrow().page().title());
	}

	@Test
	void cacheKeyNormalisesLikeMediaWiki() {
		assertEquals(PageCache.key("King's Valley"), PageCache.key("king's_Valley"));
		assertNotEquals(PageCache.key("King's Valley"), PageCache.key("King's valley"));
	}
}
