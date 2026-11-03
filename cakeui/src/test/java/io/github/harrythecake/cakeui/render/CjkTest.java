package io.github.harrythecake.cakeui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which text counts as CJK for the `readable` minimum size. */
class CjkTest {
	@Test
	void findsCjk() {
		assertTrue(Renderer.hasCjk("CakeUI 示例布局"));
		assertTrue(Renderer.hasCjk("ひらがな"));
		assertTrue(Renderer.hasCjk("カタカナ"));
		assertTrue(Renderer.hasCjk("한국어"));
		assertTrue(Renderer.hasCjk("「」"));
		assertTrue(Renderer.hasCjk("Ｃａｋｅ"));
	}

	@Test
	void latinAndSymbolsAreNotCjk() {
		assertFalse(Renderer.hasCjk("Singleplayer"));
		assertFalse(Renderer.hasCjk("❤ 20 / 20 — Ünïcödé ♿"));
		assertFalse(Renderer.hasCjk(""));
	}
}
