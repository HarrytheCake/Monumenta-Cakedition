package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every UI string in en_us, zh_cn and zh_tw, with the same keys. */
class LangTest {
	private static Set<String> keys(String lang) throws IOException {
		try (InputStream in = LangTest.class.getResourceAsStream("/assets/cakeupdater/lang/" + lang + ".json")) {
			assertNotNull(in, lang);
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return root.keySet();
		}
	}

	@Test
	void sameKeysInEveryLanguage() throws IOException {
		Set<String> en = keys("en_us");
		assertEquals(en, keys("zh_cn"));
		assertEquals(en, keys("zh_tw"));
	}
}
