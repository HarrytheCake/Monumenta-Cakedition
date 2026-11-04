package io.github.harrythecake.cakeui.render;

/** Which text counts as Chinese, Japanese or Korean, for the `readable` minimum size of Minecraft's bitmap font. */
public final class Cjk {
	private Cjk() {
	}

	/** Chinese, Japanese or Korean characters (Han, kana, hangul, bopomofo, CJK punctuation and full-width forms). */
	public static boolean hasCjk(String s) {
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (cp < 0x2E80) {
				continue;
			}
			Character.UnicodeScript script = Character.UnicodeScript.of(cp);
			if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
					|| script == Character.UnicodeScript.HANGUL || script == Character.UnicodeScript.BOPOMOFO
					|| (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF)) {
				return true;
			}
		}
		return false;
	}
}
