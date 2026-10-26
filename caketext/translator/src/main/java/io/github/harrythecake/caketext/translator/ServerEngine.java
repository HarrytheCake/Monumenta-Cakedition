package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.text.Text;

/**
 * An LLM in a local server, e.g. Hunyuan-MT-7B in Ollama. Two protocols:
 * <ul>
 *   <li><b>Ollama</b> ({@code /api/generate}, raw): the mod writes the model's own chat format around the prompt
 *       ({@code serverRawTemplate}), so it works even when Ollama doesn't know the model's template.</li>
 *   <li><b>OpenAI-compatible</b> ({@code /v1/chat/completions}): llama.cpp {@code llama-server --jinja}, LM Studio…;
 *       the server applies the chat template.</li>
 * </ul>
 * Defaults follow Tencent's model card for Hunyuan-MT: the prompt {@code 把下面的文本翻译成<language>，不要额外解释。},
 * no system prompt, top_k 20, top_p 0.6, repetition penalty 1.05, temperature 0.7.
 *
 * <p>The server is checked in the background (every 15 s until it answers, then every minute): it must answer and
 * list the configured model.
 */
final class ServerEngine implements TranslationEngine {
	private static final Gson GSON = new Gson();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final ScheduledExecutorService checker = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "CakeText-server-check");
		t.setDaemon(true);
		return t;
	});
	private volatile TranslatorConfig config;
	private volatile Text status = Text.translatable("caketext_translator.server.checking");
	private volatile boolean ready;
	private volatile long nextCheck;

	ServerEngine(TranslatorConfig config) {
		this.config = config;
		checker.scheduleWithFixedDelay(() -> {
			if (System.currentTimeMillis() >= nextCheck) {
				check();
			}
		}, 0, 5, TimeUnit.SECONDS);
	}

	void use(TranslatorConfig config) {
		this.config = config;
		nextCheck = 0;
	}

	@Override
	public String id() {
		return "server-" + config.serverModel.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	/** The model name without a registry path: {@code hf.co/x/Hunyuan-MT-7B-GGUF:Q4_K_M} → {@code Hunyuan-MT-7B-GGUF:Q4_K_M}. */
	@Override
	public String label() {
		String m = config.serverModel;
		return m.substring(m.lastIndexOf('/') + 1);
	}

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public Text describe() {
		return status;
	}

	private boolean ollama() {
		return config.serverProtocol == TranslatorConfig.Protocol.OLLAMA;
	}

	private URI uri(String path) {
		String base = config.serverUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return URI.create(base + path);
	}

	/** Is the server up and does it have the model? */
	void check() {
		TranslatorConfig cfg = config;
		boolean ok = false;
		try {
			HttpRequest request = HttpRequest.newBuilder(uri(ollama() ? "/api/tags" : "/v1/models"))
					.timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				status = Text.translatable("caketext_translator.server.error", "HTTP " + response.statusCode());
			} else if (hasModel(GSON.fromJson(response.body(), JsonObject.class), cfg.serverModel)) {
				status = Text.translatable("caketext_translator.server.ready", label());
				ok = true;
			} else {
				status = Text.translatable("caketext_translator.server.no_model", cfg.serverModel);
			}
		} catch (IOException | RuntimeException e) {
			status = Text.translatable("caketext_translator.server.unreachable", cfg.serverUrl);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		ready = ok;
		nextCheck = System.currentTimeMillis() + (ok ? 60_000 : 15_000);
	}

	private boolean hasModel(JsonObject body, String model) {
		JsonArray list = body == null ? null : body.getAsJsonArray(ollama() ? "models" : "data");
		if (list == null) {
			return false;
		}
		String want = model.toLowerCase(Locale.ROOT);
		for (JsonElement e : list) {
			JsonObject m = e.getAsJsonObject();
			for (String key : List.of("name", "model", "id")) {
				if (m.has(key) && !m.get(key).isJsonNull()) {
					String have = m.get(key).getAsString().toLowerCase(Locale.ROOT);
					// Ollama adds ":latest" to names given without a tag
					if (have.equals(want) || have.equals(want + ":latest")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The prompt for {@code text}: {@code {language}} and {@code {text}} filled in. */
	static String prompt(TranslatorConfig cfg, String text) {
		String language = "zh_tw".equals(cfg.language) ? "繁体中文" : "中文";
		return cfg.serverPrompt.replace("{language}", language).replace("{text}", text);
	}

	@Override
	public String translate(String text, TranslatorConfig cfg) throws Exception {
		String prompt = prompt(cfg, text);
		JsonObject body = new JsonObject();
		body.addProperty("model", cfg.serverModel);
		body.addProperty("stream", false);
		int maxTokens = Math.min(512, text.length() * 3 + 32);
		if (ollama()) {
			body.addProperty("prompt", cfg.serverRawTemplate.isEmpty() ? prompt : cfg.serverRawTemplate.replace("{prompt}", prompt));
			body.addProperty("raw", !cfg.serverRawTemplate.isEmpty());
			body.addProperty("keep_alive", "30m");
			JsonObject options = new JsonObject();
			options.addProperty("temperature", cfg.serverTemperature);
			options.addProperty("top_k", 20);
			options.addProperty("top_p", 0.6);
			options.addProperty("repeat_penalty", 1.05);
			options.addProperty("num_predict", maxTokens);
			options.add("stop", GSON.toJsonTree(cfg.serverStop));
			body.add("options", options);
		} else {
			JsonArray messages = new JsonArray();
			JsonObject user = new JsonObject();
			user.addProperty("role", "user");
			user.addProperty("content", prompt);
			messages.add(user);
			body.add("messages", messages);
			body.addProperty("temperature", cfg.serverTemperature);
			body.addProperty("top_p", 0.6);
			body.addProperty("top_k", 20);
			body.addProperty("repeat_penalty", 1.05);
			body.addProperty("max_tokens", maxTokens);
			body.add("stop", GSON.toJsonTree(cfg.serverStop));
		}
		HttpRequest request = HttpRequest.newBuilder(uri(ollama() ? "/api/generate" : "/v1/chat/completions"))
				.timeout(Duration.ofSeconds(120)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			ready = false;
			nextCheck = 0;
			throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
		}
		JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
		String out = ollama() ? json.get("response").getAsString()
				: json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
		return clean(text, out, cfg.serverStop);
	}

	/** Drops stop tokens the server left in, surrounding quotes the source didn't have, and extra lines it added. */
	static String clean(String source, String out, List<String> stops) {
		String s = out;
		for (String stop : stops) {
			int i = s.indexOf(stop);
			if (i >= 0) {
				s = s.substring(0, i);
			}
		}
		s = s.strip();
		if (!source.contains("\n")) {
			for (String line : s.split("\n")) {
				if (!line.isBlank()) {
					s = line.strip();
					break;
				}
			}
		}
		String src = source.strip();
		s = dropAsides(src, s);
		for (String[] q : new String[][] {{"\"", "\""}, {"“", "”"}, {"「", "」"}, {"'", "'"}}) {
			if (s.length() > 2 && s.startsWith(q[0]) && s.endsWith(q[1]) && !(src.startsWith(q[0]) && src.endsWith(q[1]))) {
				s = s.substring(q[0].length(), s.length() - q[1].length()).strip();
			}
		}
		if (s.isEmpty()) {
			throw new IllegalStateException("empty translation");
		}
		return s;
	}

	private static final java.util.regex.Pattern ASIDE = java.util.regex.Pattern.compile("\\s*[（(]([^（）()]*)[）)]");
	private static final java.util.regex.Pattern ALTERNATIVE = java.util.regex.Pattern.compile(
			"^\\s*(?:或|或者|也可|也可以|亦可|又译|另译|注|备注|即|意为|意思是|or |i\\.e\\.|note)", java.util.regex.Pattern.CASE_INSENSITIVE);
	private static final java.util.regex.Pattern LATIN_WORD = java.util.regex.Pattern.compile("[A-Za-z]");

	/**
	 * What LLMs add that a game line doesn't want: bracketed alternatives and notes ({@code （或译为…）}), the English
	 * repeated in brackets ({@code 懒惰之杖（Astral Staff of Sloth）}), and quote marks the source didn't have.
	 * Brackets the source itself has are kept.
	 */
	static String dropAsides(String source, String out) {
		java.util.regex.Matcher m = ASIDE.matcher(out);
		StringBuilder sb = new StringBuilder();
		String lowerSource = source.toLowerCase(java.util.Locale.ROOT);
		while (m.find()) {
			String inside = m.group(1).strip();
			boolean alternative = ALTERNATIVE.matcher(inside).find();
			boolean echo = LATIN_WORD.matcher(inside).find() && lowerSource.contains(inside.toLowerCase(java.util.Locale.ROOT))
					&& !source.contains("(" + inside + ")");
			m.appendReplacement(sb, alternative || echo ? "" : java.util.regex.Matcher.quoteReplacement(m.group()));
		}
		m.appendTail(sb);
		String s = sb.toString();
		if (!source.matches("(?s).*[\"“”「」『』].*")) {
			s = s.replaceAll("[“”「」『』]", "");
		}
		return s.strip();
	}

}
