package io.github.harrythecake.cakewiki.translate;

import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Inline.InlineImage;
import io.github.harrythecake.cakewiki.doc.Inline.LineBreak;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Inline.Text;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns one line of a wiki page (a list of inlines) into its translation, for {@code Layout}. Names stay in English,
 * the way players look them up in game: <b>links</b> (items, places, bosses, other pages), <b>short coloured runs</b>
 * (tiers, regions and item names are coloured; up to {@value #NAME_MAX_WORDS} words) and inline pictures stay exactly
 * as they are, wherever the translation puts them; they never reach the engine. The rest of the line is translated
 * sentence by sentence, in the style most of it had (bold or italic inside a sentence is lost in the translation).
 *
 * <p>Results are remembered per line object (the page's blocks never change) until {@link #refresh()} sees the
 * translator report news. A line not translated yet shows the original.
 */
public final class PageTranslator implements UnaryOperator<List<Inline>> {
	static final int NAME_MAX_WORDS = 4;
	/** A sentence ends at . ! ? : ; followed by a space and a capital letter, digit, quote or bracket. */
	private static final Pattern SENTENCE_END = Pattern.compile("[.!?:;] +(?=[A-Z0-9\"“(])");
	private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

	private final Translator translator;
	private final Map<List<Inline>, List<Inline>> done = new IdentityHashMap<>();
	private long seen;

	public PageTranslator(Translator translator) {
		this.translator = translator;
		this.seen = translator.changes();
	}

	/** True (and the remembered lines forgotten) when the translator has news since the last call: lay out again. */
	public boolean refresh() {
		long now = translator.changes();
		if (now == seen) return false;
		seen = now;
		done.clear();
		return true;
	}

	@Override
	public List<Inline> apply(List<Inline> line) {
		List<Inline> out = done.get(line);
		if (out == null) {
			out = translateLine(line);
			done.put(line, out);
		}
		return out;
	}

	// ---- one line -----------------------------------------------------------------------------------------

	/** A piece of a line: translatable text, or inlines kept as they are (one link, one name, one picture). */
	private record Unit(String text, Style style, List<Inline> kept) {
		boolean isKept() { return kept != null; }
	}

	/** The translated line, or {@code line} itself when nothing in it is translated (yet). */
	private List<Inline> translateLine(List<Inline> line) {
		List<Inline> out = new ArrayList<>();
		List<Inline> segment = new ArrayList<>();
		boolean changed = false;
		for (Inline in : line) {
			if (in instanceof LineBreak) {
				List<Inline> t = translateSegment(segment);
				changed |= t != segment;
				out.addAll(t);
				out.add(in);
				segment = new ArrayList<>();
			} else {
				segment.add(in);
			}
		}
		List<Inline> t = translateSegment(segment);
		changed |= t != segment;
		out.addAll(t);
		return changed ? out : line;
	}

	/** The translated segment, or {@code segment} itself when no sentence of it is translated. */
	private List<Inline> translateSegment(List<Inline> segment) {
		List<Unit> units = units(segment);
		if (units.stream().noneMatch(u -> !u.isKept() && LATIN.matcher(u.text()).find())) return segment;
		List<Inline> out = new ArrayList<>();
		boolean changed = false;
		for (List<Unit> sentence : sentences(units)) {
			List<Inline> t = translateSentence(sentence);
			if (t != null) {
				changed = true;
				out.addAll(t);
			} else {
				out.addAll(original(sentence));
			}
		}
		return changed ? out : segment;
	}

	private static List<Inline> original(List<Unit> sentence) {
		List<Inline> out = new ArrayList<>();
		for (Unit u : sentence) {
			if (u.isKept()) out.addAll(u.kept());
			else out.add(new Text(u.text(), u.style()));
		}
		return out;
	}

	/** Splits a segment into units: runs of plain text merged, each link / name / picture on its own. */
	static List<Unit> units(List<Inline> segment) {
		List<Unit> units = new ArrayList<>();
		for (Inline in : segment) {
			if (in instanceof InlineImage) {
				units.add(new Unit(null, null, List.of(in)));
			} else if (in instanceof Text t) {
				Unit last = units.isEmpty() ? null : units.get(units.size() - 1);
				if (kept(t)) {
					// a link split by a style change is still one name
					if (last != null && last.isKept() && t.style().link() != null && last.kept().get(0) instanceof Text lt
							&& Objects.equals(lt.style().link(), t.style().link())) {
						List<Inline> k = new ArrayList<>(last.kept());
						k.add(t);
						units.set(units.size() - 1, new Unit(null, null, k));
					} else {
						units.add(new Unit(null, null, List.of(t)));
					}
				} else if (last != null && !last.isKept()) {
					// keep the style that covers more of the text
					Style s = last.text().strip().length() >= t.text().strip().length() ? last.style() : t.style();
					units.set(units.size() - 1, new Unit(last.text() + t.text(), s, null));
				} else {
					units.add(new Unit(t.text(), t.style(), null));
				}
			}
		}
		return units;
	}

	/** A link, or a short coloured run: a name, kept in English. */
	static boolean kept(Text t) {
		if (t.style().link() != null) return true;
		if (t.style().color() == null) return false;
		String s = t.text().strip();
		return !s.isEmpty() && s.split("\\s+").length <= NAME_MAX_WORDS && LATIN.matcher(s).find();
	}

	/** Splits units at sentence ends inside the text units, so each sentence is translated (and remembered) alone. */
	private static List<List<Unit>> sentences(List<Unit> units) {
		List<List<Unit>> out = new ArrayList<>();
		List<Unit> cur = new ArrayList<>();
		for (Unit u : units) {
			if (u.isKept()) {
				cur.add(u);
				continue;
			}
			Matcher m = SENTENCE_END.matcher(u.text());
			int at = 0;
			while (m.find()) {
				cur.add(new Unit(u.text().substring(at, m.end()), u.style(), null));
				out.add(cur);
				cur = new ArrayList<>();
				at = m.end();
			}
			if (at < u.text().length()) cur.add(new Unit(u.text().substring(at), u.style(), null));
		}
		if (!cur.isEmpty()) out.add(cur);
		return out;
	}

	/** The translated sentence, or null to show it as it is (nothing to translate, not translated yet, bad answer). */
	private List<Inline> translateSentence(List<Unit> sentence) {
		if (sentence.stream().noneMatch(u -> !u.isKept() && LATIN.matcher(u.text()).find())) return null;
		String[] parts = new String[sentence.size()];
		boolean[] keep = new boolean[sentence.size()];
		Style style = null;
		int styleLength = -1;
		for (int i = 0; i < parts.length; i++) {
			Unit u = sentence.get(i);
			keep[i] = u.isKept();
			if (keep[i]) {
				StringBuilder sb = new StringBuilder();
				for (Inline in : u.kept()) if (in instanceof Text t) sb.append(t.text());
				parts[i] = sb.toString();
			} else {
				parts[i] = u.text();
				if (u.text().strip().length() > styleLength) {
					style = u.style();
					styleLength = u.text().strip().length();
				}
			}
		}
		Object[] result;
		try {
			result = translator.translate(parts, keep);
		} catch (RuntimeException e) {
			result = null;
		}
		if (result == null) return null;
		List<Inline> out = new ArrayList<>();
		for (Object o : result) {
			if (o instanceof Integer i && i >= 0 && i < parts.length && keep[i]) {
				out.addAll(sentence.get(i).kept());
			} else if (o instanceof String s && !s.isEmpty()) {
				out.add(new Text(s, style));
			} else {
				return null; // an answer we can't map back: show the original rather than guess
			}
		}
		return out;
	}
}
