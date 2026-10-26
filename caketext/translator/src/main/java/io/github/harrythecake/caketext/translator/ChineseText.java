package io.github.harrythecake.caketext.translator;

import java.util.regex.Pattern;

/**
 * Light clean-up of opus-mt Chinese output: the model writes ASCII punctuation ({@code 你好,世界!}) and stray spaces
 * between Chinese words ({@code 如果我是你 我不会进去}, {@code 成分 。}).
 */
final class ChineseText {
	private static final String CJK = "\\p{IsHan}\\u3000-\\u303f\\uff00-\\uffef";
	private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile("(?<=[" + CJK + "])\\s+(?=[。，！？：；、])");
	private static final Pattern SPACE_BETWEEN_HAN = Pattern.compile("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})");

	private ChineseText() {
	}

	static String tidy(String s) {
		if (!s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
			return s;
		}
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean afterHan = i > 0 && isHan(s.charAt(i - 1));
			boolean beforeHan = i + 1 < s.length() && isHan(s.charAt(i + 1));
			char full = switch (c) {
				case ',' -> '，';
				case '!' -> '！';
				case '?' -> '？';
				case ':' -> '：';
				case ';' -> '；';
				default -> c;
			};
			// only next to Chinese, so "1,250" and "x: 5" in mixed text stay as they were
			out.append(full != c && (afterHan || beforeHan) ? full : c);
		}
		String r = SPACE_BEFORE_PUNCT.matcher(out).replaceAll("");
		return SPACE_BETWEEN_HAN.matcher(r).replaceAll("，");
	}

	private static boolean isHan(char c) {
		return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
	}
}
