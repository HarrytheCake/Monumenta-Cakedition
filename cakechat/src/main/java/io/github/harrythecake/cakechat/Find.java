package io.github.harrythecake.cakechat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * What the find bar looks for: plain text or a regex, with or without matching case. Plain Java, so tests can use it.
 * While a search is on, the windows show only the messages it finds, with the matches highlighted.
 */
public final class Find {
	/** A found part of a message's text: [start, end) in {@code Text.getString()}. */
	public record Range(int start, int end) {
	}

	private String query = "";
	private boolean regex;
	private boolean matchCase;
	@Nullable
	private Pattern pattern;
	private boolean invalid;

	public Find(boolean regex, boolean matchCase) {
		this.regex = regex;
		this.matchCase = matchCase;
	}

	/** Sets the search; true when it changed. */
	public boolean set(String query, boolean regex, boolean matchCase) {
		if (query.equals(this.query) && regex == this.regex && matchCase == this.matchCase) {
			return false;
		}
		this.query = query;
		this.regex = regex;
		this.matchCase = matchCase;
		compile();
		return true;
	}

	private void compile() {
		pattern = null;
		invalid = false;
		if (query.isEmpty()) {
			return;
		}
		int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		try {
			pattern = Pattern.compile(regex ? query : Pattern.quote(query), flags);
		} catch (PatternSyntaxException e) {
			invalid = true;
		}
	}

	public String query() {
		return query;
	}

	public boolean regex() {
		return regex;
	}

	public boolean matchCase() {
		return matchCase;
	}

	/** A regex that doesn't compile: nothing is filtered, the bar shows the error. */
	public boolean invalid() {
		return invalid;
	}

	/** Whether there is something to look for (not empty, valid). */
	public boolean active() {
		return pattern != null;
	}

	public boolean matches(String text) {
		return pattern != null && pattern.matcher(text).find();
	}

	/** Every non-empty match in the text, in order. */
	public List<Range> ranges(String text) {
		List<Range> out = new ArrayList<>();
		if (pattern == null) {
			return out;
		}
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			if (m.end() > m.start()) {
				out.add(new Range(m.start(), m.end()));
			}
		}
		return out;
	}
}
