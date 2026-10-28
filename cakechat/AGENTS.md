# CakeChat — agent reference

Client-only Fabric mod (`cakechat`, package `io.github.harrythecake.cakechat`) for Minecraft **1.20.4**. It replaces three mods in the Monumenta Cakedition pack:

| Replaced | What we take over | Its license |
|---|---|---|
| ChatPlus 2.8.1 (ebicep) | windows/tabs, compact messages, find, copy, timestamps, scrolling, … | GPL-3.0 |
| More Chat History 1.3.0 | no line limit (it only raises vanilla's 100) | CC0 |
| Don't Clear Chat History 1.3 | chat kept across disconnects and server switches | – |

**ChatPlus is GPL-3.0: never copy or translate its code.** CakeChat is MIT and written from scratch; only the feature ideas and ChatPlus's *config format* (for the import) are used. The user decided on 2026-10-28 that CakeChat replaces ChatPlus, and that it can import a player's ChatPlus setup into CakeChat's own format.

The mod's standalone repo will be `HarrytheCake/CakeChat` (not created yet). Source of truth is this folder in the Monumenta-Cakedition repo; see the repo's `docs/NEW-MOD-GUIDE.md` for the steps to release and ship.

## Files

```
cakechat/
├── build.gradle            # Fabric Loom 1.10.5; Java release 17; YACL modImplementation (required, not bundled), Mod Menu compileOnly
├── gradle.properties       # MC 1.20.4, Yarn 1.20.4+build.3, Loader 0.16.9, Fabric API 0.97.3, YACL 3.6.6, mod_version
├── settings.gradle, gradlew(.bat), gradle/wrapper/   # Gradle 8.14.3, copied from updater/
├── .github/workflows/build.yml   # CI (active in the standalone repo): build + test, release v<mod_version> once per version
└── src/
    ├── main/resources/
    │   ├── fabric.mod.json          # client; depends fabric-api + yet_another_config_lib_v3 >=3.6.0; conflicts chatplus
    │   └── assets/cakechat/         # icon.png (placeholder, drawn by script), lang/{en_us,zh_cn,zh_tw}.json (same keys)
    ├── main/java/io/github/harrythecake/cakechat/
    │   ├── CakeChat.java        # entrypoint: loads config, /cakechat opens the settings
    │   ├── ChatConfig.java      # config/cakechat/config.json (Gson, plain Java; temp-file save; broken file → .broken)
    │   ├── SettingsScreen.java  # YACL screen; labels cakechat.config.<key>, tooltips <key>.desc
    │   └── ModMenuEntry.java    # Mod Menu configure button → SettingsScreen
    └── test/java/…/ChatConfigTest.java   # JUnit 5, run by `gradlew build`
```

Build: `JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` in `cakechat/`.

## Decisions (2026-10-28)

- **UI:** in-chat UI (windows, tabs, find bar, scrollbar) is our own code on vanilla widgets and mixins: no UI library helps inside `ChatHud`. The settings screen uses **YACL** (already in the pack, so players download nothing extra; it has colour pickers and list editors). No owo-lib/LibGui.
- **Ships on by default** in the pack (it replaces ChatPlus); ChatPlus, More Chat History and Don't Clear Chat History leave the pack in the same release.
- **History stays in memory only.** Unlimited by default (`history.maxMessages = 0`), kept across disconnects. Never written to disk: player chat is personal data.
- **Not doing** (they break the pack's rules): ChatPlus's online translator (sends chat to a web service; CakeText does translation locally), speech to text (bundles Vosk, uses the microphone), screenshot auto-upload, image previews from links (fetching any URL leaks the player's IP).

## Design

- **Where lines come in:** the public `ChatHud.addMessage(Text, MessageSignatureData, MessageIndicator)` (every chat line passes it once; `refresh()` uses another overload). CakeChat reads the line at **TAIL**, so it sees what CakeText Translator's `@ModifyVariable` at HEAD made of it (icons, hover translations stay).
- **CakeText bridge (planned):** CakeText swaps a chat line when a machine translation arrives late (`ChatHud.messages` + `refresh()`). CakeChat keeps its own store, so it will offer a small optional API that CakeText calls when CakeChat is installed (both mods are ours; CakeText must still work without CakeChat).
- **Monumenta chat** is `<channel> [tag] Name » text`: default tabs can split by channel.
- **Outgoing translation (lowest priority, user's request):** type a message, have CakeText translate it, then send. Needs Chinese → English: only CakeText's local-server engine (Hunyuan) can do that; opus-mt is English → Chinese only.

## Milestones

Status marks: `[x]` done, `[ ]` open.

- [x] **M0 skeleton:** builds, loads config, YACL settings screen, Mod Menu entry, `/cakechat`, 3 languages, tests.
- [ ] **M1 one window:** own message store (unlimited, kept on disconnect), CakeChat draws the chat instead of vanilla (size, text scale, opacity, line spacing, position), scrolling + scrollbar + scroll keys, timestamps, compact duplicates.
- [ ] **M2 windows and tabs:** several windows, tabs with regex rules (priority, always add, skip others, auto prefix), Monumenta channel tabs by default, unread counts, move/resize mode.
- [ ] **M3 find, copy, peek, hide:** find bar (contains/regex, ignore case, highlight matches), copy a message (plain or formatted), peek-chat and hide-chat keys.
- [ ] **M4 ChatPlus import:** read the newest `config/chatplus/chatplus-v*.json`, convert windows/tabs/patterns/compact/timestamps/keys; offered once when that file exists and no CakeChat setup has been made, and from a settings button. Never changes ChatPlus's files. Test fixtures are made up, never a player's real file.
- [ ] **Later:** highlight filters with sound, bookmarks (manual + auto), delete message, chat screenshot (clipboard/file only), hover highlight, send note, input box extras (character count, long-message auto-fill), player heads.
- [ ] **Last:** outgoing translation through CakeText.

## Invariants

- Nothing in the store or the config holds player chat on disk.
- Every UI string in en_us, zh_cn and zh_tw, same keys.
- `ChatConfig` stays plain Java (no Minecraft classes), so tests can load it.
- Test data uses made-up names (`qwertfox`, `Mock_Pl4yer`, …).
