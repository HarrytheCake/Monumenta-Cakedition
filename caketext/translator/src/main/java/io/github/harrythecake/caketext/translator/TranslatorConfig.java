package io.github.harrythecake.caketext.translator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.template.ItemFilter;

/** {@code config/caketext/translator.json} */
public final class TranslatorConfig {
	/** Languages with a model target token: folder name → opus-mt language token. */
	public static final Map<String, String> LANGUAGES = Map.of("zh_cn", ">>cmn_Hans<<", "zh_tw", ">>cmn_Hant<<");

	/** Which translations get an icon. */
	public enum Markers {
		ALL,
		MACHINE,
		OFF
	}

	/** How a translated line is shown where there's room (chat, tooltips). */
	public enum Display {
		/** The translation; in chat, hovering it shows the original. */
		TRANSLATED,
		/** The translation, then the original in grey. */
		BOTH,
		/** The original; in chat, hovering it shows the translation (tooltips: translation in grey below). */
		ORIGINAL
	}

	/** What translates the lines no string file has. */
	public enum Engine {
		/** Built in: opus-mt-en-zh int8, downloaded on request, runs in Java. */
		OPUS_MT,
		/** An LLM in a local server (Ollama, llama.cpp, LM Studio), e.g. Hunyuan-MT-7B. */
		SERVER,
		/** An online translation service with the player's own API key ({@link OnlineService}). */
		ONLINE
	}

	public enum Protocol {
		OLLAMA,
		OPENAI
	}

	/** Online translation services; each needs the player's own key (LibreTranslate: a server address, key optional). */
	public enum OnlineService {
		GOOGLE,
		DEEPL,
		MICROSOFT,
		LIBRETRANSLATE
	}

	/** What holding Alt does in an item tooltip. */
	public enum TooltipAlt {
		/** Tooltips are translated (per {@link #display}); holding Alt shows the original. */
		ORIGINAL_ON_ALT,
		/** Tooltips stay original; holding Alt shows them translated (nothing is translated until Alt is held). */
		TRANSLATION_ON_ALT,
		OFF
	}

	public Engine engine = Engine.OPUS_MT;
	public OnlineService onlineService = OnlineService.GOOGLE;
	/** The player's own key for the online service. Never shipped in a pack: it lives only in this file. */
	public String onlineApiKey = "";
	/** Microsoft only: the resource's region (e.g. {@code westeurope}); empty for a global resource. */
	public String onlineRegion = "";
	/** LibreTranslate only: the server (a public instance, or one you run). */
	public String onlineUrl = "https://libretranslate.com";
	/** Characters sent to the online service per game session before it stops (services bill per character). 0 = no limit. */
	public int onlineCharLimit = 200_000;
	public Protocol serverProtocol = Protocol.OLLAMA;
	/** OpenAI-compatible only: sent as {@code Authorization: Bearer <key>} (online LLM services); empty for local servers. */
	public String serverApiKey = "";
	/** Ollama listens on 11434; llama.cpp's llama-server on 8080; LM Studio on 1234. */
	public String serverUrl = "http://127.0.0.1:11434";
	/** As the server names it. This one is {@code ollama pull hf.co/mradermacher/Hunyuan-MT-7B-GGUF:Q4_K_M}. */
	public String serverModel = "hf.co/mradermacher/Hunyuan-MT-7B-GGUF:Q4_K_M";
	/** {@code {language}} = 中文 or 繁体中文, {@code {text}} = the line. Tencent's template for translating into Chinese. */
	public String serverPrompt = "把下面的文本翻译成{language}，不要额外解释。\n\n{text}";
	/**
	 * Ollama only: the model's chat format around {@code {prompt}}, sent raw. Hunyuan-MT's (from its tokenizer_config).
	 * Empty = let Ollama apply its own template.
	 */
	public String serverRawTemplate = "<|startoftext|>{prompt}<|extra_0|>";
	public List<String> serverStop = List.of("<|eos|>", "<|extra_0|>", "<|startoftext|>");
	public double serverTemperature = 0.7;

	public boolean enabled = true;
	/** Tooltips, and HUD text (scoreboard, titles, action bar…), which shows the translation unless ORIGINAL. */
	public Display display = Display.TRANSLATED;
	/** Server text in chat: by default the original, with the translation on hover. */
	public Display chatDisplay = Display.ORIGINAL;
	/** Translate what players say with the local model. Only the message: tags, rank and name stay as they are. */
	public boolean playerChat = true;
	public Display playerChatDisplay = Display.ORIGINAL;
	/** Target language: {@code zh_cn} or {@code zh_tw}. Also the dictionary folder. */
	public String language = "zh_cn";
	/** Translate strings that no dictionary has with the local model (once installed). */
	public boolean machine = true;
	/** {@code best} (4 beams, ~0.6 s a line) or {@code fast} (greedy, int8 activations, ~0.1 s a line). */
	public String machineQuality = "best";
	public Markers markers = Markers.ALL;
	/** Translate item names (the first tooltip line). Off by default: names stay as the server sends them. */
	public boolean translateItemNames = false;
	/** Item ids ({@code *} wildcards) whose tooltips are never translated: signs, books, shulker boxes. */
	public List<String> ignoredItems = new ArrayList<>(ItemFilter.DEFAULT);
	/** In item tooltips, one line at the bottom says where the translations came from. */
	public boolean tooltipFooter = true;
	public TooltipAlt tooltipAlt = TooltipAlt.ORIGINAL_ON_ALT;
	public Map<String, Boolean> surfaces = new LinkedHashMap<>();

	/** Pages in other mods' readers that use {@link io.github.harrythecake.caketext.translator.api.TranslatorApi} (CakeWiki). */
	public static final String WIKI = "wiki";

	/** Surfaces the translator can change (the tab list is collected but not translated). */
	public static final List<String> SURFACES = java.util.stream.Stream.concat(
			Surfaces.BUILT_IN.stream().filter(s -> !s.equals(Surfaces.TAB_LIST)), java.util.stream.Stream.of(WIKI)).toList();

	public boolean surface(String id) {
		return surfaces.getOrDefault(id, true);
	}

	public String languageToken() {
		return LANGUAGES.getOrDefault(language, ">>cmn_Hans<<");
	}

	static Path path() {
		return CakeText.dir().resolve("translator.json");
	}

	public static TranslatorConfig load() {
		TranslatorConfig c = Json.load(path(), TranslatorConfig.class, TranslatorConfig::new);
		if (!LANGUAGES.containsKey(c.language)) {
			c.language = "zh_cn";
		}
		if (c.markers == null) {
			c.markers = Markers.ALL;
		}
		TranslatorConfig d = new TranslatorConfig();
		if (c.engine == null) {
			c.engine = d.engine;
		}
		if (c.serverProtocol == null) {
			c.serverProtocol = d.serverProtocol;
		}
		if (c.serverApiKey == null) {
			c.serverApiKey = "";
		}
		if (c.onlineService == null) {
			c.onlineService = d.onlineService;
		}
		if (c.onlineApiKey == null) {
			c.onlineApiKey = "";
		}
		if (c.onlineRegion == null) {
			c.onlineRegion = "";
		}
		if (c.onlineUrl == null || c.onlineUrl.isBlank()) {
			c.onlineUrl = d.onlineUrl;
		}
		if (c.onlineCharLimit < 0) {
			c.onlineCharLimit = 0;
		}
		if (c.tooltipAlt == null) {
			c.tooltipAlt = d.tooltipAlt;
		}
		if (c.serverUrl == null || c.serverUrl.isBlank()) {
			c.serverUrl = d.serverUrl;
		}
		if (c.serverModel == null || c.serverModel.isBlank()) {
			c.serverModel = d.serverModel;
		}
		if (c.serverPrompt == null || !c.serverPrompt.contains("{text}")) {
			c.serverPrompt = d.serverPrompt;
		}
		if (c.serverRawTemplate == null) {
			c.serverRawTemplate = d.serverRawTemplate;
		}
		if (c.serverStop == null) {
			c.serverStop = d.serverStop;
		}
		if (c.ignoredItems == null) {
			c.ignoredItems = new ArrayList<>(ItemFilter.DEFAULT);
		}
		if (c.display == null) {
			c.display = Display.TRANSLATED;
		}
		if (c.chatDisplay == null) {
			c.chatDisplay = Display.ORIGINAL;
		}
		if (c.playerChatDisplay == null) {
			c.playerChatDisplay = Display.ORIGINAL;
		}
		if (c.surfaces == null) {
			c.surfaces = new LinkedHashMap<>();
		}
		for (String s : SURFACES) {
			c.surfaces.putIfAbsent(s, true);
		}
		return c;
	}

	public void save() {
		Json.save(path(), this);
	}
}
