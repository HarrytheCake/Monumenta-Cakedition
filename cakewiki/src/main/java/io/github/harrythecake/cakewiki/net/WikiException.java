package io.github.harrythecake.cakewiki.net;

/** An error the wiki reported ({@code code} is MediaWiki's, e.g. {@code missingtitle}), or {@code http} for a bad status. */
public final class WikiException extends RuntimeException {
	private final String code;

	public WikiException(String code, String info) {
		super(code + (info.isEmpty() ? "" : ": " + info));
		this.code = code;
	}

	public String code() {
		return code;
	}
}
