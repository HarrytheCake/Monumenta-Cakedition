package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindTest {
	@Test
	void emptyFindsNothingAndFiltersNothing() {
		Find find = new Find(false, false);
		assertFalse(find.active());
		assertFalse(find.matches("anything"));
		assertTrue(find.ranges("anything").isEmpty());
	}

	@Test
	void plainTextIgnoresCaseAndRegexCharacters() {
		Find find = new Find(false, false);
		find.set("[g]", false, false);
		assertTrue(find.active());
		assertTrue(find.matches("<lfg> [G] qwertfox » hi"));
		assertFalse(find.matches("<g> qwertfox » hi"));
	}

	@Test
	void matchCase() {
		Find find = new Find(false, true);
		find.set("Trade", false, true);
		assertTrue(find.matches("<tr> Trade offer"));
		assertFalse(find.matches("<tr> trade offer"));
	}

	@Test
	void caseInsensitiveOutsideAscii() {
		Find find = new Find(false, false);
		find.set("ÉTÉ", false, false);
		assertTrue(find.matches("un été"));
	}

	@Test
	void regex() {
		Find find = new Find(true, false);
		find.set("^<(g|lfg)> ", true, false);
		assertTrue(find.matches("<lfg> Mock_Pl4yer » lf healer"));
		assertFalse(find.matches("<tr> Mock_Pl4yer » wts"));
	}

	@Test
	void invalidRegexFiltersNothing() {
		Find find = new Find(true, false);
		find.set("(unclosed", true, false);
		assertTrue(find.invalid());
		assertFalse(find.active());
		// the same text as plain text is fine
		find.set("(unclosed", false, false);
		assertFalse(find.invalid());
		assertTrue(find.matches("a (unclosed bracket"));
	}

	@Test
	void rangesAreEveryNonEmptyMatch() {
		Find find = new Find(true, false);
		find.set("o|x*", true, false);
		assertEquals(List.of(new Find.Range(1, 2), new Find.Range(4, 5)), find.ranges("follow"));
		find.set("ab", false, false);
		assertEquals(List.of(new Find.Range(0, 2), new Find.Range(3, 5)), find.ranges("AB ab"));
	}

	@Test
	void setReportsChanges() {
		Find find = new Find(false, false);
		assertTrue(find.set("x", false, false));
		assertFalse(find.set("x", false, false));
		assertTrue(find.set("x", true, false));
		assertTrue(find.set("x", true, true));
	}
}
