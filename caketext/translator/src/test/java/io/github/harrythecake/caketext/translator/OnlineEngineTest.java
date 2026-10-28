package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.harrythecake.caketext.translator.TranslatorConfig.OnlineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The online engine's exact requests for each service, against a fake service on 127.0.0.1 (no real service is called). */
class OnlineEngineTest {
	private static final Gson GSON = new Gson();
	private HttpServer server;
	private final AtomicReference<HttpExchange> last = new AtomicReference<>();
	private final AtomicReference<String> lastBody = new AtomicReference<>();
	private final AtomicReference<String> lastQuery = new AtomicReference<>();

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private String start(String path, int status, String answer) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(path, ex -> {
			last.set(ex);
			lastQuery.set(ex.getRequestURI().getRawQuery());
			lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static TranslatorConfig config(OnlineService service, String key) {
		TranslatorConfig c = new TranslatorConfig();
		c.engine = TranslatorConfig.Engine.ONLINE;
		c.onlineService = service;
		c.onlineApiKey = key;
		return c;
	}

	private JsonElement body() {
		return GSON.fromJson(lastBody.get(), JsonElement.class);
	}

	private String header(String name) {
		return last.get().getRequestHeaders().getFirst(name);
	}

	@Test
	void google() throws Exception {
		String url = start("/language/translate/v2", 200, "{\"data\":{\"translations\":[{\"translatedText\":\"总伤害 7301\"}]}}");
		TranslatorConfig c = config(OnlineService.GOOGLE, "AIza-test");
		OnlineEngine e = new OnlineEngine(c, url);
		assertEquals("总伤害 7301", e.translate("Total Damage 7301", c));
		assertEquals("AIza-test", header("X-goog-api-key"));
		assertEquals(null, lastQuery.get(), "the key goes in a header, not the address");
		var b = body().getAsJsonObject();
		assertEquals("Total Damage 7301", b.get("q").getAsString());
		assertEquals("zh-CN", b.get("target").getAsString());
		assertEquals("text", b.get("format").getAsString());
		assertEquals(17, e.sent());
	}

	@Test
	void deeplTraditional() throws Exception {
		String url = start("/v2/translate", 200, "{\"translations\":[{\"detected_source_language\":\"EN\",\"text\":\"返回\"}]}");
		TranslatorConfig c = config(OnlineService.DEEPL, "abc:fx");
		c.language = "zh_tw";
		assertEquals("返回", new OnlineEngine(c, url).translate("Back", c));
		assertEquals("DeepL-Auth-Key abc:fx", header("Authorization"));
		var b = body().getAsJsonObject();
		assertEquals("Back", b.getAsJsonArray("text").get(0).getAsString());
		assertEquals("ZH-HANT", b.get("target_lang").getAsString());
		assertEquals("EN", b.get("source_lang").getAsString());
	}

	@Test
	void deeplFreeKeysUseTheFreeHost() {
		TranslatorConfig c = config(OnlineService.DEEPL, "abc:fx");
		assertEquals("api-free.deepl.com", OnlineEngine.request(c, "x", null).uri().getHost());
		c.onlineApiKey = "abc";
		assertEquals("api.deepl.com", OnlineEngine.request(c, "x", null).uri().getHost());
	}

	@Test
	void microsoftWithRegion() throws Exception {
		String url = start("/translate", 200, "[{\"translations\":[{\"text\":\"任务\",\"to\":\"zh-Hans\"}]}]");
		TranslatorConfig c = config(OnlineService.MICROSOFT, "ms-key");
		c.onlineRegion = "westeurope";
		assertEquals("任务", new OnlineEngine(c, url).translate("Quest", c));
		assertEquals("ms-key", header("Ocp-Apim-Subscription-Key"));
		assertEquals("westeurope", header("Ocp-Apim-Subscription-Region"));
		assertEquals("api-version=3.0&from=en&to=zh-Hans", lastQuery.get());
		assertEquals("Quest", body().getAsJsonArray().get(0).getAsJsonObject().get("Text").getAsString());
	}

	@Test
	void libreTranslateKeyOptional() throws Exception {
		String url = start("/translate", 200, "{\"translatedText\":\"你好\"}");
		TranslatorConfig c = config(OnlineService.LIBRETRANSLATE, "");
		c.onlineUrl = url + "/";
		OnlineEngine e = new OnlineEngine(c);
		assertTrue(e.ready(), "LibreTranslate needs only a server");
		assertEquals("你好", e.translate("Hello", c));
		var b = body().getAsJsonObject();
		assertFalse(b.has("api_key"));
		assertEquals("zh-Hans", b.get("target").getAsString());
	}

	@Test
	void rejectedKeyStopsUntilSettingsChange() throws Exception {
		String url = start("/language/translate/v2", 403, "{\"error\":{\"code\":403}}");
		TranslatorConfig c = config(OnlineService.GOOGLE, "bad");
		OnlineEngine e = new OnlineEngine(c, url);
		assertTrue(e.ready());
		assertThrows(IOException.class, () -> e.translate("Back", c));
		assertFalse(e.ready());
		e.use(c);
		assertTrue(e.ready());
	}

	@Test
	void rateLimitPauses() throws Exception {
		String url = start("/v2/translate", 429, "{}");
		TranslatorConfig c = config(OnlineService.DEEPL, "k:fx");
		OnlineEngine e = new OnlineEngine(c, url);
		assertThrows(IOException.class, () -> e.translate("Back", c));
		assertFalse(e.ready());
	}

	@Test
	void sessionCharacterLimit() throws Exception {
		String url = start("/language/translate/v2", 200, "{\"data\":{\"translations\":[{\"translatedText\":\"x\"}]}}");
		TranslatorConfig c = config(OnlineService.GOOGLE, "k");
		c.onlineCharLimit = 10;
		OnlineEngine e = new OnlineEngine(c, url);
		e.translate("Total Damage", c);
		assertFalse(e.ready(), "12 characters sent, limit 10");
		c.onlineCharLimit = 0;
		assertTrue(e.ready(), "0 = no limit");
	}

	@Test
	void notConfiguredIsNotReady() {
		assertFalse(new OnlineEngine(config(OnlineService.GOOGLE, "")).ready());
		assertFalse(new OnlineEngine(config(OnlineService.GOOGLE, "k")).local(), "player chat must never reach it");
	}

	@Test
	void whichServersAreLocal() {
		for (String url : new String[] {"http://127.0.0.1:11434", "http://localhost:8080", "http://192.168.1.20:1234",
				"http://10.0.0.5", "http://172.20.1.1", "http://[::1]:8080", "http://gpu-box.local:11434"}) {
			assertTrue(ServerEngine.isLocal(url), url);
		}
		for (String url : new String[] {"https://api.openai.com", "https://api.deepseek.com", "http://8.8.8.8",
				"http://172.32.0.1", "not a url"}) {
			assertFalse(ServerEngine.isLocal(url), url);
		}
	}
}
