# CakeChat — agent reference

Client-only Fabric mod (`cakechat`, package `io.github.harrythecake.cakechat`) for Minecraft **1.20.4**. It replaces three mods in the Monumenta Cakedition pack:

| Replaced | What we take over | Its license |
|---|---|---|
| ChatPlus 2.8.1 (ebicep) | windows/tabs, compact messages, find, copy, timestamps, scrolling, … | GPL-3.0 |
| More Chat History 1.3.0 | no line limit (it only raises vanilla's 100) | CC0 |
| Don't Clear Chat History 1.3 | chat kept across disconnects and server switches | – |

**ChatPlus is GPL-3.0: never copy or translate its code.** CakeChat is MIT and written from scratch; only the feature ideas and ChatPlus's *config format* (for the import) are used. The user decided on 2026-10-28 that CakeChat replaces ChatPlus, and that it can import a player's ChatPlus setup into CakeChat's own format.

The mod's standalone repo is `HarrytheCake/CakeChat` (remote `cakechat`, `git subtree push --prefix=cakechat cakechat main`; CI releases `v<mod_version>`). Source of truth is this folder in the Monumenta-Cakedition repo; never commit in the standalone repo.

**Releases:** v0.1.0 (2026-10-29), v0.2.0 (2026-10-29, M2–M4; in the pack since 2.4.0, replacing the three mods above), **v0.2.1** (2026-10-30, in the pack since 2.4.2): a left click just above the input line crashed the game (`NoSuchElementException` from `MessageHandler.process()`), because the delayed-message bar's hit area (`ChatView.inQueueBar`) was live while nothing was queued; it now answers only while the bar is drawn. Built and tested out of game; not yet tried in game.

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
    │   ├── cakechat.mixins.json     # ChatHudMixin, ChatScreenMixin, InGameHudMixin, MouseMixin
    │   └── assets/cakechat/         # icon.png (placeholder, drawn by script), lang/{en_us,zh_cn,zh_tw}.json (same keys)
    ├── main/java/io/github/harrythecake/cakechat/
    │   ├── CakeChat.java        # entrypoint: loads config, /cakechat opens the settings, /cakechat import, peek/hide keys, the one-time ChatPlus offer on join
    │   ├── ChatConfig.java      # config/cakechat/config.json (Gson, plain Java; temp-file save; broken file → .broken)
    │   ├── Compactor.java       # duplicate matcher + counter text (plain Java)
    │   ├── ChatEntry.java       # one message: content (replaceable), signature, indicator, tick, millis, compact key, count
    │   ├── ChatStore.java       # history (oldest first), compacting, history limit, mirroring vanilla's in-place swaps, routing into tabs
    │   ├── Channels.java        # tab → server channel: /join on a channel tab, /join of the player's own channel back on a tab without one (never /leave); hides replies to its own joins; remembers the own channel (plain Java)
    │   ├── TabRules.java        # which tabs take a message (priority, skip others, always add); routing text (plain Java)
    │   ├── ChatWindows.java     # all windows: views, rules, unread counts, mouse routing, move/resize/tab drags, send prefix
    │   ├── ChatPlusImport.java  # ChatPlus setup → CakeChat config: newest chatplus-v*.json by version, worthOffering, apply (plain Java, Gson)
    │   ├── Find.java            # find bar's search: plain/regex, match case, matches + ranges (plain Java)
    │   ├── FindBar.java         # find bar UI in the open chat (Ctrl+F): field + Aa/.* toggles + count; filters every window while searching
    │   ├── ChatCopy.java        # message → clipboard text: plain, § codes (hex as §x§R§R§G§G§B§B), JSON
    │   ├── CopyMenu.java        # right-click menu on a message in the open chat: copy text / codes / JSON, "Copied" flash
    │   ├── ChatView.java        # one window: its tab's wrapped lines, drawing (vanilla's layout + fade), tab bar, grip, queue bar, scroll, hover
    │   ├── SettingsScreen.java  # YACL screen; labels cakechat.config.<key>, tooltips <key>.desc
    │   ├── ModMenuEntry.java    # Mod Menu configure button → SettingsScreen
    │   └── mixin/
    │       ├── ChatHudMixin.java     # priority 500. addMessage HEAD → routing text, TAIL → store; refresh TAIL → swaps; clear HEAD; reset → re-wrap; render/scroll/resetScroll/getTextStyleAt/getIndicatorAt/getVisibleLineCount → windows; mouseClicked → false (queue bar is ours)
    │       ├── InGameHudMixin.java   # @WrapWithCondition on InGameHud.clear → ChatHud.clear(true): skipped when keepOnDisconnect
    │       ├── ChatScreenMixin.java  # mouseScrolled speed/invert; mouseClicked HEAD → windows; sendMessage: tab prefix around sendChatMessage; init TAIL → find bar widgets (input line focus unlocked); keyPressed HEAD → Ctrl+F, find field Enter/Esc/Up/Down, Esc closes the copy menu; render TAIL → find count + copy menu; removed HEAD → search and menu end
    │       └── MouseMixin.java       # @WrapOperation on Screen.mouseDragged/mouseReleased in Mouse's lambdas (method_1602/method_1605): open chat → windows first
    └── test/java/…/             # JUnit 5, run by `gradlew build`: ChatConfigTest, CompactorTest, TabRulesTest (incl. the shipped Monumenta tabs on made-up lines), ChannelsTest, ChatPlusImportTest (made-up 2.7 + 2.5 files), FindTest, LangTest (same keys in all 3 languages)
```

Build: `JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` in `cakechat/`.

## Decisions (2026-10-28)

- **UI:** in-chat UI (windows, tabs, find bar, scrollbar) is our own code on vanilla widgets and mixins: no UI library helps inside `ChatHud`. The settings screen uses **YACL** (already in the pack, so players download nothing extra; it has colour pickers and list editors). No owo-lib/LibGui.
- **Ships on by default** in the pack (it replaces ChatPlus); ChatPlus, More Chat History and Don't Clear Chat History leave the pack in the same release.
- **History stays in memory only.** Unlimited by default (`history.maxMessages = 0`), kept across disconnects. Never written to disk: player chat is personal data.
- **Not doing** (they break the pack's rules): ChatPlus's online translator (sends chat to a web service; CakeText does translation locally), speech to text (bundles Vosk, uses the microphone), screenshot auto-upload, image previews from links (fetching any URL leaks the player's IP).

## Design

- **Store vs vanilla:** vanilla's `ChatHud` is the way in and keeps its own 100 `messages` (other mods read and change them); CakeChat keeps its own `ChatStore` and draws it. Vanilla changes a line in place by `messages.set(i, …)` + `refresh()` (server deletion → "deleted" marker, CakeText's late translation, matched by `line.content() == original`). `ChatStore.onRefresh` keeps a shadow copy of vanilla's list and an identity map vanilla line → entry, and copies every replaced line's content into its entry. So CakeText 0.2.0 works unchanged; no API needed so far.
- **Windows (M2):** `config.windows` (first = main). Position (`x`, `bottom`) and size (`width`, `height`, `unfocusedHeight`) in GUI pixels; -1 = Minecraft's Chat Settings (the main window's default, so nobody's chat changes on update). Scale, opacity, background, line spacing stay Minecraft's, for all windows. Drawing transform: translate(x) → scale → translate(4), i.e. vanilla's box shifted. While the chat is open: a tab bar (12 px) under each box, a resize grip at the box's top-right; the delayed-message bar sits under the main window's tab bar and CakeChat handles its click (vanilla's `ChatHud.mouseClicked` returns false).
- **Mouse:** drags and releases are wrapped where `Mouse` calls `Screen.mouseDragged`/`mouseReleased` (`MouseMixin`), never added to `ChatScreen`: UMM's bundled njols-ui-framework adds those two methods to `ChatScreen` for its HUD editor, and Mixin keeps only one (fixed 2026-10-29; before, UMM's HUD drag in the open chat was skipped). CakeChat claims only a drag it started. `ChatScreen.mouseClicked` HEAD → `ChatWindows.mouseClicked` (topmost window first: queue bar, grip → resize, tab → select on release or move after 3 px, tab bar → move; a click in a box only makes it the active window and falls through to vanilla's text-click handling). Hover/click styles and indicators go to the window under the point; scroll and Page Up/Down to the window under the mouse, else the active one. Moves/resizes/tab selections save the file only (`saveConfigFile`); the settings screen's save rebuilds windows, rules and tab membership (`saveConfig` → `ChatWindows.rebuild` → `ChatStore.reroute`).
- **Tabs:** `TabRules` gives every tab of every window one index; `ChatEntry.tabs` is a BitSet of them, set on arrival and on reroute. Within a window: priority order (stable), pattern `find()` on the routing text, empty = all, `skipOthers` stops later tabs except `alwaysAdd` ones, invalid regex = matches nothing (the settings screen refuses invalid patterns anyway). Unread counts per tab (memory only), cleared on selecting it.
- **Routing text:** taken at `addMessage` HEAD (mixin priority 500, meant to run before CakeText's `@ModifyVariable` at HEAD, so tabs match the server's text even when CakeText shows the translation; verified in game with CakeText in translated mode 2026-10-29), private-use characters removed (CakeText's icons U+E100/U+E101), leading spaces stripped. A late CakeText swap never moves a message between tabs.
- **Default tabs (shipped in the jar, user's choice 2026-10-29):** main window = All, Global `^<g> ` (channel `g`), LFG `^<lfg> ` (`lfg`), Trade `^<tr> ` (`tr`), 中文 `^<zh> ` (`zh`, added 2026-10-29; config version 2 inserts it after Trade in existing default setups), Guild `^(?!<(?:lfg|tr|zh|es|mh)>)<.{2,5}.*>.*\[.{2,5}\]` (the user's own ChatPlus guild pattern, anchored; no channel: guild channel names differ per guild), Whisper `whispers? to` (the user's ChatPlus `dm` pattern, also catching "You whisper to"). All but All have `server` = `(^|\.)playmonumenta\.com(:\d+)?$`, so they're hidden (and never send commands) elsewhere. Names are translation keys `cakechat.tab.*` shown in the game's language (`CakeChat.tabName`) until the player renames them.
- **Channel switching (`Channels`, user's spec 2026-10-29, fixed after the first in-game test the same day):** clicking a tab with a `channel` sends `/join <channel>` (not again if CakeChat already switched there). Clicking a tab without one joins the **player's own channel** again: the one they last picked themselves (their typed `/join X`, or a "Joined channel X." the server sent that CakeChat didn't ask for, e.g. a clickable link). Unknown own channel → nothing is sent. **Never `/leave`:** on Monumenta it takes you out of the channel (the first version used it and the player lost their guild channel EDE and `tr`). The own channel is remembered per server address in `config.channelMemory` (lower-cased address → channel), so it survives restarts. Monumenta's replies "Joined channel X." to CakeChat's own /join (within 10 s) are cancelled at `addMessage` HEAD (unsigned lines only) and never stored; replies to the player's own commands show. **CakeChat sends commands only when the player clicks a tab: never on join, on a timer, or by itself** (relevant to server mod rules).
- **Send prefix:** `@WrapOperation` on `sendChatMessage` in `ChatScreen.sendMessage` (after the history was written, so history keeps what was typed): prefix "/x " → command `x <message>`, other prefixes are prepended; typed commands are untouched. Active window = last clicked.
- **Settings for windows/tabs:** one YACL category per window, one group per tab; removing a tab or window asks first on Minecraft's `ConfirmScreen` (Cancel returns to the same YACL screen, user's request 2026-10-29); buttons (add/remove tab, add/remove window, reset size) save pending edits (`finishOrSave`), change the config, `saveConfig()`, and reopen the screen (YACL can't add options to an open screen; the screen opens on its first category again).
- **Keep on disconnect:** `InGameHud.clear()` runs on disconnect **and** on `enterReconfiguration` (a 1.20.2+ proxy server switch: Monumenta shard switches). CakeChat skips its `ChatHud.clear(true)` call; F3+D calls `ChatHud.clear(false)` directly and still clears.
- **Compacting:** a repeat removes the earlier entry and arrives as a new one at the bottom with count + 1 (fade restarts). Key = `getString()` (or the JSON with styles). Blank lines never merge (Monumenta sends spacer lines). Timestamps and counters are added when wrapping, never stored, so changing the settings applies to old lines too.
- **Timestamps on hover** go only on runs without a hover of their own: in CakeText's ORIGINAL display every run carries the translation hover, so the time doesn't show there (use before/after).
- **Other mods touching chat in the pack** (checked 2026-10-28 via refmaps): UMM injects in `InGameHud.render` right after the `ChatHud.render` call (skills HUD) — unaffected by cancelling `ChatHud.render`. BetterF3, Language Reload and Xaero's World Map call `addMessage`. CIT Resewn hooks `ChatScreen.keyPressed`. ChatPlus, More Chat History, Don't Clear Chat History must be removed (ChatPlus also replaces drawing).
- **Where lines come in:** the public `ChatHud.addMessage(Text, MessageSignatureData, MessageIndicator)` (every chat line passes it once; `refresh()` uses another overload). CakeChat reads the line at **TAIL**, so it sees what CakeText Translator's `@ModifyVariable` at HEAD made of it (icons, hover translations stay).
- **CakeText bridge (planned):** CakeText swaps a chat line when a machine translation arrives late (`ChatHud.messages` + `refresh()`). CakeChat keeps its own store, so it will offer a small optional API that CakeText calls when CakeChat is installed (both mods are ours; CakeText must still work without CakeChat).
- **Monumenta chat** is `<channel> [tag] Name » text`: default tabs can split by channel.
- **Find (M3, Ctrl+F in the open chat):** a bar above the input line at the right (field, Aa = match case, .* = regex; toggles kept in `config.find`). While it has a valid search, every window shows only the messages whose `content.getString()` it finds (any window, the selected tab's messages), matches drawn yellow + underlined (styles otherwise kept, so clicks/hovers work); the count is over the whole history. Enter goes back to the input line, Esc / Ctrl+F in the field closes it; closing the chat ends the search (the text stays for the next Ctrl+F). The input line's focus lock is lifted so the field can take the typing; a click on the input line takes it back.
- **Copy (M3):** right-click a message in the open chat → menu: Copy text / Copy with colour codes (§) / Copy as JSON. Copies the message itself, never the timestamp, counter or find highlight. Any click closes the menu (one on an item copies first); Esc closes it.
- **Peek and hide keys (M3):** peek defaults to **Left Alt** (user's choice 2026-10-29; nothing in the pack's options.txt uses it, and CakeText's tooltip Alt only acts inside screens), hide is unbound (Controls → CakeChat). Peek (hold, no screen open): the windows draw as if open (full height, no fade, scrollbar), without tab bar or mouse. Hide (toggle): closed chat isn't drawn; messages still arrive; the open chat and peek always show it; the action bar says so; memory only (a restart shows the chat).
- **ChatPlus import (M4):** reads the newest `config/chatplus/chatplus-v<version>.json` (by version number), never writes ChatPlus's files. Two tab layouts: 2.7+ keeps a tab's rules in `settings[]` (several rules → one pattern `(?:a)|(?:b)`, name and the rest from the first), 2.5 has them on the tab. Patterns: match-all (`""`, `.*`, `(?s).*`) → `""`; anything else → `^(?:p)$`, because ChatPlus matches the whole text and CakeChat uses `find()`. Server patterns the same (2.7 `serverPattern.pattern`, 2.5 `serverTabPatterns[]`). Tabs with an invalid pattern are skipped; tabs with `formatted: true` (matched colour codes) are imported and listed for the player to check. Windows: `renderer` x/width/height, y = the box's bottom edge from the top (→ `bottom` = GUI height − y, clamped), `unfocusedHeight` = height × ChatPlus's fraction; disabled windows skipped; no windows in the file → CakeChat's stay. Settings: maxMessages (−1 → 0 = no limit), compact (format `&` codes stripped, `%VALUE%` → `{count}`; LATEST or else by time; lines; seconds; style), timestamps (the `{…}` part of the format as the pattern; HOVER/AFTER/else BEFORE), invert, scrollbar, find case + regex mode. Keys: hide key and peek key (unless it's ChatPlus's default P, so Left Alt stays), only without a modifier. Channels aren't in ChatPlus: imported tabs have none. **Offer:** on CakeChat's first start (no config yet) `chatPlusOffer` is set if `worthOffering` (more than one window, or any tab that isn't match-all; a lone All tab would replace the Monumenta tabs with less); the first join shows a chat line with [Import] (`/cakechat import`) and clears the flag. Also a settings category "ChatPlus" with a confirmed Import button (greyed out without a file). Result as toasts + a log line.
- **Outgoing translation (lowest priority, user's request):** type a message, have CakeText translate it, then send. Needs Chinese → English: only CakeText's local-server engine (Hunyuan) can do that; opus-mt is English → Chinese only.

## Milestones

Status marks: `[x]` done, `[ ]` open.

- [x] **M0 skeleton:** builds, loads config, YACL settings screen, Mod Menu entry, `/cakechat`, 3 languages, tests.
- [x] **M1 one window** (tested in game by the user 2026-10-28, test instance on Monumenta: look/fade/hover/clicks, >100 lines + red scrollbar, kept on shard switch + relog, F3+D clears, compacting, timestamps in all 3 placements, CakeText icons/hovers, UMM skill HUD): own message store (unlimited, kept on disconnect), CakeChat draws the chat instead of vanilla (size, text scale, opacity, line spacing, position), scrolling + scrollbar + scroll keys, timestamps, compact duplicates.
- [x] **M2 windows and tabs** (second in-game test passed 2026-10-29, all 15 steps: no /leave, own channel back and remembered, hidden join replies, 中文 tab, remove confirmations, routing incl. CakeText in translated mode, unread, prefix, second window move/resize, non-Monumenta servers, shard switch/relog, UMM HUD drag after the MouseMixin fix, with Ixeris): several windows, tabs with regex rules (priority, always add, skip others, auto prefix), unread counts, move by the tab bar, resize by the corner grip.
- [x] **M3 find, copy, peek, hide** (built and tested in game by the user 2026-10-29, all 11 steps; 35 tests): find bar (contains/regex, ignore case, highlight matches), copy a message (plain or formatted), peek-chat and hide-chat keys.
- [x] **M4 ChatPlus import** (built 2026-10-29, 42 tests; tested in game by the user 2026-10-29, all 7 steps: first-join offer + [Import], no second offer, settings button with cancel/confirm, `/cakechat import`, imported settings; real 2.7.0 file → 1 window, 4 tabs, nothing skipped; ChatPlus's files unchanged by checksum): read the newest `config/chatplus/chatplus-v*.json`, convert windows/tabs/patterns/compact/timestamps/keys; offered once when that file exists and no CakeChat setup has been made, and from a settings button. Never changes ChatPlus's files. Test fixtures are made up, never a player's real file.
- [ ] **Later:** highlight filters with sound, bookmarks (manual + auto), delete message, chat screenshot (clipboard/file only), hover highlight, send note, input box extras (character count, long-message auto-fill), player heads.
- [ ] **Last:** outgoing translation through CakeText.

## Invariants

- Commands go to the server only from a player's click on a tab (`/join`; never `/leave`) or a tab prefix on a message the player sends; never automatically.
- Nothing in the store or the config holds player chat on disk.
- Every UI string in en_us, zh_cn and zh_tw, same keys.
- `ChatConfig` stays plain Java (no Minecraft classes), so tests can load it.
- Test data uses made-up names (`qwertfox`, `Mock_Pl4yer`, …).
- A click area answers only while its element is drawn (the delayed-message bar: only with messages queued; vanilla's `process()` throws on an empty queue).
