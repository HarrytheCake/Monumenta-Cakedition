package io.github.harrythecake.cakewiki.net;

import io.github.harrythecake.cakewiki.net.WikiClient.Page;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Where the reader gets pages of one site: the disk cache while it's fresh, the site otherwise.
 * When the wiki can't be reached, an old cached copy is still shown, marked {@code stale}.
 */
public final class Pages {
	/** {@code stale}: the wiki couldn't be reached and this is an older saved copy. */
	public record Loaded(Page page, boolean stale) {}

	private final Site site;
	private final PageCache cache;
	private final Clock clock;
	private final Duration maxAge;
	private final Consumer<Throwable> onCacheError;

	public Pages(Site site, PageCache cache, Clock clock, Duration maxAge, Consumer<Throwable> onCacheError) {
		this.site = site;
		this.cache = cache;
		this.clock = clock;
		this.maxAge = maxAge;
		this.onCacheError = onCacheError;
	}

	/** After {@link Site#ready()}, so a page converted from the cache links the way a fetched one does. */
	public CompletableFuture<Loaded> load(String title) {
		return site.ready().thenCompose(v -> loadNow(title));
	}

	private CompletableFuture<Loaded> loadNow(String title) {
		Optional<PageCache.Entry> cached = cache.get(title);
		if (cached.isPresent() && cached.get().fetched().plus(maxAge).isAfter(clock.instant())) {
			return CompletableFuture.completedFuture(new Loaded(cached.get().page(), false));
		}
		return site.parse(title).handle((page, error) -> {
			if (error == null) {
				try {
					cache.put(title, page, clock.instant());
				} catch (IOException e) {
					onCacheError.accept(e);
				}
				return new Loaded(page, false);
			}
			Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
			// a page that doesn't exist (any more) is an answer, not an outage: don't fall back to the old copy
			boolean missing = cause instanceof WikiException w && w.code().equals("missingtitle");
			if (cached.isPresent() && !missing) return new Loaded(cached.get().page(), true);
			throw cause instanceof RuntimeException r ? r : new CompletionException(cause);
		});
	}

	/** Always asks the wiki (the reader's Refresh button). */
	public CompletableFuture<Loaded> refresh(String title) {
		return site.parse(title).thenApply(page -> {
			try {
				cache.put(title, page, clock.instant());
			} catch (IOException e) {
				onCacheError.accept(e);
			}
			return new Loaded(page, false);
		});
	}
}
