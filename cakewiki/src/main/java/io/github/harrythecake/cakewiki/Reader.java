package io.github.harrythecake.cakewiki;

import io.github.harrythecake.cakewiki.doc.HtmlConverter;
import io.github.harrythecake.cakewiki.image.WikiImages;
import io.github.harrythecake.cakewiki.net.PageCache;
import io.github.harrythecake.cakewiki.net.Pages;
import io.github.harrythecake.cakewiki.net.WikiClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Everything the reader keeps between openings: the wiki client, page cache, pictures and reading history.
 * One per game session; the screen is created fresh each time it opens.
 */
public final class Reader {
	public static final String MAIN_PAGE = "Monumenta Wiki";
	public static final String LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/";

	/** A page in the history and how far down it was read. */
	public record Visit(String title, int scroll) {}

	public final WikiClient client;
	public final Pages pages;
	public final WikiImages images;
	public final HtmlConverter converter;
	public final Bookmarks bookmarks;

	final Deque<Visit> back = new ArrayDeque<>();
	final Deque<Visit> forward = new ArrayDeque<>();
	/** The page shown when the reader last closed, or null before the first page. */
	Visit last;

	/** {@code cacheDir}: pages and pictures (can be deleted any time); {@code configDir}: the player's bookmarks. */
	Reader(String modVersion, Path cacheDir, Path configDir) {
		this.bookmarks = new Bookmarks(configDir.resolve("bookmarks.json"));
		WikiClient.Transport http = WikiClient.http(modVersion);
		this.client = new WikiClient(WikiClient.MONUMENTA_WIKI, http);
		this.pages = new Pages(client, new PageCache(cacheDir.resolve("pages")), Clock.systemUTC(), Duration.ofHours(24),
				e -> CakeWiki.LOG.warn("Couldn't save a wiki page to the cache: {}", e.toString()));
		this.images = new WikiImages(http, cacheDir.resolve("images"));
		this.converter = new HtmlConverter(client.base());
	}
}
