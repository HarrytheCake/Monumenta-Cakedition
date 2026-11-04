package io.github.harrythecake.cakewiki.net;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.net.PagesTest.TestClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/** Hand-written pages in the shapes a VuePress 2 (Theme Hope) site serves; made-up content. */
class VuePressSiteTest {
	private static final String BASE = "https://guide.test";
	private static final SiteConfig.Entry ENTRY = new SiteConfig.Entry("guide", SiteConfig.VUEPRESS, "Test Guide", BASE, "/",
			"© Someone", "", "#markdown-content");

	@TempDir
	Path dir;

	/** Answers by full URL; anything else is a 404, like the static host. */
	static final class UrlTransport implements WikiClient.Transport {
		final Map<String, String> pages = new HashMap<>();
		final List<String> requests = new ArrayList<>();
		boolean down;

		UrlTransport page(String url, String body) {
			pages.put(url, body);
			return this;
		}

		@Override
		public CompletableFuture<byte[]> get(URI uri) {
			requests.add(uri.toString());
			if (down) return CompletableFuture.failedFuture(new java.io.IOException("offline"));
			String body = pages.get(uri.toString());
			return body != null ? CompletableFuture.completedFuture(body.getBytes(StandardCharsets.UTF_8))
					: CompletableFuture.failedFuture(WikiException.http(404, uri.getHost()));
		}
	}

	private static final String HOME = "<!doctype html><html><head><title>Home | Test Guide</title>"
			+ "<link rel=\"modulepreload\" href=\"/assets/app-AbC123.js\"></head><body><div id=\"app\"></div></body></html>";

	private static final String APP = "const r=Object.fromEntries([[\"/\",{loader:()=>D(()=>import(\"./index.html-x1.js\"),[]),meta:{title:\"Home\",icon:\"house\"}}],"
			+ "[\"/guide/\",{loader:()=>D(()=>import(\"./index.html-x2.js\"),[]),meta:{title:\"Game Guide\",icon:\"bulb\"}}],"
			+ "[\"/guide/classes.html\",{loader:()=>D(()=>import(\"./classes.html-x3.js\"),__vite__mapDeps([0,1])),meta:{title:\"Classes \\u0026 Skills\",icon:\"hat\"}}],"
			+ "[\"/quests/first.html\",{loader:()=>D(()=>import(\"./first.html-x4.js\"),[]),meta:{title:\"\\\"First\\\" Quest\"}}],"
			+ "[\"/quests/again.html\",{loader:()=>D(()=>import(\"./again.html-x5.js\"),[]),meta:{title:\"Game Guide\"}}],"
			+ "[\"/404.html\",{loader:()=>D(()=>import(\"./404.html-x6.js\"),[]),meta:{title:\"\"}}]]);";

	private static final String CLASSES = "<!doctype html><html><head><title>Classes &amp; Skills | Test Guide</title></head><body>"
			+ "<nav class=\"vp-sidebar\"><a class=\"vp-sidebar-link\" href=\"/guide/\">Game Guide</a></nav>"
			+ "<main id=\"main-content\" class=\"vp-page\"><div class=\"vp-page-title\"><h1>Classes &amp; Skills</h1></div>"
			+ "<div class=\"theme-hope-content\" id=\"markdown-content\">"
			+ "<h2 id=\"warrior\" tabindex=\"-1\"><a class=\"header-anchor\" href=\"#warrior\"><span>Warrior</span></a></h2>"
			+ "<p>See <a href=\"/quests/first.html#reward\" class=\"route-link\">the first quest</a>, <a href=\"../guide/\">the guide</a>,"
			+ " <a href=\"#warrior\">this part</a> and <a href=\"https://elsewhere.test/x\">another site</a>.</p>"
			+ "<p><img src=\"/assets/img/sword.png\" alt=\"sword\"><a href=\"/assets/img/map.jpg\">big map</a></p>"
			+ "<div class=\"hint-container tip\"><p class=\"hint-container-title\">Tip</p><p>Be careful.</p></div>"
			+ "</div><footer class=\"vp-page-meta\">Edit this page</footer></main></body></html>";

	private VuePressSite site(UrlTransport t, TestClock clock) {
		return new VuePressSite(ENTRY, t, dir.resolve("index.json"), clock, Duration.ofHours(24), why -> {});
	}

	private static UrlTransport fullSite() {
		return new UrlTransport().page(BASE + "/", HOME).page(BASE + "/assets/app-AbC123.js", APP)
				.page(BASE + "/guide/classes.html", CLASSES);
	}

	@Test
	void routeTableNamesPagesByTitleFirstComeFirstServed() {
		VuePressSite s = site(fullSite(), new TestClock());
		s.ready().join();
		List<String> keys = s.routes().stream().map(VuePressSite.Route::key).toList();
		assertEquals(List.of("Home", "Game Guide", "Classes & Skills", "\"First\" Quest", "/quests/again.html"), keys,
				"escapes undone; a title used twice names only its first page; 404 left out");
		assertEquals("/guide/classes.html", s.pathFor("classes & skills"));
		assertEquals("Game Guide", s.keyFor("/guide/index.html"));
		assertEquals("Game Guide", s.keyFor("/guide"));
		assertEquals("/nowhere.html", s.keyFor("/nowhere.html"));
	}

	@Test
	void pageIsTheContentWithAbsoluteLinksAndItsTitle() {
		VuePressSite s = site(fullSite(), new TestClock());
		WikiClient.Page page = s.parse("Classes & Skills").join();
		assertEquals("Classes & Skills", page.title());
		assertEquals("Classes & Skills", page.displayTitle());
		assertTrue(page.html().startsWith("<div class=\"theme-hope-content\" id=\"markdown-content\">"), page.html());
		assertFalse(page.html().contains("vp-sidebar") || page.html().contains("Edit this page"), "only the page text");
		assertTrue(page.html().contains("href=\"https://guide.test/guide/\""), "relative links made absolute");
		assertTrue(page.html().contains("src=\"https://guide.test/assets/img/sword.png\""));
		assertTrue(page.html().contains("href=\"#warrior\""), "same-page anchors stay as they are");
	}

	@Test
	void convertedPageLinksToTitlesOfThisSite() {
		VuePressSite s = site(fullSite(), new TestClock());
		WikiClient.Page page = s.parse("/guide/classes.html").join();
		List<Block> blocks = new HtmlConverter(s.base(), s::link).convert(page.displayTitle(), page.html()).blocks();

		Block.Heading h = (Block.Heading) blocks.get(0);
		assertEquals("warrior", h.anchor());
		assertNull(((Inline.Text) h.text().get(0)).style().link(), "the heading's link to itself is dropped");

		List<Link> links = new ArrayList<>();
		for (Block b : blocks) {
			if (b instanceof Block.Paragraph p) {
				for (Inline i : p.text()) if (i instanceof Inline.Text t && t.style().link() != null) links.add(t.style().link());
			}
		}
		assertEquals(List.of(new Link.Page("\"First\" Quest", "reward"), new Link.Page("Game Guide", null),
				new Link.Page("", "warrior"), new Link.External("https://elsewhere.test/x"),
				new Link.External("https://guide.test/assets/img/map.jpg")), links);
	}

	@Test
	void homeLayoutShowsMainWhenTheTextIsEmpty() {
		UrlTransport t = fullSite().page(BASE + "/", HOME.replace("<div id=\"app\"></div>",
				"<main id=\"main-content\" class=\"vp-page vp-project-home\"><header class=\"vp-hero-info-wrapper\">"
						+ "<img class=\"vp-hero-image\" src=\"/logo.svg\" alt><h1 class=\"vp-hero-title\">Test Guide</h1>"
						+ "<p class=\"vp-hero-actions\"><a class=\"route-link\" href=\"/guide/\">Guide</a><a class=\"route-link\" href=\"/quests/\">Quests</a></p>"
						+ "</header><div vp-content><div id=\"markdown-content\"></div></div></main>"));
		WikiClient.Page page = site(t, new TestClock()).parse("Home").join();
		assertEquals("Home", page.displayTitle(), "from <title>, without the site name");
		assertTrue(page.html().contains("Test Guide") && page.html().contains("href=\"https://guide.test/guide/\""), page.html());
		assertFalse(page.html().contains("logo.svg"), "SVG pictures can't be shown: left out");
		assertTrue(page.html().contains("</a> · <a"), "buttons get a separator");
	}

	@Test
	void encodedRoutePathsAreFetchedOnce() {
		UrlTransport t = fullSite()
				.page(BASE + "/assets/app-AbC123.js", APP.replace("]]);", "],[\"/quests/level%201.html\",{loader:()=>D(()=>import(\"./l.js\"),[]),meta:{title:\"Level One\"}}]]);"))
				.page(BASE + "/quests/level%201.html", CLASSES);
		VuePressSite s = site(t, new TestClock());
		assertEquals("Level One", s.parse("Level One").join().title(), "not encoded twice (level%25201)");
		assertEquals("Level One", s.keyFor("/quests/level 1.html"), "a link's decoded path finds it");
	}

	// a Theme Hope app script with a sidebar for /guide/ ("structure" = the folder list) and its folder lists
	private static final String APP_WITH_SIDEBAR = "const r=[[\"/\",{loader:()=>D(()=>import(\"./a.js\"),[]),meta:{title:\"Home\"}}],"
			+ "[\"/guide/\",{loader:()=>D(()=>import(\"./b.js\"),[]),meta:{title:\"Game Guide\"}}],"
			+ "[\"/guide/classes.html\",{loader:()=>D(()=>import(\"./c.js\"),[]),meta:{title:\"Classes & Skills\"}}],"
			+ "[\"/guide/basics/cave.html\",{loader:()=>D(()=>import(\"./d.js\"),[]),meta:{title:\"Caves\"}}],"
			+ "[\"/guide/basics/king's%20road.html\",{loader:()=>D(()=>import(\"./e.js\"),[]),meta:{title:\"King's Road\"}}],"
			+ "[\"/quests/\",{loader:()=>D(()=>import(\"./f.js\"),[]),meta:{title:\"Quest List\"}}],"
			+ "[\"/quests/first.html\",{loader:()=>D(()=>import(\"./g.js\"),[]),meta:{title:\"First Quest\"}}]];"
			+ "const qs={\"/guide/basics/\":[\"cave\",\"king\\'s road\"]},Tp=Symbol(\"\");"
			+ "const L1=JSON.parse('{\"author\":{\"name\":\"X\"},\"locales\":{\"/\":{\"sidebar\":{\"/guide/\":[{\"text\":\"Contents\",\"prefix\":\"\",\"children\":["
			+ "{\"text\":\"Basics\",\"prefix\":\"basics/\",\"collapsible\":true,\"children\":\"structure\"},\"classes\",\"missing\","
			+ "{\"text\":\"Quests\",\"prefix\":\"/quests/\",\"link\":\"README.md\",\"collapsible\":true,\"children\":[\"first\"]}]}]}}}}');";

	@Test
	void sidebarIsTheSitesContentsForItsPart() {
		UrlTransport t = new UrlTransport().page(BASE + "/", HOME).page(BASE + "/assets/app-AbC123.js", APP_WITH_SIDEBAR);
		VuePressSite s = site(t, new TestClock());
		s.ready().join();
		NavItem caves = new NavItem("Caves", "Caves", false, List.of());
		NavItem road = new NavItem("King's Road", "King's Road", false, List.of());
		List<NavItem> expected = List.of(new NavItem("Contents", null, false, List.of(
				new NavItem("Basics", null, true, List.of(caves, road)),
				new NavItem("Classes & Skills", "Classes & Skills", false, List.of()),
				new NavItem("Quests", "Quest List", true, List.of(new NavItem("First Quest", "First Quest", false, List.of()))))));
		assertEquals(expected, s.navigation("Classes & Skills"), "\"missing\" names no page: left out");
		assertEquals(expected, s.navigation("Caves"));
		assertEquals(List.of(), s.navigation("Home"), "no sidebar outside /guide/");
		assertEquals(List.of(), s.navigation("First Quest"));

		t.down = true; // kept with the index
		VuePressSite again = site(t, new TestClock());
		again.ready().join();
		assertEquals(expected, again.navigation("Classes & Skills"));
	}

	@Test
	void missingPagesAreMissingTitles() {
		VuePressSite s = site(fullSite(), new TestClock());
		CompletionException unknownTitle = assertThrows(CompletionException.class, () -> s.parse("No Such Page").join());
		assertEquals("missingtitle", ((WikiException) unknownTitle.getCause()).code());
		CompletionException gone = assertThrows(CompletionException.class, () -> s.parse("/quests/first.html").join());
		assertEquals("missingtitle", ((WikiException) gone.getCause()).code(), "a 404 is a missing page, not an outage");
	}

	@Test
	void suggestAndResolveMatchTitles() {
		VuePressSite s = site(fullSite(), new TestClock());
		assertEquals(List.of("Game Guide", "/quests/again.html", "Classes & Skills"), s.suggest("g", 8).join(),
				"titles starting with it, then containing it, then paths containing it");
		assertEquals(List.of("Classes & Skills"), s.suggest("skill", 8).join());
		assertEquals(List.of("/quests/again.html"), s.suggest("again", 8).join(), "paths match too");
		assertEquals(Optional.of("Classes & Skills"), s.resolve("classes & skills").join());
		assertEquals(Optional.of("Classes & Skills"), s.resolve("Classes").join(), "else the best match");
		assertEquals(Optional.of("Game Guide"), s.resolve("/guide/").join());
		assertEquals(Optional.empty(), s.resolve("zzz").join());
	}

	@Test
	void pageUrlsAreEncoded() {
		VuePressSite s = site(fullSite(), new TestClock());
		s.ready().join();
		assertEquals("https://guide.test/guide/classes.html", s.pageUrl("Classes & Skills"));
		assertEquals("https://guide.test/quests/%E4%BB%BB%E5%8A%A1%20a.html", s.pageUrl("/quests/任务 a.html"));
		assertEquals("https://guide.test/", s.pageUrl("Unknown"));
	}

	@Test
	void indexIsKeptADayAndUsedOfflineWhenOld() {
		TestClock clock = new TestClock();
		UrlTransport t = fullSite();
		site(t, clock).ready().join();
		assertTrue(Files.exists(dir.resolve("index.json")));

		t.requests.clear();
		VuePressSite again = site(t, clock);
		again.ready().join();
		assertTrue(t.requests.isEmpty(), "fresh index from disk: no request");

		clock.now = clock.now.plus(Duration.ofHours(30));
		t.down = true;
		VuePressSite offline = site(t, clock);
		offline.ready().join();
		assertFalse(t.requests.isEmpty(), "old index: asked the site first");
		assertEquals("/guide/classes.html", offline.pathFor("Classes & Skills"), "then used the old copy");
	}

	@Test
	void indexSavedByAnOlderVersionIsFetchedAgain() throws Exception {
		Files.writeString(dir.resolve("index.json"), "{\"fetched\":" + new TestClock().now.toEpochMilli()
				+ ",\"routes\":[[\"/guide/classes.html\",\"Old Name\"]]}");
		UrlTransport t = fullSite();
		VuePressSite s = site(t, new TestClock());
		s.ready().join();
		assertFalse(t.requests.isEmpty(), "no format number: fetched although fresh");
		assertEquals("Classes & Skills", s.keyFor("/guide/classes.html"));
	}

	@Test
	void withoutARouteTableTheSitemapGivesPaths() {
		UrlTransport t = new UrlTransport()
				.page(BASE + "/", "<html><head><script src=\"/js/main.js\"></script></head></html>")
				.page(BASE + "/sitemap.xml", "<urlset><url><loc>https://guide.test/</loc></url>"
						+ "<url><loc>https://guide.test/guide/classes.html</loc></url><url><loc>https://other.test/x.html</loc></url></urlset>");
		VuePressSite s = site(t, new TestClock());
		s.ready().join();
		assertEquals(List.of("/", "/guide/classes.html"), s.routes().stream().map(VuePressSite.Route::key).toList());
	}

	@Test
	void siteThatCantBeReachedStillWorksByPath() {
		UrlTransport t = fullSite();
		t.down = true;
		VuePressSite s = site(t, new TestClock());
		s.ready().join(); // doesn't fail
		assertTrue(s.routes().isEmpty());
		assertEquals("/guide/classes.html", s.pathFor("/guide/classes.html"));
	}
}
