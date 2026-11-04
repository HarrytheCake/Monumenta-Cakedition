package io.github.harrythecake.cakewiki.net;

/**
 * An error the wiki reported ({@code code} is MediaWiki's, e.g. {@code missingtitle}), or {@code http} for a bad status
 * ({@link #status()}; 0 for every other error).
 */
public final class WikiException extends RuntimeException {
	private final String code;
	private final int status;

	public WikiException(String code, String info) {
		this(code, info, 0);
	}

	private WikiException(String code, String info, int status) {
		super(code + (info.isEmpty() ? "" : ": " + info));
		this.code = code;
		this.status = status;
	}

	/** The server answered with HTTP {@code status} (not 200). */
	public static WikiException http(int status, String host) {
		return new WikiException("http", "HTTP " + status + " from " + host, status);
	}

	public String code() {
		return code;
	}

	public int status() {
		return status;
	}
}
