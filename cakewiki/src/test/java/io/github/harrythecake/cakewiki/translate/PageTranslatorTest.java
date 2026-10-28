package io.github.harrythecake.cakewiki.translate;

import io.github.harrythecake.cakewiki.doc.Image;
import io.github.harrythecake.cakewiki.doc.Inline;
import io.github.harrythecake.cakewiki.doc.Inline.InlineImage;
import io.github.harrythecake.cakewiki.doc.Inline.LineBreak;
import io.github.harrythecake.cakewiki.doc.Inline.Style;
import io.github.harrythecake.cakewiki.doc.Inline.Text;
import io.github.harrythecake.cakewiki.doc.Link;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PageTranslatorTest {
	/** Answers from a table keyed by the parts as "text|[kept]|text"; records every question. */
	static final class FakeTranslator implements Translator {
		final Map<String, Object[]> answers = new HashMap<>();
		final List<String> asked = new ArrayList<>();
		long changes;

		@Override
		public Object[] translate(String[] parts, boolean[] keep) {
			StringBuilder key = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) key.append('|');
				key.append(keep[i] ? "[" + parts[i] + "]" : parts[i]);
			}
			asked.add(key.toString());
			return answers.get(key.toString());
		}

		@Override
		public long changes() {
			return changes;
		}
	}

	private static final Link KAUL = new Link.Page("Kaul", null);
	private static final Link SEALED = new Link.Page("Sealed Remorse", null);

	private static Text plain(String s) {
		return new Text(s, Style.PLAIN);
	}

	private static Text link(String s, Link l) {
		return new Text(s, Style.PLAIN.withLink(l));
	}

	private static String show(List<Inline> line) {
		StringBuilder sb = new StringBuilder();
		for (Inline in : line) {
			if (in instanceof Text t) sb.append(t.style().link() != null ? "<" + t.text() + ">" : t.text());
			else if (in instanceof LineBreak) sb.append("\n");
			else sb.append("#");
		}
		return sb.toString();
	}

	@Test
	void linksStayAndMoveWithTheTranslation() {
		FakeTranslator t = new FakeTranslator();
		t.answers.put("Dropped by |[Kaul]| in |[Sealed Remorse]|.", new Object[] {3, "中的", 1, "掉落。"});
		List<Inline> line = List.of(plain("Dropped by "), link("Kaul", KAUL), plain(" in "), link("Sealed Remorse", SEALED), plain("."));
		List<Inline> out = new PageTranslator(t).apply(line);
		assertEquals("<Sealed Remorse>中的<Kaul>掉落。", show(out));
		assertSame(line.get(1), out.get(2), "the link itself, unchanged");
	}

	@Test
	void notTranslatedYetShowsTheOriginalUntilNews() {
		FakeTranslator t = new FakeTranslator();
		PageTranslator p = new PageTranslator(t);
		List<Inline> line = List.of(plain("A rare sword."));
		assertEquals("A rare sword.", show(p.apply(line)));
		p.apply(line);
		assertEquals(1, t.asked.size(), "remembered per line until refresh");
		assertFalse(p.refresh());
		t.answers.put("A rare sword.", new Object[] {"一把稀有的剑。"});
		t.changes++;
		assertTrue(p.refresh());
		assertEquals("一把稀有的剑。", show(p.apply(line)));
	}

	@Test
	void sentencesAndLineBreaksAreTranslatedOnTheirOwn() {
		FakeTranslator t = new FakeTranslator();
		new PageTranslator(t).apply(List.of(plain("First one. Second one? third"), new LineBreak(), plain("Next line")));
		assertEquals(List.of("First one. ", "Second one? third", "Next line"), t.asked);
	}

	@Test
	void shortColouredRunsAreNamesLongOnesAreText() {
		Style rare = Style.PLAIN.withColor(0x5555FF);
		assertTrue(PageTranslator.kept(new Text("Rare", rare)));
		assertTrue(PageTranslator.kept(new Text("Sierhaven Guard Captain", rare)));
		assertFalse(PageTranslator.kept(new Text("The ancient blade hums with power when held.", rare)), "lore");
		assertFalse(PageTranslator.kept(new Text("12", rare)), "no letters");
		assertFalse(PageTranslator.kept(plain("Rare")));
	}

	@Test
	void aLinkSplitByAStyleChangeIsOneName() {
		FakeTranslator t = new FakeTranslator();
		Style boldLink = Style.PLAIN.withBold().withLink(KAUL);
		new PageTranslator(t).apply(List.of(plain("Fight "), link("Ka", KAUL), new Text("ul", boldLink), plain(" now")));
		assertEquals(List.of("Fight |[Kaul]| now"), t.asked);
	}

	@Test
	void picturesAreKeptInPlace() {
		FakeTranslator t = new FakeTranslator();
		InlineImage icon = new InlineImage(new Image("https://x/i.png", 16, 16, "icon"), null);
		t.answers.put("Costs 3 |[]| coins", new Object[] {"花费 3 ", 1, " 枚硬币"});
		List<Inline> out = new PageTranslator(t).apply(List.of(plain("Costs 3 "), icon, plain(" coins")));
		assertEquals("花费 3 # 枚硬币", show(out));
	}

	@Test
	void linesWithoutWordsAreNotAsked() {
		FakeTranslator t = new FakeTranslator();
		List<Inline> line = List.of(link("Kaul", KAUL), plain(" – 12"));
		assertSame(line, new PageTranslator(t).apply(line));
		assertTrue(t.asked.isEmpty());
	}

	@Test
	void badAnswersShowTheOriginal() {
		FakeTranslator t = new FakeTranslator();
		t.answers.put("Fight |[Kaul]|", new Object[] {"打败", 0}); // index 0 is not a kept part
		List<Inline> line = List.of(plain("Fight "), link("Kaul", KAUL));
		assertEquals("Fight <Kaul>", show(new PageTranslator(t).apply(line)));
		assertEquals(1, Arrays.asList(t.asked.toArray()).size());
	}
}
