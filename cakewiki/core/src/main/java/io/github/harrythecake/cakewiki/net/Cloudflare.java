package io.github.harrythecake.cakewiki.net;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tells Cloudflare's "verify you are human" answer apart from other errors. wiki.gg is behind Cloudflare, which answers
 * connections it distrusts (VPN exits, game accelerators, some regions) with a check only a browser can pass; CakeWiki
 * then says so instead of a bare "HTTP 403" (and offers the browser, or MCEF's in-game browser). Pure Java.
 */
public final class Cloudflare {
	private Cloudflare() {
	}

	/**
	 * True when this is Cloudflare's check: status 403/429/503 with Cloudflare's {@code cf-mitigated} header, or from a
	 * Cloudflare server with its challenge page in the body. Header names are case-insensitive.
	 */
	public static boolean isChallenge(int status, Map<String, List<String>> headers, byte[] body) {
		if (status != 403 && status != 429 && status != 503) {
			return false;
		}
		if (header(headers, "cf-mitigated") != null) {
			return true;
		}
		String server = header(headers, "server");
		if (server == null || !server.toLowerCase(Locale.ROOT).contains("cloudflare") || body == null) {
			return false;
		}
		String text = new String(body, 0, Math.min(body.length, 64 * 1024), StandardCharsets.UTF_8);
		return text.contains("challenge-platform") || text.contains("cf-chl") || text.contains("Just a moment")
				|| text.contains("Attention Required");
	}

	/** Cloudflare's reference for this answer ({@code cf-ray}), or "" (players can quote it in a report). */
	public static String ray(Map<String, List<String>> headers) {
		String r = header(headers, "cf-ray");
		return r == null ? "" : r;
	}

	private static String header(Map<String, List<String>> headers, String name) {
		if (headers == null) {
			return null;
		}
		for (Map.Entry<String, List<String>> e : headers.entrySet()) {
			if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
				return e.getValue().get(0);
			}
		}
		return null;
	}
}
