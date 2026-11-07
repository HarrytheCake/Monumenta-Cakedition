package io.github.harrythecake.cakechat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every string exists in all three languages. */
class LangTest {
	private static Set<String> keys(String lang) throws IOException {
		try (InputStream in = LangTest.class.getResourceAsStream("/assets/cakechat/lang/" + lang + ".json")) {
			assertNotNull(in, lang);
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			json.entrySet().forEach(e -> assertFalse(e.getValue().getAsString().isBlank(), lang + " " + e.getKey()));
			return json.keySet();
		}
	}

	@Test
	void sameKeysInEveryLanguage() throws IOException {
		Set<String> english = keys("en_us");
		assertEquals(english, keys("zh_cn"));
		assertEquals(english, keys("zh_tw"));
	}
}
