package io.github.harrythecake.cakewiki.net;

import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.net.WikiClient.Page;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One site the reader can show: a MediaWiki ({@link WikiClient}) or a static VuePress site ({@link VuePressSite}).
 * Pages are named by a title, which is the key for the cache, history and bookmarks. Nothing here touches the game.
 */
public interface Site {
	/** What the site list ({@link SiteConfig}) says about the site. */
	SiteConfig.Entry info();

	/** The site's origin without a trailing slash, e.g. {@code https://monumenta.wiki.gg}. */
	default String base() {
		return info().url();
	}

	/** Anything the site needs before pages can be named and linked (a title index); done by default. */
	default CompletableFuture<Void> ready() {
		return CompletableFuture.completedFuture(null);
	}

	/** One page, its HTML ready for {@code HtmlConverter}; a page that doesn't exist fails with {@code missingtitle}. */
	CompletableFuture<Page> parse(String title);

	/** Page titles for what the player is typing, best first. */
	CompletableFuture<List<String>> suggest(String prefix, int limit);

	/** The page for a name (an item's, a search): exact title first, else the best match, else empty. */
	CompletableFuture<Optional<String>> resolve(String name);

	/** The page's address in a browser, for "open in browser" and attribution. */
	String pageUrl(String title);

	/** A link to an absolute URL on this site as a page link, or null to keep it external. */
	Link link(String url);

	/** The contents shown beside {@code title} (the site's sidebar for that part of it); empty when the site has none. */
	default List<NavItem> navigation(String title) {
		return List.of();
	}

	/** Pages the reader can't show (MediaWiki's {@code Special:} and category lists): opened in the browser instead. */
	default boolean browserOnly(String title) {
		return false;
	}
}
