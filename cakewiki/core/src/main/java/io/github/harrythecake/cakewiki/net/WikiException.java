package io.github.harrythecake.cakewiki.net;

/**
 * An error the wiki reported ({@code code} is MediaWiki's, e.g. {@code missingtitle}), {@code http} for a bad status
 * ({@link #status()}; 0 for every other error), or {@code blocked} when Cloudflare's "verify you are human" check
 * stopped the request ({@link Cloudflare}; only a browser can pass it).
 */
public final class WikiException extends RuntimeException {
	private final String code;
	private final int status;
	private final String host;
	private final String ray;

	public WikiException(String code, String info) {
		this(code, info, 0);
	}

	private WikiException(String code, String info, int status) {
		this(code, info, status, "", "");
	}

	private WikiException(String code, String info, int status, String host, String ray) {
		super(code + (info.isEmpty() ? "" : ": " + info));
		this.code = code;
		this.status = status;
		this.host = host;
		this.ray = ray;
	}

	/** The server answered with HTTP {@code status} (not 200). */
	public static WikiException http(int status, String host) {
		return new WikiException("http", "HTTP " + status + " from " + host, status);
	}

	/** Cloudflare's check stopped the request to {@code host} ({@code ray}: Cloudflare's reference, or ""). */
	public static WikiException blocked(int status, String host, String ray) {
		return new WikiException("blocked", "HTTP " + status + " from " + host + " (Cloudflare check" + (ray.isEmpty() ? "" : ", ray " + ray) + ")",
				status, host, ray);
	}

	/** True for Cloudflare's check ({@link #blocked}). */
	public boolean blocked() {
		return code.equals("blocked");
	}

	/** The blocked host ({@link #blocked}), else "". */
	public String host() {
		return host;
	}

	/** Cloudflare's reference ({@link #blocked}), else "". */
	public String ray() {
		return ray;
	}

	public String code() {
		return code;
	}

	public int status() {
		return status;
	}
}
