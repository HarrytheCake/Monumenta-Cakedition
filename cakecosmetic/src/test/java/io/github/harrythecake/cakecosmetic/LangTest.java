package io.github.harrythecake.cakecosmetic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** en_us, zh_cn and zh_tw have the same keys. */
class LangTest {
	private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\s*:");

	private static Set<String> keys(String lang) throws IOException {
		try (InputStream in = LangTest.class.getResourceAsStream("/assets/cakecosmetic/lang/" + lang + ".json")) {
			assertNotNull(in, lang);
			Matcher m = KEY.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			Set<String> keys = new TreeSet<>();
			while (m.find()) keys.add(m.group(1));
			return keys;
		}
	}

	@Test
	void sameKeysInEveryLanguage() throws IOException {
		Set<String> en = keys("en_us");
		assertEquals(en, keys("zh_cn"));
		assertEquals(en, keys("zh_tw"));
	}
}
