package io.github.harrythecake.cakewiki.net;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudflareTest {
	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void challengeByHeader() {
		Map<String, List<String>> h = Map.of("CF-Mitigated", List.of("challenge"), "cf-ray", List.of("8abc123-TPE"));
		assertTrue(Cloudflare.isChallenge(403, h, b("")));
		assertEquals("8abc123-TPE", Cloudflare.ray(h));
	}

	@Test
	void challengeByPage() {
		Map<String, List<String>> h = Map.of("server", List.of("cloudflare"));
		assertTrue(Cloudflare.isChallenge(403, h, b("<html><title>Just a moment...</title><script src=\"/cdn-cgi/challenge-platform/x\"></script>")));
		assertTrue(Cloudflare.isChallenge(503, h, b("<div class=\"cf-chl-widget\"></div>")));
	}

	@Test
	void otherErrorsAreNot() {
		Map<String, List<String>> cf = Map.of("server", List.of("cloudflare"));
		assertFalse(Cloudflare.isChallenge(403, cf, b("{\"error\":\"forbidden\"}")), "a plain 403 (e.g. a blocked IP the wiki answers itself)");
		assertFalse(Cloudflare.isChallenge(404, Map.of("cf-mitigated", List.of("challenge")), b("")));
		assertFalse(Cloudflare.isChallenge(403, Map.of("server", List.of("nginx")), b("Just a moment")));
		assertFalse(Cloudflare.isChallenge(403, null, null));
		assertEquals("", Cloudflare.ray(Map.of()));
	}

	@Test
	void blockedException() {
		WikiException e = WikiException.blocked(403, "monumenta.wiki.gg", "8abc");
		assertTrue(e.blocked());
		assertEquals("monumenta.wiki.gg", e.host());
		assertEquals(403, e.status());
		assertFalse(WikiException.http(403, "x").blocked());
	}

	@Test
	void routerSendsARoutedHostElsewhereAndDropsItWhenBlockedAgain() throws Exception {
		AtomicInteger direct = new AtomicInteger();
		AtomicInteger browser = new AtomicInteger();
		HostRouter router = new HostRouter(uri -> {
			direct.incrementAndGet();
			return CompletableFuture.completedFuture(b("direct"));
		});
		URI wiki = URI.create("https://monumenta.wiki.gg/api.php?x=1");
		URI other = URI.create("https://example.org/");
		assertArrayEquals(b("direct"), router.get(wiki).get());
		boolean[] blockedNext = {false};
		router.route("Monumenta.Wiki.gg", uri -> {
			browser.incrementAndGet();
			return blockedNext[0] ? CompletableFuture.failedFuture(WikiException.blocked(403, uri.getHost(), ""))
					: CompletableFuture.completedFuture(b("browser"));
		});
		assertTrue(router.routed("monumenta.wiki.gg"));
		assertArrayEquals(b("browser"), router.get(wiki).get());
		assertArrayEquals(b("direct"), router.get(other).get(), "other hosts still go direct");
		blockedNext[0] = true;
		ExecutionException ex = assertThrows(ExecutionException.class, () -> router.get(wiki).get());
		assertTrue(ex.getCause() instanceof WikiException w && w.blocked());
		assertFalse(router.routed("monumenta.wiki.gg"), "a blocked in-game browser is dropped: the player is asked again");
		assertArrayEquals(b("direct"), router.get(wiki).get());
		assertEquals(2, browser.get());
		assertEquals(3, direct.get());
	}
}
