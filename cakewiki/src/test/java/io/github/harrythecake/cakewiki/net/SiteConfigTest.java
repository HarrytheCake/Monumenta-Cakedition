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
	void noFileMeansTheMonumentaWiki() {
		SiteConfig.Loaded l = SiteConfig.load(dir.resolve("sites.json"));
		assertEquals(List.of(SiteConfig.MONUMENTA), l.sites());
		assertTrue(l.problems().isEmpty());
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
		assertEquals(new SiteConfig.Entry("guide", "vuepress", "guide", "https://guide.test", "/", "", "", "#markdown-content"), g);
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
}
