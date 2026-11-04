package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.image.WikiImages;
import io.github.harrythecake.cakewiki.net.PageCache;
import io.github.harrythecake.cakewiki.net.Pages;
import io.github.harrythecake.cakewiki.net.Site;
import io.github.harrythecake.cakewiki.net.SiteConfig;
import io.github.harrythecake.cakewiki.net.VuePressSite;
import io.github.harrythecake.cakewiki.net.WikiClient;
import io.github.harrythecake.cakewiki.translate.CakeTextBridge;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Everything the reader keeps between openings: the sites (from {@code config/cakewiki/sites.json}), their page caches,
 * pictures and reading history. One per game session; the screen is created fresh each time it opens.
 */
public final class Reader {
	/** One site with its page cache and HTML conversion. */
	public record Source(Site site, Pages pages, HtmlConverter converter) {
		public String id() {
			return site.info().id();
		}

		public SiteConfig.Entry info() {
			return site.info();
		}
	}

	/** A page in the history (on which site) and how far down it was read. */
	public record Visit(String site, String title, int scroll) {}

	/** In the order of the site list; the first is the default (first opening, item lookup). */
	public final List<Source> sources;
	public final WikiImages images;
	public final Bookmarks bookmarks;
	/** CakeText Translator, when installed (0.3.0+), else null. */
	public final CakeTextBridge cakeText = CakeTextBridge.find();
	/** The reader's translate button: pages translated (when CakeText is there and on for wiki pages), this session. */
	boolean translate = true;
	/** The contents panel: shown (when the page's site has contents), its last tree with open groups, and its scroll. */
	boolean contentsOpen = true;
	io.github.harrythecake.cakewiki.layout.Contents contents;
	int contentsScroll;
	/** The site the reader is showing. */
	private Source current;

	final Deque<Visit> back = new ArrayDeque<>();
	final Deque<Visit> forward = new ArrayDeque<>();
	/** The page shown when the reader last closed, or null before the first page. */
	Visit last;

	/** {@code cacheDir}: pages and pictures (can be deleted any time); {@code configDir}: the site list and bookmarks. */
	Reader(String modVersion, Path cacheDir, Path configDir) {
		this.bookmarks = new Bookmarks(configDir.resolve("bookmarks.json"));
		WikiClient.Transport http = WikiClient.http(modVersion);
		SiteConfig.Loaded config = SiteConfig.load(configDir.resolve("sites.json"));
		config.problems().forEach(p -> CakeWiki.LOG.warn("config/cakewiki/sites.json: {}", p));
		List<Source> list = new ArrayList<>();
		for (SiteConfig.Entry e : config.sites()) {
			// the Monumenta Wiki keeps the cache folder it had before there were several sites
			Path dir = e.id().equals(SiteConfig.MONUMENTA.id()) ? cacheDir : cacheDir.resolve("sites").resolve(e.id());
			Site site = e.type().equals(SiteConfig.VUEPRESS)
					? new VuePressSite(e, http, dir.resolve("index.json"), Clock.systemUTC(), Duration.ofHours(24),
							why -> CakeWiki.LOG.warn("Couldn't list the pages of {} ({}): {}", e.name(), e.url(), why))
					: new WikiClient(e, http);
			Pages pages = new Pages(site, new PageCache(dir.resolve("pages")), Clock.systemUTC(), Duration.ofHours(24),
					err -> CakeWiki.LOG.warn("Couldn't save a wiki page to the cache: {}", err.toString()));
			list.add(new Source(site, pages, new HtmlConverter(site.base(), site::link)));
		}
		this.sources = List.copyOf(list);
		this.current = sources.get(0);
		this.images = new WikiImages(http, cacheDir.resolve("images"));
		if (sources.size() > 1) CakeWiki.LOG.info("CakeWiki sites: {}", sources.stream().map(Source::id).toList());
	}

	public Source source() {
		return current;
	}

	/** The site with this id, or null when the site list no longer has it. */
	public Source source(String id) {
		for (Source s : sources) if (s.id().equals(id)) return s;
		return null;
	}

	/** Shows {@code s} from now on. */
	void use(Source s) {
		current = s;
	}

	/** The site after the current one (the bar's site button). */
	Source next() {
		return sources.get((sources.indexOf(current) + 1) % sources.size());
	}
}
