package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.Block;
import io.github.harrythecake.cakewiki.doc.Document;
import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.net.WikiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in: talks to the real monumenta.wiki.gg. Run with {@code ./gradlew test -Dcakewiki.liveTest=true}.
 * Converts a few known pages plus random ones and checks the result is sane; prints a summary per page.
 */
@EnabledIfSystemProperty(named = "cakewiki.liveTest", matches = "true")
class LiveWikiTest {
	private final WikiClient client = new WikiClient(WikiClient.MONUMENTA_WIKI, WikiClient.http("test"));
	private final HtmlConverter converter = new HtmlConverter(WikiClient.MONUMENTA_WIKI);

	@Test
	void convertsRealPages() throws Exception {
		List<String> titles = new ArrayList<>(List.of("Monumenta Wiki", "Moonscarred Clockblade", "Celsian Isles",
				"Turtle Set", "True North", "Changelogs"));
		titles.addAll(client.search("boots", 3).join());
		for (String title : titles) {
			long start = System.nanoTime();
			WikiClient.Page page = client.parse(title).join();
			long fetched = System.nanoTime();
			Document doc = converter.convert(page.displayTitle(), page.html());
			long converted = System.nanoTime();
			assertFalse(doc.blocks().isEmpty(), title + ": no blocks");
			System.out.printf("%-28s %8d B html, %5d blocks %s; fetch %d ms, convert %d ms%n", title, page.html().length(),
					doc.blocks().size(), census(doc.blocks()), (fetched - start) / 1_000_000, (converted - fetched) / 1_000_000);
			Thread.sleep(300); // be gentle with the wiki
		}
		Document item = converter.convert("x", client.parse("Moonscarred Clockblade").join().html());
		assertInstanceOf(Block.Infobox.class, item.blocks().get(0), "item pages start with their infobox");
		Block.Infobox box = (Block.Infobox) item.blocks().get(0);
		assertTrue(box.images().size() >= 1 && !box.items().isEmpty());
	}

	/** {@code -Dcakewiki.dump=<title>} prints that page as an indented outline (for checking conversions by eye). */
	@Test
	@EnabledIfSystemProperty(named = "cakewiki.dump", matches = ".+")
	void dumpPage() {
		WikiClient.Page page = client.parse(System.getProperty("cakewiki.dump")).join();
		System.out.println(DocumentDump.dump(converter.convert(page.displayTitle(), page.html())));
	}

	private static String census(List<Block> blocks) {
		java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		blocks.forEach(b -> counts.merge(b.getClass().getSimpleName(), 1, Integer::sum));
		return counts.toString();
	}
}
