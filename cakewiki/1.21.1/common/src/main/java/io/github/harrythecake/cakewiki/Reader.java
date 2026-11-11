package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.image.WikiImages;
import io.github.harrythecake.cakewiki.net.HostRouter;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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

	/** In the order of the site list, every site; {@link #offered()} is the part offered on the current server. */
	public final List<Source> sources;
	/** The host of the server the player is on (no port, lower case), or null when not on a server. */
	private final Supplier<String> server;
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

	/** Every request goes through it: direct, or through MCEF's browser for a host whose check the player passed. */
	final HostRouter router;

	final Deque<Visit> back = new ArrayDeque<>();
	final Deque<Visit> forward = new ArrayDeque<>();
	/** The page shown when the reader last closed, or null before the first page. */
	Visit last;
	/** The last page shown on each site (by site id), so a site comes back where it was left after a server change. */
	private final Map<String, Visit> lastBySite = new HashMap<>();

	/** {@code cacheDir}: pages and pictures (can be deleted any time); {@code configDir}: the site list and bookmarks. */
	Reader(String modVersion, Path cacheDir, Path configDir, Supplier<String> server) {
		this.server = server;
		this.bookmarks = new Bookmarks(configDir.resolve("bookmarks.json"));
		WikiClient.Transport direct = WikiClient.http(modVersion);
		String forced = System.getProperty("cakewiki.forceBlocked", "");
		if (!forced.isBlank()) {
			// development only: answer as Cloudflare's check would for this host (tests the blocked screen and MCEF)
			WikiClient.Transport real = direct;
			direct = uri -> forced.equalsIgnoreCase(uri.getHost())
					? java.util.concurrent.CompletableFuture.failedFuture(io.github.harrythecake.cakewiki.net.WikiException.blocked(403, uri.getHost(), "dev"))
					: real.get(uri);
			CakeWiki.LOG.warn("CakeWiki: -Dcakewiki.forceBlocked={}: that host answers as Cloudflare's check (development)", forced);
		}
		this.router = new HostRouter(direct);
		WikiClient.Transport http = router;
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

	/**
	 * The sites offered on the server the player is on now (sites.json {@code servers}): when some site is tied to it,
	 * those plus the untied ones; otherwise all. The ⇄ button, {@code /wiki} suggestions, the first opening and item
	 * lookups use these; a typed {@code /wiki <id>:<page>} still reaches any site.
	 */
	public List<Source> offered() {
		List<SiteConfig.Entry> entries = SiteConfig.offered(sources.stream().map(Source::info).toList(), server.get());
		return sources.stream().filter(s -> entries.contains(s.info())).toList();
	}

	/** The default site here: the first offered one (first opening, item lookup). */
	public Source home() {
		return offered().get(0);
	}

	/** The site after the current one among the offered ones (the bar's site button). */
	Source next() {
		List<Source> offered = offered();
		return offered.get((offered.indexOf(current) + 1) % offered.size()); // a site not offered here: the first
	}

	/** Records the page shown now (the reader closing or a page loading). */
	void remember(Visit v) {
		last = v;
		lastBySite.put(v.site(), v);
	}

	/**
	 * Where opening the reader goes back to: the last page when its site is offered here, else the last page of the
	 * default site here, else null (its main page).
	 */
	Visit resume() {
		List<Source> offered = offered();
		if (last != null && offered.contains(source(last.site()))) return last;
		return lastBySite.get(offered.get(0).id());
	}
}
