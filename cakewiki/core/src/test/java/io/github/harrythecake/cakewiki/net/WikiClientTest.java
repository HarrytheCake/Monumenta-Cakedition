package io.github.harrythecake.cakewiki.net;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class WikiClientTest {
	/** Answers each request with the reply registered for the first key its query string contains. */
	static final class FakeTransport implements WikiClient.Transport {
		final Map<String, String> replies = new LinkedHashMap<>();
		final List<URI> requests = new ArrayList<>();

		FakeTransport reply(String queryContains, String json) {
			replies.put(queryContains, json);
			return this;
		}

		@Override
		public CompletableFuture<byte[]> get(URI uri) {
			requests.add(uri);
			for (var e : replies.entrySet()) {
				if (uri.getRawQuery().contains(e.getKey())) {
					return CompletableFuture.completedFuture(e.getValue().getBytes(StandardCharsets.UTF_8));
				}
			}
			return CompletableFuture.failedFuture(new WikiException("http", "no reply for " + uri));
		}
	}

	@Test
	void parseFollowsRedirectsAndCleansDisplayTitle() {
		FakeTransport t = new FakeTransport().reply("action=parse",
				"{\"parse\":{\"title\":\"King's Valley\",\"displaytitle\":\"<span class=\\\"x\\\">King's Valley</span>\",\"revid\":42,\"text\":\"<p>Hi</p>\"}}");
		WikiClient.Page page = new WikiClient("https://wiki.test/", t).parse("Kings valley").join();
		assertEquals("King's Valley", page.title());
		assertEquals("King's Valley", page.displayTitle());
		assertEquals(42, page.revision());
		assertEquals("<p>Hi</p>", page.html());
		String q = t.requests.get(0).getRawQuery();
		assertTrue(q.contains("page=Kings%20valley"), q);
		assertTrue(q.contains("redirects=1") && q.contains("formatversion=2") && q.contains("format=json"), q);
		assertEquals("https://wiki.test/api.php", t.requests.get(0).toString().split("\\?")[0]);
	}

	@Test
	void apiErrorsBecomeWikiExceptions() {
		FakeTransport t = new FakeTransport().reply("action=parse",
				"{\"error\":{\"code\":\"missingtitle\",\"info\":\"The page you specified doesn't exist.\"}}");
		CompletionException e = assertThrows(CompletionException.class, () -> new WikiClient("https://wiki.test", t).parse("Nope").join());
		assertEquals("missingtitle", ((WikiException) e.getCause()).code());
	}

	@Test
	void suggestReadsOpensearch() {
		FakeTransport t = new FakeTransport().reply("action=opensearch",
				"[\"moon\",[\"Moonscarred Clockblade\",\"Moonlight\"],[\"\",\"\"],[\"u1\",\"u2\"]]");
		WikiClient c = new WikiClient("https://wiki.test", t);
		assertEquals(List.of("Moonscarred Clockblade", "Moonlight"), c.suggest("moon", 8).join());
		assertEquals(List.of(), c.suggest("  ", 8).join());
		assertEquals(1, t.requests.size(), "blank input makes no request");
	}

	@Test
	void resolvePrefersExactTitleThenSearch() {
		FakeTransport exact = new FakeTransport().reply("titles=",
				"{\"query\":{\"redirects\":[{\"from\":\"Old Name\",\"to\":\"New Name\"}],\"pages\":[{\"pageid\":1,\"ns\":0,\"title\":\"New Name\"}]}}");
		assertEquals(Optional.of("New Name"), new WikiClient("https://wiki.test", exact).resolve("Old Name").join());

		FakeTransport viaSearch = new FakeTransport()
				.reply("titles=", "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Odd blade\",\"missing\":true}]}}")
				.reply("list=search", "{\"query\":{\"search\":[{\"ns\":0,\"title\":\"Odd Blade\"}]}}");
		assertEquals(Optional.of("Odd Blade"), new WikiClient("https://wiki.test", viaSearch).resolve("odd blade").join());

		FakeTransport none = new FakeTransport()
				.reply("titles=", "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Zzz\",\"missing\":true}]}}")
				.reply("list=search", "{\"query\":{\"search\":[]}}");
		assertEquals(Optional.empty(), new WikiClient("https://wiki.test", none).resolve("Zzz").join());
	}

	@Test
	void pageUrlKeepsReadableCharacters() {
		WikiClient c = new WikiClient("https://monumenta.wiki.gg", new FakeTransport());
		assertEquals("https://monumenta.wiki.gg/wiki/King%27s_Valley", c.pageUrl("King's Valley"));
		assertEquals("https://monumenta.wiki.gg/wiki/Exalted_Lobby/Quest", c.pageUrl("Exalted Lobby/Quest"));
		assertEquals("https://monumenta.wiki.gg/wiki/Category:Items", c.pageUrl("Category:Items"));
	}
}
