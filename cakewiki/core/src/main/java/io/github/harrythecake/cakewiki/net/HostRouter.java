package io.github.harrythecake.cakewiki.net;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The transport every site and the pictures use: direct (Java's HTTP client) unless a host has another way in. A
 * host whose Cloudflare check the player passed in MCEF's in-game browser gets that browser as its transport
 * ({@link #route}); when that browser is blocked again (the pass ran out), the route is dropped and the player is
 * asked again. Thread-safe.
 */
public final class HostRouter implements WikiClient.Transport {
	private final WikiClient.Transport direct;
	private final Map<String, WikiClient.Transport> routes = new ConcurrentHashMap<>();

	public HostRouter(WikiClient.Transport direct) {
		this.direct = direct;
	}

	/** Requests to {@code host} go through {@code transport} from now on. */
	public void route(String host, WikiClient.Transport transport) {
		routes.put(key(host), transport);
	}

	/** {@code host} goes direct again. */
	public void unroute(String host) {
		routes.remove(key(host));
	}

	public boolean routed(String host) {
		return host != null && routes.containsKey(key(host));
	}

	@Override
	public CompletableFuture<byte[]> get(URI uri) {
		String host = uri.getHost();
		WikiClient.Transport t = host == null ? null : routes.get(key(host));
		if (t == null) {
			return direct.get(uri);
		}
		return t.get(uri).whenComplete((body, error) -> {
			Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
			if (cause instanceof WikiException w && w.blocked()) {
				routes.remove(key(host), t); // the in-game browser's pass ran out: ask again
			}
		});
	}

	private static String key(String host) {
		return host.toLowerCase(Locale.ROOT);
	}
}
