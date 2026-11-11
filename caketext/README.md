# CakeText

Two client-side mods for **Fabric and NeoForge** on Minecraft **1.20.4** and **26.1.2** that translate what a server shows you: the chat, scoreboard, item tooltips, titles, boss bars, nameplates and menus. They were built for the [Monumenta](https://www.playmonumenta.com/) MMORPG and work on any server. Translations go from English into Simplified or Traditional Chinese.

| Mod | For | What it does |
|---|---|---|
| **CakeText Translator** | players | Replaces server text with translations from string files, and machine-translates whatever no file covers yet |
| **CakeText Collector** | translators | Records the text a server shows, with where it was seen, and exports it as Crowdin/Gettext files ready to translate |

Each mod works on its own, and you can install both. They share a small core library that is bundled inside each jar.

## CakeText Translator

- **String files first.** Human translations in `config/caketext/translations/<zh_cn|zh_tw>/` always win. Both our JSON format and the `.po` files Crowdin returns are read. An entry can be scoped to one place (a menu, a shard, an area or a box of blocks) when the same English text means different things.
- **Machine translation for the rest**, on your own PC or with an online service:
  - **Built in:** the opus-mt-en-zh model (Helsinki-NLP, Apache-2.0), run in pure Java. The mod doesn't download it: the settings have a **Model download page** button and an **Open model folder** button. Download its 4 files (about 312 MB) into that folder and press **Install from folder**. They're checked, converted to about 77 MB, and the originals deleted. It uses about 110 MB of memory. Each line takes 0.1–0.6 s and is remembered.
  - **Local server:** a bigger model you run yourself through Ollama, llama.cpp or LM Studio. The default is Hunyuan-MT-7B, which translates much better but needs a GPU with about 8 GB. The mod never downloads or ships it. Its license (Tencent Hunyuan Community License) doesn't cover the EU, UK or South Korea. With an API key, an online OpenAI-compatible service (OpenAI, DeepSeek…) works the same way.
  - **Online service:** Google Cloud Translation, DeepL, Microsoft Translator or LibreTranslate, with **your own API key** (shown as stars in the settings, stored only in `config/caketext/translator.json`). Good translations without a GPU, but every line to translate is sent to that company, which bills per character; a game session stops after 200,000 characters (`onlineCharLimit`, 0 = no limit).
- **You can see where each translation came from:** a book icon marks string translations and a chip marks machine ones. Hovering a translation in chat shows the original.
- **Display modes,** set separately for server chat, player chat and tooltips/HUD: translation only, both, or original with the translation on hover. Chat defaults to the original with the translation on hover, and tooltips and HUD to the translation.
- **Player chat** can be machine-translated too, but only by an engine on your PC or home network, never an online service. Only the message is translated: the channel, rank and name are never sent. These translations stay in memory and are never saved.
- **Alt in item tooltips:** by default tooltips are translated and holding **Alt** shows the original. Set "Alt in tooltips" to the other way round to keep tooltips in English and translate them only while Alt is held (nothing is sent to the engine until then), or turn it off.
- **Other mods' pages:** [CakeWiki](https://github.com/HarrytheCake/CakeWiki) shows wiki pages translated, with linked names (items, places, bosses) kept in English. Other mods can do the same through `io.github.harrythecake.caketext.translator.api.TranslatorApi` (plain Java types, so it can be called by reflection).
- Item names, signs, books and shulker boxes are left untranslated by default.
- Settings are in Mod Menu (NeoForge: Mods → Config) or `/cttranslate`.
  - Commands: `/cttranslate status`, `reload`, `test <text>`, `cache clear`, `exchange`, `model folder`, `model install`, `model delete`.

## CakeText Collector

- Collects chat and action bar messages, titles, scoreboard, boss bars, tab list, item tooltips, menu titles, nameplates and holograms.
- Each line becomes a template: numbers become `{~}`, player names `{n}` and styles `{c1}`, `{c2}`… This way, "Talk to 3 guards" and "Talk to 5 guards" are one string. Each string keeps up to 16 places it was seen: server, dimension, shard, area and block position. Shard and area come from the Unofficial Monumenta Mod, when it's installed.
- **Privacy:**
  - Player chat and player names are never stored.
  - Lines shaped like a player talking are dropped, including Monumenta's `<channel> [tag] Name » text`.
  - Nothing is uploaded; everything stays in `config/caketext/`.
  - Singleplayer worlds are skipped by default.
- **Exports for Crowdin:** `exports/crowdin/<server>/en_us/<surface>.pot` plus a `crowdin.yml`. They're written by `/ctcollect export` and, by default, automatically every 10 minutes, on leaving a server and on quitting. Every export contains every string, so Crowdin never drops a translated one.
- **Exchanger:** to convert between the JSON format and PO files, drop files into `config/caketext/exchange/` and run `/ctcollect exchange`.
- Settings are in Mod Menu (NeoForge: Mods → Config) or `/ctcollect`.
  - Commands: `/ctcollect status`, `save`, `export`, `exchange`, `on`, `off`.

Other mods can add context keys or text sources through the `caketext` entrypoint on Fabric, or `META-INF/services/io.github.harrythecake.caketext.core.module.CakeTextModule` on NeoForge (see `CakeTextModule`).

## Requirements

- Minecraft 1.20.4 or 26.1.2, Java 17+ (26.1.2: Java 25+)
- Fabric: Fabric Loader 0.15+ and [Fabric API](https://modrinth.com/mod/fabric-api); optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screens
- or NeoForge (20.4 for 1.20.4, 26.1 for 26.1.2); the settings screens are under Mods → Config

Tested in game: Fabric 1.20.4. The NeoForge and 26.1.2 jars are built and unit-tested only. There's no 1.21.1 version.

All interface text is in English, Simplified Chinese and Traditional Chinese.

## Download

Get the jars from [Releases](https://github.com/HarrytheCake/CakeText/releases). The Translator is also an optional mod in the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack, where it's off by default.

## Building

```bash
./gradlew build                          # needs JDK 25 to run Gradle; 1.20.4 compiles with Java 17, 26.1.2 with Java 25
./gradlew build -Pcake.versions=1.20.4   # one Minecraft version only
```

The build makes every jar in `build/libs/`: `caketext-<collector|translator>-<fabric|neoforge>-<minecraft>-<version>.jar`, plus `caketext-collector-<version>.jar` and `caketext-translator-<version>.jar` (the Fabric 1.20.4 jars under their old names). The shared core is bundled inside each. The build runs the tests. Tests that need the real model (they download it themselves) or a running LLM server are skipped unless given the `-Dcaketext.*` options described in [AGENTS.md](AGENTS.md), which also documents the layout (`core/`, `collector/`, `translator/`, each with `core/` and one folder per Minecraft version), formats, hooks and invariants.

GitHub Actions builds and tests every push. When `mod_version` in `gradle.properties` changes on `main`, it publishes release `v<version>` with all the jars and their SHA-256 in the release notes.

This repository is a mirror of the [`caketext/` folder](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz/caketext) of Monumenta-Cakedition, where development happens.

## License

- **CakeText Translator** (`translator/`): [Apache License 2.0](translator/LICENSE). See [translator/NOTICE](translator/NOTICE) for the projects its translation engine is modelled on.
- **CakeText Collector** (`collector/`) and the shared **core** (`core/`): [MIT](LICENSE).

- **Human translations** (CakeText string files and the `.po` files of the CakeText translation project): [CC BY-SA 4.0](LICENSE-translations). This covers the translators' work; the original English text belongs to its owners (for Monumenta, the Monumenta team).
