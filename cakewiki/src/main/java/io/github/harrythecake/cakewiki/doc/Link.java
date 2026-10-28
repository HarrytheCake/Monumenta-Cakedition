package io.github.harrythecake.cakewiki.doc;

/**
 * Where a link goes.
 * {@link Page}: a wiki page by title (spaces, not underscores), with an optional {@code #anchor}; an empty title means this page.
 * {@link Missing}: a wiki page that doesn't exist yet (MediaWiki's red link).
 * {@link External}: anything off the wiki, opened in the browser.
 */
public sealed interface Link {
	record Page(String title, String anchor) implements Link {}

	record Missing(String title) implements Link {}

	record External(String url) implements Link {}
}
