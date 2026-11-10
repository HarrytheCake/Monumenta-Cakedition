package io.github.harrythecake.cakewiki.doc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CssColorTest {
	@Test
	void parsesCommonForms() {
		assertEquals(0xFFAA00, CssColor.parse("#FFAA00"));
		assertEquals(0xFFAA00, CssColor.parse("#fa0"));
		assertEquals(0x112233, CssColor.parse("#112233cc"));
		assertEquals(0x55FF55, CssColor.parse("rgb(85, 255, 85)"));
		assertEquals(0xFF0000, CssColor.parse("rgba(100%, 0%, 0%, 0.5)"));
		assertEquals(0x808080, CssColor.parse("Grey"));
		assertNull(CssColor.parse("transparent"));
		assertNull(CssColor.parse("#12"));
		assertNull(CssColor.parse("rgb(oops)"));
	}

	@Test
	void readsTheRightPropertyFromAStyle() {
		String style = "border: 1px solid #21295A; width: 100%; background: #191E3E; color:#fff !important";
		assertEquals(0xFFFFFF, CssColor.fromStyle(style, "color"));
		assertEquals(0x191E3E, CssColor.fromStyle(style, "background-color"));
		assertNull(CssColor.fromStyle("border-color: red", "color"));
		assertEquals(0x00FF00, CssColor.fromStyle("color: red; color: lime", "color"));
		assertEquals(0x010203, CssColor.fromStyle("background: url(x.png) rgb(1, 2, 3) no-repeat", "background-color"));
	}
}
