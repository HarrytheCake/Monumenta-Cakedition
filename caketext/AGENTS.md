# CakeText — agent reference

Two client-only Fabric mods for Minecraft **1.20.4**, plus a shared library, in one Gradle build (`caketext/`, Fabric Loom 1.10.5, Yarn 1.20.4+build.3, Java 17 target):

| Project | Mod id | Jar | What it does |
|---|---|---|---|
| `core` | `caketext-core` | bundled inside both mods (jar-in-jar) | text templates, strings files, game context, module API, settings screen base |
| `collector` | `caketext-collector` | `caketext-collector-0.3.1.jar` | records the text a server shows, with where it was seen, into local strings files |
| `translator` | `caketext-translator` | `caketext-translator-0.3.1.jar` | replaces server text with translations from strings files; strings no file has go to an engine (local opus-mt model, local LLM server, or an online service with the player's key) |

Either mod works alone. With both installed, Fabric loads one copy of core.

Package root: `io.github.harrythecake.caketext`. Licenses: `translator/` Apache-2.0 (`translator/LICENSE` + `NOTICE`), `core/` and `collector/` MIT (root `LICENSE`); each jar carries its file (`LICENSE_caketext-<module>`), and `fabric.mod.json` has a matching `license` field. Everything both mods write lives in `config/caketext/`.

## Build and test

```bash
cd caketext
JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build          # jars in */build/libs; runs the tests
JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build -x test  # jars only
```

**CI and releases:** `.github/workflows/build.yml` (GitHub only runs it in the standalone `HarrytheCake/CakeText` repo, where this folder is the root) builds and tests every push and pull request on JDK 21. A push to `main` whose `mod_version` has no `v<mod_version>` release yet publishes one with `caketext-collector-<v>.jar` and `caketext-translator-<v>.jar` (core is inside both). No built jar is committed anywhere. To release: bump `mod_version`, commit, `git subtree push --prefix=caketext caketext main` from the repo root, wait for the release, then (for the pack, which ships only the Translator, optional and off) `python tools/packwiz_sync.py github mods HarrytheCake/CakeText v<v> caketext-translator`, commit, `tools/push_all.py`.

Tests run inside Fabric's class loader (`fabric-loader-junit`, client side), so mixins are applied. Model tests are skipped unless given files:

| Test | Needs | Proves |
|---|---|---|
| core `TextTemplateTest`, `StringsFileTest` | – | templates round-trip (numbers, names, styles, braces); raw/gzip files |
| core `ExchangerTest` | – | JSON ↔ PO round trip (scope ↔ msgctxt, comments ↔ contexts/counts), reading Crowdin-style PO (fuzzy, obsolete, continuation lines), Crowdin layout + `crowdin.yml`, folder converter, item filter patterns |
| translator `CrowdinAndTooltipTest` | – | a Crowdin `.po` in `translations/<lang>/` is used; item names untouched by default; signs/books/shulker boxes not translated |
| core `PlayerChatTest` | – | where a player's message starts (tags, rank, name, separator); server lines aren't player chat; styled split |
| translator `OnlineEngineTest` | – | exact Google / DeepL / Microsoft / LibreTranslate requests against a fake service on 127.0.0.1 (key in a header, DeepL free host, region, targets), 401/403 stops, 429 pauses, session character limit, `ServerEngine.isLocal` |
| translator `TooltipAltAndPartsTest` | – | Alt in tooltips both ways round and off; `translateParts` (kept parts move with the translation, numbers through `{~}`, misses, surface off) |
| translator `ServerEngineTest` | (real-server case: `caketext.serverUrl`, `caketext.serverModel`, `caketext.serverProtocol`) | exact Ollama raw / OpenAI requests, response parsing and clean-up, model/server checks |
| translator `MachineCacheTest` | – | machine results saved per engine and reloaded; another engine doesn't reuse them; player chat never reaches disk |
| translator `ChatLineTest` | – | chat display modes, hover cards, player chat keeps prefix/name, item-link hovers survive |
| collector `PrivacyFilterTest` | – | player-chat shapes are rejected, server messages kept |
| `MixinsApplyTest` (both mods) | – | every mixin target and injection point resolves |
| translator `DictionaryTest` | – | scope priority, `box`, whitespace-forgiving lookup, human beats machine |
| translator `MachineTranslatorTest` | (real-model case: `caketext.workDir`) | template ↔ model mapping, fallbacks, icons, Chinese punctuation |
| translator `mt.OpusMtReferenceTest` | `caketext.hfModel`, `caketext.hfReference`, `caketext.workDir` | Java port vs Hugging Face |
| translator `ModelInstallTest` | `-Dcaketext.downloadTest=true` (the test downloads 312 MB, playing the player) | missing file → MISSING with the name; all files → check → convert → delete originals → load → translate |

`caketext.hfModel` = a downloaded `Helsinki-NLP/opus-mt-en-zh` folder (with `flax_model.msgpack`); `caketext.hfReference` = JSON from a Hugging Face script (token ids, greedy and 4-beam outputs per sentence); `caketext.workDir` = where converted models are cached. Pass them as `-D` options to `gradlew`.

## Core

- `template/TextTemplate`: a styled `Text` → template string, and a translated template → `Text` with the original styles and values.
  - `{~}` number (`\d+([.,]\d+)*`), `{n}` player name (names come from `PlayerNames`, the tab list + own name, and from the text's style: a player `SHOW_ENTITY` hover, a name-shaped insertion, or a `/msg|tell|w|whisper|m|pm|dm <name>` click; `TextTemplate.styleName`), `{c1}`… the Nth distinct style (only when a line has more than one), `{{` `}}` literal braces. Translations may use `{~2}`, `{n1}` to reorder.
  - `hasWords()`: a letter outside placeholders; lines without one are never collected or translated.
- `template/Translated`: weak identity set of `Text` the translator produced; the collector skips them.
- `template/PlayerChat.bodyStart(line)`: index where a player's message starts, or -1. Checks, in order: a tab-list name with a separator; the `»` form (Monumenta); the vanilla `<name> ` form; whispers. A tab-list name with only tags/one rank word/icons before it and a separator after (`:` `»` `>` `|` `whispers to you:`), or any name in the vanilla `<name> ` and `From/To name: ` forms. NPC lines like `[Quest] Aledar: …` are not matched (NPC names aren't in the tab list).
- `TextTemplate` reads old-style `§` codes inside literal text as styles (a colour resets earlier § modifiers, `§r` returns to the component's style, unknown codes stay text). `itemStyles()` = styles with a SHOW_ITEM hover (chat item links).
- `PlayerNames.refresh` always includes the session's own name; both mods also refresh on JOIN.
- `template/TextUtil`: split a styled line at an index, add a hover where a run has none (item links keep theirs), recolour (fonts kept).
- `strings/StringsFile` + `StringEntry`: the one file format for both mods (below). `.json.gz` = same JSON gzipped; readers detect gzip by magic bytes.
- `context/GameContext`: surface, server (host, no port; `singleplayer`), dimension, shard, area, block pos. `matches(scope)` for scoped translations; unknown scope keys never match.
- `context/ContextRegistry`: extra context keys from modules (`shard`, `area`). A provider that throws is removed.
- `module/CakeTextModule`: entrypoint `caketext` in any mod's `fabric.mod.json`. `registerContext()` (both mods) and `registerSources(TextSink)` (collector). `UmmModule` (built in): shard = `Locations.getShortShard()`, area = `UnofficialMonumentaModClient.locations.getLocation(x, z, shard)` of UMM 1.10, by reflection.
- `ui/OptionsScreen`: two-column settings screen with status lines, toggles, cycles and tooltips.

## Crowdin (Gettext PO) and the exchanger

- `strings/PoFile`: Gettext PO reader/writer (header, `#.` comments, `#:`, flags, `msgctxt`, multi-line strings, plural forms read as the first form, obsolete `#~` skipped).
- `strings/Exchanger`: our JSON ↔ PO.
  - `src`↔`msgid`, `dst`↔`msgstr` (fuzzy = not translated), `scope`↔`msgctxt` as sorted `key=value;…`.
  - `note`, `count`/`first`/`last`, `contexts`, `engine` ↔ `#.` lines in a fixed form (`note: …`, `seen N times, first …, last …`, `where: surface | server | dimension | shard | area | x y z`, `engine: …`), plus a `placeholders:` hint, which is ignored on read.
  - Header `Language` ↔ `lang`; `X-CakeText-Server/Source` ↔ `server`/`source`.
- **Crowdin layout** (`Exchanger.writeCrowdin`): `<dir>/en_us/<surface>.pot` + `<dir>/crowdin.yml`, written once and never overwritten. The yml uses source `/en_us/*.pot`, translation `/translations/%locale_with_underscore%/%file_name%.po`, and maps zh-CN → zh_cn, zh-TW → zh_tw. A string goes in the file of the surface it was first seen on, so it never moves; the order is first-seen, so new exports only add lines.
- **The collector's exports are Crowdin layout:** `exports/crowdin/<server>/`, manual (`/ctcollect export`) and auto (every 10 min / leave / quit, only when something changed). They always contain every string, because a Crowdin source missing a string drops it, translation and all. The old dated JSON exports and the "untranslated only" option are gone.
- **The translator reads `.po`** in `translations/<lang>/` (as Crowdin returns them) next to `.json`/`.json.gz`.
- **Exchanger, when needed:**
  - In game: drop files into `config/caketext/exchange/`, then run `/ctcollect exchange` or `/cttranslate exchange` (or the collector's button). Results go to `exchange/converted/`.
  - Command line: `./gradlew :core:exchange --args="<files or folders> --out <folder>"`.
  - Direction: `.json`/`.json.gz` → `<name>/<surface>.po` (`.pot` if nothing is translated); `.po`/`.pot` → `<name>.json`.
- The collector's own store (`collected/<server>.json.gz`) stays our JSON: it's the source everything is made from.

## Strings file format

```json
{ "format": "caketext/strings", "version": 1, "lang": "zh_cn", "source": "en_us", "server": "…",
  "entries": [
    { "src": "{c1}[Quest] {c2}Talk to {~} guards", "dst": "{c1}[任务] {c2}和 {~} 名守卫交谈" },
    { "src": "Back", "dst": "向后", "scope": { "surface": "nameplate", "box": "100 60 100 110 70 110" } }
  ] }
```

- Collector fields: `contexts` (up to `maxContexts` distinct places: surface, server, dimension, shard, area, `pos`), `count`, `first`, `last`. The translator ignores them.
- `scope` keys: `surface`, `server`, `dimension`, `shard`, `area` (exact, case-insensitive) and `box` (`x1 y1 z1 x2 y2 z2`, inclusive). All keys must match; most keys wins.
- `engine`: absent/`human` for people, else the model label. A flat `{"src": "dst"}` object is also accepted.

## Collector

- Sources: chat and action bar (`ALLOW_GAME`, sees the original before any mod changes it), titles/subtitles (mixin, priority 900), item tooltips (default phase), menu titles (non-translatable screen titles), and once a second: scoreboard sidebar (team slot first), boss bars, tab header/footer, nameplates + text displays within `nameplateRange`. Modules can add sources.
- Privacy: signed player chat is never read (only game messages); player names become `{n}`; `PrivacyFilter` drops chat lines shaped like a player talking (template patterns plus `PlayerChat.bodyStart` on the original); singleplayer is off by default. Nothing is uploaded. Names of players not in the tab list are caught when the server marks them (entity hover, insertion, /msg click); plain unmarked names of such players still get through (known gap). Monumenta player chat looks like `<lfg> [AB] Name » text` (channel, guild tag, name, »); speakers are mostly not in the tab list, so `PlayerChat` also matches that shape by itself. Lines containing Chinese characters are skipped (not English source: vanilla text in a Chinese game, other mods' messages). On load, `Store` purges stored lines that fail these checks (`PrivacyFilter.rejectStored`). An older build collected ~900 such lines in the test instance; the purge removes them.
- Auto-export (`autoExport`, default on): rewrites the Crowdin files in `exports/crowdin/<server>/` every `autoExportMinutes` (10), when leaving or switching servers and on quit, only if something new was recorded. Manual `/ctcollect export` writes the same files.
- `Store`: one entry per template; a repeat on the same surface within 5 s doesn't count; contexts merge by surface/server/dimension/shard/area/16-block cell (exact block for nameplates). Saved every `saveSeconds` and on disconnect/exit to `collected/<server>.json.gz`, atomically.
- Export (`/ctcollect export` or the button): the Crowdin layout in `exports/crowdin/<server>/` (see Crowdin above), every string, optionally without positions. Our JSON is available through the exchanger.
- Settings `collector.json`; screen via Mod Menu or `/ctcollect`. Commands: `status`, `save`, `export`, `on`, `off`.

## Translator

- `Dictionary`: every strings file under `translations/<lang>/` (recursive). Lookup: best matching scoped human entry → unscoped human → the same with whitespace collapsed → machine cache (`translations/<lang>/_machine/opus-mt-en-zh-int8.json.gz`).
- Where text is replaced:
  - chat: `ChatHudMixin` on the public `ChatHud.addMessage(Text, MessageSignatureData, MessageIndicator)`, which every chat line passes once (server text, player chat, signed or not; `refresh()` uses another overload). `TranslationService.chatLine` decides:
    - player chat (`PlayerChat.bodyStart` ≥ 0, setting `playerChat`, default on): only the message part is translated (human dictionary, else the model); tags, rank and name are never sent. Results stay in memory (`MachineTranslator` chat cache, 1000 lines), never on disk. Display `playerChatDisplay`, default ORIGINAL.
    - a signed line that can't be split is left alone; everything else is server text, display `display`, default TRANSLATED.
    - Display modes in chat: TRANSLATED (hover the text → original), BOTH (translation, then the original in grey on the next line), ORIGINAL (hover → translation). The hover card names the source; it goes on every run without its own hover, and on the icon.
  - action bar (`MODIFY_GAME`, overlay only)
  - tooltips (a phase after default; BOTH/ORIGINAL add the other version as a grey line; plus a footer line naming the sources)
  - titles/subtitles (`@ModifyVariable` on `setTitle`/`setSubtitle`)
  - scoreboard (`getDisplayName()` in `renderScoreboardSidebar`, `Team.decorateName` in lambda `method_55439`)
  - boss bars (`ClientBossBar.getName()` in `BossBarHud.render`, at draw time)
  - nameplates (`renderLabelIfPresent`, players skipped)
  - text displays (`getText()` in `getLerpedRenderState`)
  - menu titles (`ScreenEvents.AFTER_INIT` + `@Mutable` title accessor)
- Markers: a font `caketext_translator:icons` with `\uE100` (book = string translation) and `\uE101` (chip = model). Chat: icon in front. Other HUD text: icon after (no hover possible there). Setting `markers`: all / machine only / off; the hover on chat text stays either way. HUD text (scoreboard, titles, boss bars, nameplates, menus, action bar) shows the translation unless `display` is ORIGINAL.
- A miss is queued for the model if it's installed, enabled and the line looks English (Latin letters, no Han). Chat lines are swapped in place when the model finishes (`ChatHud.messages` + `refresh()`); other surfaces re-render every frame, so they update by themselves; text displays only when re-created.
- **Alt in tooltips** (`tooltipAlt`): `ORIGINAL_ON_ALT` (default: translated per `display`, Alt held = the untranslated tooltip), `TRANSLATION_ON_ALT` (untranslated; Alt held = translated as TRANSLATED; nothing is queued for the engine until Alt is held, which saves an online service's characters), `OFF`. A grey hint line says what Alt does (only with `tooltipFooter`, never while Alt is held). `translateTooltip(stack, lines, alt)` is the testable overload.
- Item tooltips: the first line (item name) is not translated unless `translateItemNames` (default off). Items matching `ignoredItems` (`core/template/ItemFilter`, id globs, default `*sign`, `*book`, `*shulker_box`; edited in the Where-to-translate screen) are never translated. The collector still collects them (user's choice, 2026-10-27).
- Display settings: `chatDisplay` (server text in chat, default ORIGINAL), `playerChatDisplay` (default ORIGINAL), `display` (tooltips and HUD, default TRANSLATED; HUD shows nothing translated when ORIGINAL).
- Settings `translator.json` (language `zh_cn`/`zh_tw`, display, chat display, player chat on/off and its display, machine on/off, quality `best`/`fast`, markers, tooltip footer, per-surface). Screen via Mod Menu or `/cttranslate`. Commands: `status`, `reload`, `test <text>`, `cache clear`, `exchange`, `model folder`, `model install`, `model delete`.

### Engines (`TranslationEngine`)

Setting `engine`: `OPUS_MT` (built in, below), `SERVER` (`ServerEngine`, an LLM the player runs, e.g. Hunyuan-MT-7B in Ollama) or `ONLINE` (`OnlineEngine`, 0.3.0). All get the same chunks from `MachineTranslator` (stand-in numbers for placeholders) and all are checked by the same placeholder rules. `TranslationEngine.local()`: the engine runs on this PC or a private network; **player chat is only submitted when the active engine is local** (`TranslationService.playerChat`). The engine screen shows only the active engine's settings (OptionsScreen doesn't scroll) and rebuilds on a switch; key fields render as stars.

- `OnlineEngine` (user's request 2026-10-29), `onlineService`: `GOOGLE` (Cloud Translation v2, key in the `X-goog-api-key` header, target zh-CN/zh-TW), `DEEPL` (`DeepL-Auth-Key` header; keys ending `:fx` → api-free.deepl.com; ZH-HANS/ZH-HANT), `MICROSOFT` (v3, `Ocp-Apim-Subscription-Key` + optional `-Region` = `onlineRegion`; zh-Hans/zh-Hant), `LIBRETRANSLATE` (`onlineUrl`, key optional in the body). `onlineApiKey` lives only in `translator.json`. Never `local()`. Ready as soon as it's configured (no health check: asking costs requests). 401/403 → stopped until the settings change (`use`); 429/456 → paused 60 s; `onlineCharLimit` (default 200,000, 0 = none) characters per game session, then stopped. Requests/answers are static (`request`, `parse`) and tested against a fake service; **not tried against the real services** (needs the player's keys). Cache file `_machine/online-<service>.json.gz`.
- `ServerEngine.local()` = `isLocal(serverUrl)`: localhost, 127/8, 10/8, 172.16/12, 192.168/16, 169.254/16, ::1, fc/fd/fe80, `.local`. `serverApiKey` (OpenAI protocol only) is sent as `Authorization: Bearer` on the model check and on every request, so online OpenAI-compatible services work; such a URL isn't local, so no player chat.

- `ServerEngine`, protocol `OLLAMA` (default, `http://127.0.0.1:11434`): `/api/generate` with `raw: true` and the model's own chat format around the prompt (`serverRawTemplate`, default Hunyuan's `<|startoftext|>{prompt}<|extra_0|>` from its tokenizer_config), because Ollama doesn't know Hunyuan's template. `OPENAI`: `/v1/chat/completions` (llama.cpp `llama-server --jinja`, LM Studio), the server applies the template.
- Defaults from Tencent's model card: prompt `把下面的文本翻译成{language}，不要额外解释。\n\n{text}` ({language} = 中文 / 繁体中文), no system prompt, top_k 20, top_p 0.6, repetition penalty 1.05, temperature 0.7, stops `<|eos|>` `<|extra_0|>` `<|startoftext|>`. Default model `hf.co/mradermacher/Hunyuan-MT-7B-GGUF:Q4_K_M` (4.31 GB).
- Health check every 15 s until the server answers and lists the model, then every minute. `clean()` drops stop tokens, explanations on extra lines, bracketed asides (`（或译为…）`, `(or …)`, notes), the source's English echoed in brackets, and quote marks the source didn't have (`dropAsides`).
- An engine failure (server down, timeout) doesn't mark the line failed; it's retried when seen again. A bad output (placeholders lost) does.
- License: Hunyuan-MT is under the Tencent Hunyuan Community License (not valid in the EU, UK, South Korea). The mod never downloads or ships it; the tooltip says so.
- Live-tested 2026-10-27 with Ollama 0.40.11 + `hf.co/mradermacher/Hunyuan-MT-7B-GGUF:Q4_K_M` on an RTX 4060 Laptop (8 GB): 100% GPU, ~5.3 GB VRAM, ~46–52 tokens/s, 0.17–0.6 s per line warm (the first call after loading is slow). All placeholders kept; far better than opus-mt on game terms.
- Weakness: each colour run is translated alone, so a half sentence can come back wordy. For LLM engines, sending the whole line with `{cN}` markers (and checking they return) would give better context.

### Machine cache

`translations/<lang>/_machine/<engine id>.json.gz`, one per engine (`opus-mt-en-zh-int8`, `server-<model>`); only the active engine's is used, so switching to a better engine re-translates instead of reusing. Saved 30 s after the previous save while results come in, after 30 s without results, on reload, on leaving a server and on quitting. Player chat results stay in memory (1000 lines).

### Local model (`translator/mt`, pure Java, no native code)

- Model: `Helsinki-NLP/opus-mt-en-zh` (Apache-2.0), pinned revision `408d9bc4…`. **The mod downloads nothing (0.2.0+, for mod-site rules).** The engine screen has [Model download page] (`ModelManager.PAGE_URL`, the Hugging Face file list at the pinned revision), [Open model folder] (`importDir()` = `config/caketext/models/opus-mt-en-zh/`) and [Install from folder]; `/cttranslate model folder` opens the folder and prints the page as a link. The player downloads `config.json`, `vocab.json`, `source.spm`, `flax_model.msgpack` (312 MB) there. Install checks size + SHA-256 of each (state CHECKING; a missing file → MISSING with `model.incomplete` naming it; a wrong one → FAILED `model.bad_file`), converts to `models/opus-mt-en-zh-int8/model.ctm` (77 MB: int8 matrices with a scale per row, float biases/norms) + `source.spm`, and deletes the four originals (only those; a file Windows still has mapped is deleted on the next load, as is the `download/` folder of 0.1.0). Measured: check ~2.5 s, convert ~1 s, load ~0.2 s.
- `Msgpack` reads the Flax checkpoint (plain data, no pickle). `SentencePiece` parses the `.spm` protobuf and applies its precompiled `nmt_nfkc` charsmap (darts-clone trie) + unigram Viterbi. `Marian` = HF `MarianMTModel` (post-norm, swish, sinusoidal positions with sin|cos halves, scaled tied embeddings, `final_logits_bias`) with HF's greedy and beam search (log-softmax, pad banned, renormalized, length penalty 1, early_stopping false).
- Verified against transformers 4.49: tokenizer 16/16 identical ids; float32 weights 16/16 identical greedy **and** 4-beam token ids; int8 weights 13/16 identical outputs (the rest are synonyms).
- Speed (28-core desktop, 4 worker threads at min priority): `best` (int8 weights, float activations, 4 beams) ~0.6 s per line; `fast` (int8 activations too, greedy) ~0.08 s per line but rougher (e.g. "Back" → "后退( E)"). Heap ~110 MB once loaded.
- `MachineTranslator`: newest first, queue cap 300; bad outputs are not retried this session, engine errors are. Runs whose style is in the `keep` set (item links, unless `translateItemNames`) are copied unchanged. `clearCache()` (`/cttranslate cache clear`, engine screen button) drops the active engine's cache file. Machine entries whose source has `§` are ignored on load (made before § codes were styles). A template is split at `{cN}`; in each run `{~}`/`{n}` become stand-ins 7301, 7302… which must come back exactly once, otherwise the text between placeholders is translated separately. Private-use characters (resource-pack icons) are never sent. `ChineseText.tidy`: full-width punctuation next to Han, stray spaces removed.
- Model quality is limited: "Total Damage" → 损失总额, "[Quest]" → [查询]. That's why human strings always win and machine lines carry the chip icon.

### API for other mods (`api/TranslatorApi`, version 1, 0.3.0)

Plain Java types only, so callers use reflection and don't depend on CakeText (CakeWiki's `CakeTextBridge`). Game thread. `version()`, `active(surface)`, `language()`, `changes()` (an `AtomicLong` in `TranslationService`, bumped on every engine result and on reload: poll it and redraw), `pending()`, `translate(surface, String[] parts, boolean[] keep)` → `Object[]` of Strings (translated text) and Integers (index of a kept part, wherever the translation puts it), or null = show the original (queued if the engine can take it). `TranslationService.translateParts` builds the template: kept parts → `{n}` (stand-ins, never sent to an engine), numbers → `{~}` (so `+12% Attack Speed` hits a human `+{~}% Attack Speed`), literals escaped; dictionary lookup with `GameContext.of(surface, null)`; style markers in a human `dst` are dropped. Surface `wiki` (`TranslatorConfig.WIKI`, in `SURFACES`, on by default, lang key `caketext.surface.wiki` in the translator's files). Wiki results go into the same machine cache as server text.

## Invariants

- The collector must never store player chat or player names, and never upload anything.
- Player chat translations are never written to disk, and the prefix/name part of a player line is never sent to the model.
- The translator must never throw into the game: `TranslatorMod.translate` returns the original on any error.
- The translator downloads nothing. Its network use is the LLM server the player configures (default `127.0.0.1`) and, only when the player picks it and enters their own key, an online translation service (0.3.0).
- Player chat goes only to an engine whose `local()` is true (this PC or a private network), never to an online service.
- Mixins only touch client classes; text displays check `world.isClient`.

## Status (2026-10-27)

- Proven out of game: everything in the test table, including a real install from Hugging Face.
- **In-game test on Monumenta passed** (2026-10-27, test-plan steps 1–9: loading, Hunyuan, chat display/hover, UMM shard, Crowdin export, purge). It led to the privacy, § code, own-name, item-link and clean-up fixes now covered by the tests. Still to run in game: the exchanger and the machine cache without Ollama.
- Not built: remote sync of translation files (like the updater does for the pack), a shared translation repo. Other LLMs would work through the server engine with their own prompt/template.
