package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The local-server engine against fake Ollama and OpenAI-compatible servers. */
class ServerEngineTest {
	private static final Gson GSON = new Gson();
	private HttpServer server;
	private final AtomicReference<JsonObject> lastRequest = new AtomicReference<>();

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private static void reply(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	private String start(String tagsPath, String tagsBody, String genPath, String genBody) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(tagsPath, ex -> reply(ex, 200, tagsBody));
		server.createContext(genPath, ex -> {
			lastRequest.set(GSON.fromJson(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class));
			reply(ex, 200, genBody);
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}

	private static TranslatorConfig config(String url, TranslatorConfig.Protocol protocol) {
		TranslatorConfig c = new TranslatorConfig();
		c.engine = TranslatorConfig.Engine.SERVER;
		c.serverProtocol = protocol;
		c.serverUrl = url;
		return c;
	}

	@Test
	void ollamaRawPromptInHunyuanFormat() throws Exception {
		String url = start("/api/tags", "{\"models\":[{\"name\":\"hf.co/mradermacher/Hunyuan-MT-7B-GGUF:Q4_K_M\"}]}",
				"/api/generate", "{\"response\":\"启动需要7301秒<|eos|>\",\"done\":true}");
		TranslatorConfig c = config(url, TranslatorConfig.Protocol.OLLAMA);
		ServerEngine engine = new ServerEngine(c);
		engine.check();
		assertTrue(engine.ready(), engine.describe().getString());
		assertEquals("Hunyuan-MT-7B-GGUF:Q4_K_M", engine.label());
		assertEquals("server-hf.co_mradermacher_Hunyuan-MT-7B-GGUF_Q4_K_M", engine.id());

		assertEquals("启动需要7301秒", engine.translate("Takes 7301 seconds", c));
		JsonObject req = lastRequest.get();
		assertEquals("<|startoftext|>把下面的文本翻译成中文，不要额外解释。\n\nTakes 7301 seconds<|extra_0|>", req.get("prompt").getAsString());
		assertTrue(req.get("raw").getAsBoolean());
		assertFalse(req.get("stream").getAsBoolean());
		JsonObject options = req.getAsJsonObject("options");
		assertEquals(20, options.get("top_k").getAsInt());
		assertEquals(0.6, options.get("top_p").getAsDouble(), 1e-9);
		assertEquals(1.05, options.get("repeat_penalty").getAsDouble(), 1e-9);
		assertEquals(0.7, options.get("temperature").getAsDouble(), 1e-9);
		assertEquals("<|eos|>", options.getAsJsonArray("stop").get(0).getAsString());

		c.language = "zh_tw";
		engine.translate("Back", c);
		assertTrue(lastRequest.get().get("prompt").getAsString().contains("翻译成繁体中文"));
	}

	@Test
	void openAiCompatible() throws Exception {
		String url = start("/v1/models", "{\"data\":[{\"id\":\"hunyuan-mt-7b\"}]}",
				"/v1/chat/completions", "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"“欢迎来到山谷！”\"}}]}");
		TranslatorConfig c = config(url, TranslatorConfig.Protocol.OPENAI);
		c.serverModel = "hunyuan-mt-7b";
		ServerEngine engine = new ServerEngine(c);
		engine.check();
		assertTrue(engine.ready());
		// quotes the source didn't have are dropped
		assertEquals("欢迎来到山谷！", engine.translate("Welcome to the Valley!", c));
		JsonObject req = lastRequest.get();
		assertEquals("把下面的文本翻译成中文，不要额外解释。\n\nWelcome to the Valley!",
				req.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
		assertEquals("hunyuan-mt-7b", req.get("model").getAsString());
	}

	@Test
	void missingModelOrServer() throws Exception {
		String url = start("/api/tags", "{\"models\":[{\"name\":\"qwen3:4b\"}]}", "/api/generate", "{}");
		TranslatorConfig c = config(url, TranslatorConfig.Protocol.OLLAMA);
		ServerEngine engine = new ServerEngine(c);
		engine.check();
		assertFalse(engine.ready());
		assertTrue(engine.describe().getString().contains("no_model"), engine.describe().getString());

		server.stop(0);
		server = null;
		engine.check();
		assertFalse(engine.ready());
		assertTrue(engine.describe().getString().contains("unreachable"), engine.describe().getString());
		assertThrows(IOException.class, () -> engine.translate("Hi", c));
	}

	/**
	 * Against a real server: {@code -Dcaketext.serverUrl=http://127.0.0.1:11434} (and optionally
	 * {@code -Dcaketext.serverModel=…}, {@code -Dcaketext.serverProtocol=OPENAI}). Skipped otherwise.
	 */
	@Test
	void realServerOnGameTemplates() throws Exception {
		String url = System.getProperty("caketext.serverUrl", "");
		org.junit.jupiter.api.Assumptions.assumeTrue(!url.isEmpty(), "no real server configured");
		TranslatorConfig c = config(url, TranslatorConfig.Protocol.valueOf(System.getProperty("caketext.serverProtocol", "OLLAMA")));
		String model = System.getProperty("caketext.serverModel", "");
		if (!model.isEmpty()) {
			c.serverModel = model;
		}
		ServerEngine engine = new ServerEngine(c);
		engine.check();
		assertTrue(engine.ready(), engine.describe().getString());
		String[] templates = {
				"Takes {~} seconds to wind up when activated.",
				"{c1}[Quest] {c2}Speak with the guard at the gate of Sierhaven.",
				"{n} has completed the dungeon in {~} minutes!",
				"{c1}Right-Click {c2}to open up the recipe menu.",
				"You need {~} more Experience to reach level {~}.",
				"Total Damage: {~}",
				"Back",
				"I wouldn't go in there if I were you... strange noises come from that cave at night."};
		for (String template : templates) {
			long t = System.nanoTime();
			String dst = MachineTranslator.translate(template, s -> {
				try {
					return engine.translate(s, c);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			});
			System.out.printf("%5d ms  %-62s -> %s%n", (System.nanoTime() - t) / 1_000_000, template, dst);
			assertEquals(io.github.harrythecake.caketext.core.template.TextTemplate.parse(template).stream()
							.filter(x -> !(x instanceof io.github.harrythecake.caketext.core.template.TextTemplate.Token.Literal)).count(),
					io.github.harrythecake.caketext.core.template.TextTemplate.parse(dst).stream()
							.filter(x -> !(x instanceof io.github.harrythecake.caketext.core.template.TextTemplate.Token.Literal)).count(),
					"placeholders kept in " + dst);
		}
	}

	@Test
	void cleanUp() {
		List<String> stops = List.of("<|eos|>");
		assertEquals("你好", ServerEngine.clean("Hello", "你好<|eos|>garbage", stops));
		assertEquals("你好", ServerEngine.clean("Hello", "\n你好\n（注：这是问候）", stops));
		assertEquals("「你好」", ServerEngine.clean("「Hello」", "「你好」", stops));
		assertThrows(IllegalStateException.class, () -> ServerEngine.clean("Hello", "  ", stops));
	}
}
