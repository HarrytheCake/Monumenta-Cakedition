package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.net.SiteConfig;
import io.github.harrythecake.cakewiki.net.VuePressSite;
import io.github.harrythecake.cakewiki.net.WikiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in: talks to a real VuePress site given on the command line, e.g.
 * {@code ./gradlew test --tests '*LiveVuePressTest' -Dcakewiki.liveTest=true -Dcakewiki.vuepress=https://example.org}.
 * Lists its pages, converts {@code cakewiki.pages} of them (default 8) from number {@code cakewiki.from} (default 0)
 * and prints a summary per page;
 * with {@code -Dcakewiki.dump=<title or path>}, prints that page as an outline instead; with
 * {@code -Dcakewiki.nav=<title or path>}, the contents (sidebar) shown beside it.
 */
@EnabledIfSystemProperty(named = "cakewiki.vuepress", matches = "https?://.+")
class LiveVuePressTest {
	@TempDir
	Path dir;

	@Test
	void convertsRealPages() throws Exception {
		SiteConfig.Entry entry = SiteConfig.parse("{\"sites\":[{\"id\":\"live\",\"type\":\"vuepress\",\"url\":\""
				+ System.getProperty("cakewiki.vuepress") + "\"}]}").sites().get(0);
		VuePressSite site = new VuePressSite(entry, WikiClient.http("test"), dir.resolve("index.json"), Clock.systemUTC(),
				Duration.ofHours(1), why -> System.out.println("index: " + why));
		site.ready().join();
		List<VuePressSite.Route> routes = site.routes();
		System.out.println(routes.size() + " pages; first: " + routes.subList(0, Math.min(5, routes.size())));
		assertFalse(routes.isEmpty(), "no pages listed");
		HtmlConverter converter = new HtmlConverter(site.base(), site::link);

		String nav = System.getProperty("cakewiki.nav");
		if (nav != null) {
			List<io.github.harrythecake.cakewiki.net.NavItem> tree = site.navigation(nav);
			printTree(tree, 0);
			assertFalse(tree.isEmpty(), "no contents for " + nav);
			return;
		}
		String dump = System.getProperty("cakewiki.dump");
		if (dump != null) {
			WikiClient.Page page = site.parse(dump).join();
			System.out.println(DocumentDump.dump(converter.convert(page.displayTitle(), page.html())));
			return;
		}
		int from = Math.min(Integer.getInteger("cakewiki.from", 0), routes.size()), n = Integer.getInteger("cakewiki.pages", 8);
		for (VuePressSite.Route r : routes.subList(from, Math.min(from + n, routes.size()))) {
			long start = System.nanoTime();
			WikiClient.Page page = site.parse(r.key()).join();
			long fetched = System.nanoTime();
			Document doc = converter.convert(page.displayTitle(), page.html());
			long converted = System.nanoTime();
			assertEquals(r.key(), page.title(), "a page is named by its index key");
			assertFalse(doc.blocks().isEmpty(), r.key() + ": no blocks");
			System.out.printf("%-40s %-28s %7d B, %4d blocks; fetch %d ms, convert %d ms%n", r.path(), page.displayTitle(),
					page.html().length(), doc.blocks().size(), (fetched - start) / 1_000_000, (converted - fetched) / 1_000_000);
			Thread.sleep(300); // be gentle with the site
		}
	}

	private static void printTree(List<io.github.harrythecake.cakewiki.net.NavItem> items, int depth) {
		for (var it : items) {
			System.out.println("  ".repeat(depth) + it.text() + (it.key() != null ? "  -> " + it.key() : "")
					+ (it.collapsible() ? "  [+]" : "") + (it.group() ? "  (" + it.children().size() + ")" : ""));
			printTree(it.children(), depth + 1);
		}
	}
}
