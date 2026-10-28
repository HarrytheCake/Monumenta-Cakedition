package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * One search over every book page (in the game's language, with fallback) and every note. Each word of the query must
 * appear in the title or the text; title hits rank first. Case-insensitive; works for Chinese, which has no spaces,
 * because words are matched as substrings. Plain Java.
 */
final class Search {
	enum Kind { PAGE, NOTE }

	/** {@code book} is null for a note; {@code path} is the page path or note id. */
	record Hit(Kind kind, @Nullable String book, String path, String title, String snippet, int score) {
		Links.Target target() {
			return kind == Kind.NOTE ? new Links.Target(Links.Kind.NOTE, null, path) : Links.Target.page(book, path);
		}
	}

	private static final int SNIPPET = 70;

	private Search() {}

	static List<Hit> find(Library library, List<Notes.Note> notes, String lang, String query, int limit) {
		List<String> terms = terms(query);
		List<Hit> hits = new ArrayList<>();
		if (terms.isEmpty()) {
			return hits;
		}
		for (Book book : library.books()) {
			for (String path : book.paths()) {
				Book.Page page = book.page(path, lang);
				if (page != null) {
					Hit hit = match(Kind.PAGE, book.id, path, page.title(), page.doc().plainText(), query, terms);
					if (hit != null) {
						hits.add(hit);
					}
				}
			}
		}
		for (Notes.Note note : notes) {
			Hit hit = match(Kind.NOTE, null, note.id(), note.title(), Markdown.parse(note.body()).plainText(), query, terms);
			if (hit != null) {
				hits.add(hit);
			}
		}
		hits.sort(Comparator.comparingInt(Hit::score).reversed()
				.thenComparing(hit -> hit.title().toLowerCase(Locale.ROOT)));
		return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
	}

	static List<String> terms(String query) {
		List<String> terms = new ArrayList<>();
		for (String term : query.toLowerCase(Locale.ROOT).strip().split("\\s+")) {
			if (!term.isEmpty()) {
				terms.add(term);
			}
		}
		return terms;
	}

	private static @Nullable Hit match(Kind kind, @Nullable String book, String path, String title, String text,
			String query, List<String> terms) {
		String lowTitle = title.toLowerCase(Locale.ROOT);
		String lowText = text.toLowerCase(Locale.ROOT);
		int score = 0;
		int firstInText = -1;
		for (String term : terms) {
			boolean inTitle = lowTitle.contains(term);
			int at = lowText.indexOf(term);
			if (!inTitle && at < 0) {
				return null;
			}
			if (inTitle) {
				score += 10;
			}
			if (at >= 0) {
				score += Math.min(count(lowText, term), 5);
				if (firstInText < 0) {
					firstInText = at;
				}
			}
		}
		String whole = query.toLowerCase(Locale.ROOT).strip();
		if (lowTitle.equals(whole)) {
			score += 50;
		} else if (lowTitle.startsWith(whole)) {
			score += 20;
		}
		return new Hit(kind, book, path, title, snippet(text, firstInText), score);
	}

	private static int count(String text, String term) {
		int n = 0;
		for (int at = text.indexOf(term); at >= 0 && n < 5; at = text.indexOf(term, at + term.length())) {
			n++;
		}
		return n;
	}

	/** The text around {@code at} on its line, cut to about {@value #SNIPPET} characters. */
	static String snippet(String text, int at) {
		if (at < 0) {
			int end = text.indexOf('\n');
			String first = (end < 0 ? text : text.substring(0, end)).strip();
			return first.length() > SNIPPET ? first.substring(0, SNIPPET) + "…" : first;
		}
		int lineStart = text.lastIndexOf('\n', at) + 1;
		int lineEnd = text.indexOf('\n', at);
		String line = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd);
		int pos = at - lineStart;
		int from = Math.max(0, pos - SNIPPET / 3);
		int to = Math.min(line.length(), from + SNIPPET);
		return (from > 0 ? "…" : "") + line.substring(from, to).strip() + (to < line.length() ? "…" : "");
	}
}
