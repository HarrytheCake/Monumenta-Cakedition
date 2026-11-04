package io.github.harrythecake.cakeui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which text counts as CJK for the `readable` minimum size. */
class CjkTest {
	@Test
	void findsCjk() {
		assertTrue(Cjk.hasCjk("CakeUI 示例布局"));
		assertTrue(Cjk.hasCjk("ひらがな"));
		assertTrue(Cjk.hasCjk("カタカナ"));
		assertTrue(Cjk.hasCjk("한국어"));
		assertTrue(Cjk.hasCjk("「」"));
		assertTrue(Cjk.hasCjk("Ｃａｋｅ"));
	}

	@Test
	void latinAndSymbolsAreNotCjk() {
		assertFalse(Cjk.hasCjk("Singleplayer"));
		assertFalse(Cjk.hasCjk("❤ 20 / 20 — Ünïcödé ♿"));
		assertFalse(Cjk.hasCjk(""));
	}
}
