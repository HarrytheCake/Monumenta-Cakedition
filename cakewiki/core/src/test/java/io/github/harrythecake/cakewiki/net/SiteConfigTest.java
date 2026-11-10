package io.github.harrythecake.cakewiki.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SiteConfigTest {
	@TempDir
	Path dir;

	@Test
	void noFileWritesTheDefaultOnceAndUsesIt() throws Exception {
		Path file = dir.resolve("cakewiki").resolve("sites.json");
		SiteConfig.Loaded l = SiteConfig.load(file);
		assertEquals(List.of(), l.problems());
		assertEquals(List.of("monumenta", "wynn"), l.sites().stream().map(SiteConfig.Entry::id).toList());
		assertEquals(SiteConfig.MONUMENTA, l.sites().get(0));
		assertEquals(List.of("play.wynncraft.com"), l.sites().get(1).servers());
		assertEquals(SiteConfig.defaultText(), java.nio.file.Files.readString(file), "written as in the jar, comments and all");

		java.nio.file.Files.writeString(file, "{\"sites\": [{\"id\": \"mine\", \"url\": \"https://mine.test\"}]}");
		assertEquals(List.of("mine"), SiteConfig.load(file).sites().stream().map(SiteConfig.Entry::id).toList(),
				"an existing file is read, never replaced");
	}

	@Test
	void unwritableFolderStillGivesTheDefault() throws Exception {
		Path blocker = dir.resolve("not-a-folder");
		java.nio.file.Files.writeString(blocker, "x"); // a file where the config folder should be
		SiteConfig.Loaded l = SiteConfig.load(blocker.resolve("sites.json"));
		assertEquals(2, l.sites().size());
		assertEquals(1, l.problems().size(), l.problems().toString());
	}

	@Test
	void readsSitesWithCommentsAndDefaults() {
		SiteConfig.Loaded l = SiteConfig.parse("""
				{
				  // the first site is the default
				  "sites": [
				    {"id": "monumenta", "type": "mediawiki", "name": "Monumenta Wiki", "url": "https://monumenta.wiki.gg",
				     "mainPage": "Monumenta Wiki", "license": "CC BY-SA 4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/"},
				    /* a static guide */
				    {"id": "guide", "type": "VuePress", "url": "https://guide.test/"}
				  ]
				}
				""");
		assertEquals(List.of(), l.problems());
		assertEquals(SiteConfig.MONUMENTA, l.sites().get(0));
		SiteConfig.Entry g = l.sites().get(1);
		assertEquals(new SiteConfig.Entry("guide", "vuepress", "guide", "https://guide.test", "/", "", "", "#markdown-content", List.of()), g);
	}

	@Test
	void badEntriesAreSkippedWithAReason() {
		SiteConfig.Loaded l = SiteConfig.parse("""
				{"sites": [
				  {"id": "Bad Id", "url": "https://a.test"},
				  {"id": "a", "type": "gopher", "url": "https://a.test"},
				  {"id": "b", "url": "ftp://b.test"},
				  {"id": "c", "url": "https://c.test"},
				  {"id": "c", "url": "https://c2.test"}
				]}
				""");
		assertEquals(List.of("c"), l.sites().stream().map(SiteConfig.Entry::id).toList());
		assertEquals("Main Page", l.sites().get(0).mainPage(), "a MediaWiki's usual main page");
		assertEquals(4, l.problems().size(), l.problems().toString());
	}

	@Test
	void unusableFileFallsBackToTheMonumentaWiki() {
		SiteConfig.Loaded l = SiteConfig.parse("{ not a site list");
		assertEquals(List.of(SiteConfig.MONUMENTA), l.sites());
		assertFalse(l.problems().isEmpty());
	}

	@Test
	void serversAreReadAsHosts() {
		SiteConfig.Loaded l = SiteConfig.parse("""
				{"sites": [
				  {"id": "monumenta", "url": "https://monumenta.wiki.gg"},
				  {"id": "wynn", "url": "https://wynn.test", "servers": ["Play.Wynncraft.com:25565", "*.wynn.example."]},
				  {"id": "one", "url": "https://one.test", "servers": "one.example"},
				  {"id": "untied", "url": "https://untied.test", "servers": []},
				  {"id": "mine", "url": "https://monumenta.wiki.gg", "servers": []},
				  {"id": "bad", "url": "https://bad.test", "servers": ["  "]}
				]}
				""");
		assertEquals(List.of("playmonumenta.com"), l.sites().get(0).servers(), "the Monumenta Wiki defaults to Monumenta");
		assertEquals(List.of("play.wynncraft.com", "wynn.example"), l.sites().get(1).servers());
		assertEquals(List.of("one.example"), l.sites().get(2).servers(), "one string is a list of one");
		assertEquals(List.of(), l.sites().get(3).servers());
		assertEquals(List.of(), l.sites().get(4).servers(), "an explicit empty list unties the Monumenta Wiki");
		assertEquals(5, l.sites().size());
		assertEquals(1, l.problems().size(), l.problems().toString());
		assertEquals(List.of("playmonumenta.com"), SiteConfig.MONUMENTA.servers());
	}

	@Test
	void hostDropsPortCaseAndTrailingDot() {
		assertEquals("server.playmonumenta.com", SiteConfig.host(" Server.PlayMonumenta.com:25565 "));
		assertEquals("play.wynncraft.com", SiteConfig.host("play.wynncraft.com."));
		assertEquals("::1", SiteConfig.host("[::1]:25565"));
		assertEquals("fe80::1", SiteConfig.host("fe80::1"));
		assertEquals("192.168.1.5", SiteConfig.host("192.168.1.5:25565"));
		assertNull(SiteConfig.host(""));
		assertNull(SiteConfig.host(null));
	}

	@Test
	void matchesTheHostAndItsSubdomainsOnly() {
		SiteConfig.Entry m = SiteConfig.MONUMENTA;
		assertTrue(m.matches("playmonumenta.com"));
		assertTrue(m.matches("server.playmonumenta.com"));
		assertTrue(m.matches("monumenta-12.playmonumenta.com"));
		assertFalse(m.matches("notplaymonumenta.com"));
		assertFalse(m.matches("playmonumenta.com.evil.test"));
		assertFalse(m.matches(null));
	}

	@Test
	void offeredSites() {
		SiteConfig.Entry monumenta = SiteConfig.MONUMENTA;
		SiteConfig.Entry wynn = entry("wynn", "play.wynncraft.com");
		SiteConfig.Entry guide = entry("guide");
		List<SiteConfig.Entry> all = List.of(monumenta, wynn, guide);
		assertEquals(List.of(monumenta, guide), SiteConfig.offered(all, "server.playmonumenta.com"), "tied + untied");
		assertEquals(List.of(wynn, guide), SiteConfig.offered(all, "play.wynncraft.com"));
		assertEquals(all, SiteConfig.offered(all, null), "not on a server: every site");
		assertEquals(all, SiteConfig.offered(all, "other.example"), "no site tied to this server: every site");
		assertEquals(List.of(wynn), SiteConfig.offered(List.of(monumenta, wynn), "play.wynncraft.com"));
	}

	private static SiteConfig.Entry entry(String id, String... servers) {
		return new SiteConfig.Entry(id, SiteConfig.MEDIAWIKI, id, "https://" + id + ".test", "Main Page", "", "", "",
				List.of(servers));
	}
}
