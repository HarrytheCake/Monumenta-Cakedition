package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.harrythecake.caketext.translator.TranslatorConfig.OnlineService;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * An online translation service with the player's own key: Google Cloud Translation (v2, API key), DeepL (free keys
 * end in {@code :fx} and use api-free.deepl.com), Microsoft Translator (v3, key + region) or LibreTranslate (any
 * server, key optional). Each line goes to the service as plain text; placeholders are stand-in numbers by then.
 *
 * <p>Never {@link #local()}: player chat never reaches it. Services bill per character, so a session stops at
 * {@code onlineCharLimit} characters. A rejected key (401/403) stops it until the settings change; a rate limit (429)
 * pauses it for a minute. No health check: asking costs requests too, so it's ready as soon as it's configured.
 */
final class OnlineEngine implements TranslationEngine {
	private static final Gson GSON = new Gson();
	private static final long PAUSE_MS = 60_000;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private volatile TranslatorConfig config;
	private final AtomicLong sent = new AtomicLong();
	private volatile boolean keyRejected;
	private volatile long pausedUntil;
	private volatile String lastError;
	/** For tests: replaces the service's address. */
	private final @Nullable String endpointOverride;

	OnlineEngine(TranslatorConfig config) {
		this(config, null);
	}

	OnlineEngine(TranslatorConfig config, @Nullable String endpointOverride) {
		this.config = config;
		this.endpointOverride = endpointOverride;
	}

	void use(TranslatorConfig config) {
		this.config = config;
		keyRejected = false;
		pausedUntil = 0;
		lastError = null;
	}

	@Override
	public String id() {
		return "online-" + config.onlineService.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String label() {
		return switch (config.onlineService) {
			case GOOGLE -> "Google Translate";
			case DEEPL -> "DeepL";
			case MICROSOFT -> "Microsoft Translator";
			case LIBRETRANSLATE -> "LibreTranslate";
		};
	}

	@Override
	public boolean local() {
		return false;
	}

	private boolean configured(TranslatorConfig cfg) {
		return cfg.onlineService == OnlineService.LIBRETRANSLATE ? !cfg.onlineUrl.isBlank() : !cfg.onlineApiKey.isBlank();
	}

	private boolean overLimit(TranslatorConfig cfg) {
		return cfg.onlineCharLimit > 0 && sent.get() >= cfg.onlineCharLimit;
	}

	@Override
	public boolean ready() {
		TranslatorConfig cfg = config;
		return configured(cfg) && !keyRejected && !overLimit(cfg) && System.currentTimeMillis() >= pausedUntil;
	}

	@Override
	public Text describe() {
		TranslatorConfig cfg = config;
		if (!configured(cfg)) {
			return Text.translatable("caketext_translator.online.no_key", label());
		}
		if (keyRejected) {
			return Text.translatable("caketext_translator.online.key_rejected", label(), String.valueOf(lastError));
		}
		if (overLimit(cfg)) {
			return Text.translatable("caketext_translator.online.limit", label(), cfg.onlineCharLimit);
		}
		if (System.currentTimeMillis() < pausedUntil) {
			return Text.translatable("caketext_translator.online.paused", label());
		}
		return Text.translatable("caketext_translator.online.ready", label(), sent.get());
	}

	@Override
	public String translate(String text, TranslatorConfig cfg) throws Exception {
		if (!ready()) {
			throw new IOException(label() + " is not available");
		}
		HttpRequest request = request(cfg, text, endpointOverride);
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		sent.addAndGet(text.length());
		int status = response.statusCode();
		if (status == 401 || status == 403) {
			keyRejected = true;
			lastError = "HTTP " + status;
			throw new IOException(label() + " rejected the key: HTTP " + status);
		}
		if (status == 429 || status == 456) { // 456: DeepL's quota exceeded
			pausedUntil = System.currentTimeMillis() + PAUSE_MS;
			throw new IOException(label() + " limit reached: HTTP " + status);
		}
		if (status != 200) {
			throw new IOException(label() + ": HTTP " + status + ": " + response.body());
		}
		String out = parse(cfg.onlineService, response.body());
		if (out == null || out.isBlank()) {
			throw new IllegalStateException("empty translation");
		}
		return out.strip();
	}

	/** Characters sent this session (all services together). */
	long sent() {
		return sent.get();
	}

	// ---- one request per service ----

	static String target(OnlineService service, String language) {
		boolean traditional = "zh_tw".equals(language);
		return switch (service) {
			case GOOGLE -> traditional ? "zh-TW" : "zh-CN";
			case DEEPL -> traditional ? "ZH-HANT" : "ZH-HANS";
			case MICROSOFT, LIBRETRANSLATE -> traditional ? "zh-Hant" : "zh-Hans";
		};
	}

	static HttpRequest request(TranslatorConfig cfg, String text, @Nullable String endpointOverride) {
		String key = cfg.onlineApiKey.trim();
		String target = target(cfg.onlineService, cfg.language);
		JsonObject body = new JsonObject();
		HttpRequest.Builder b;
		String json;
		switch (cfg.onlineService) {
			case GOOGLE -> {
				body.addProperty("q", text);
				body.addProperty("source", "en");
				body.addProperty("target", target);
				body.addProperty("format", "text");
				b = HttpRequest.newBuilder(URI.create(base(endpointOverride, "https://translation.googleapis.com")
						+ "/language/translate/v2")).header("X-goog-api-key", key);
				json = GSON.toJson(body);
			}
			case DEEPL -> {
				JsonArray texts = new JsonArray();
				texts.add(text);
				body.add("text", texts);
				body.addProperty("source_lang", "EN");
				body.addProperty("target_lang", target);
				String host = key.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com";
				b = HttpRequest.newBuilder(URI.create(base(endpointOverride, host) + "/v2/translate"))
						.header("Authorization", "DeepL-Auth-Key " + key);
				json = GSON.toJson(body);
			}
			case MICROSOFT -> {
				JsonArray items = new JsonArray();
				JsonObject item = new JsonObject();
				item.addProperty("Text", text);
				items.add(item);
				b = HttpRequest.newBuilder(URI.create(base(endpointOverride, "https://api.cognitive.microsofttranslator.com")
						+ "/translate?api-version=3.0&from=en&to=" + URLEncoder.encode(target, StandardCharsets.UTF_8)))
						.header("Ocp-Apim-Subscription-Key", key);
				if (!cfg.onlineRegion.isBlank()) {
					b.header("Ocp-Apim-Subscription-Region", cfg.onlineRegion.trim());
				}
				json = GSON.toJson(items);
			}
			case LIBRETRANSLATE -> {
				body.addProperty("q", text);
				body.addProperty("source", "en");
				body.addProperty("target", target);
				body.addProperty("format", "text");
				if (!key.isEmpty()) {
					body.addProperty("api_key", key);
				}
				b = HttpRequest.newBuilder(URI.create(base(endpointOverride, cfg.onlineUrl) + "/translate"));
				json = GSON.toJson(body);
			}
			default -> throw new IllegalStateException();
		}
		return b.timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build();
	}

	private static String base(@Nullable String override, String url) {
		String s = (override != null ? override : url).trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	/** The translated text from a service's answer, or null if it has none. */
	static @Nullable String parse(OnlineService service, String body) {
		JsonElement json = GSON.fromJson(body, JsonElement.class);
		if (json == null) {
			return null;
		}
		return switch (service) {
			case GOOGLE -> json.getAsJsonObject().getAsJsonObject("data").getAsJsonArray("translations").get(0)
					.getAsJsonObject().get("translatedText").getAsString();
			case DEEPL -> json.getAsJsonObject().getAsJsonArray("translations").get(0).getAsJsonObject().get("text").getAsString();
			case MICROSOFT -> json.getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("translations").get(0)
					.getAsJsonObject().get("text").getAsString();
			case LIBRETRANSLATE -> json.getAsJsonObject().get("translatedText").getAsString();
		};
	}
}
