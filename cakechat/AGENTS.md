# CakeChat — agent reference

Client-only mod (`cakechat`, package `io.github.harrythecake.cakechat`) for **Fabric and NeoForge** on Minecraft **1.20.4, 1.21.1 and 26.1.2** (the pack ships Fabric 1.20.4). It replaces three mods in the Monumenta Cakedition pack:

| Replaced | What we take over | Its license |
|---|---|---|
| ChatPlus 2.8.1 (ebicep) | windows/tabs, compact messages, find, copy, timestamps, scrolling, … | GPL-3.0 |
| More Chat History 1.3.0 | no line limit (it only raises vanilla's 100) | CC0 |
| Don't Clear Chat History 1.3 | chat kept across disconnects and server switches | – |

**ChatPlus is GPL-3.0: never copy or translate its code.** CakeChat is MIT and written from scratch; only the feature ideas and ChatPlus's *config format* (for the import) are used. The user decided on 2026-10-28 that CakeChat replaces ChatPlus, and that a player's ChatPlus setup can be imported into CakeChat's own format. **Since 0.3.0 that import lives in Cakedition Updater** (1.7.0, `ChatPlusImport` + `MoveIn`, user 2026-11-03): it writes into `config/cakechat/config.json` after the game closes, so CakeChat's config field names are an interface: keep them stable or update CakeUpdater too.

The mod's standalone repo is `HarrytheCake/CakeChat` (remote `cakechat`, `git subtree push --prefix=cakechat cakechat main`; CI releases `v<mod_version>`). Source of truth is this folder in the Monumenta-Cakedition repo; never commit in the standalone repo.

**Releases:** v0.1.0 (2026-10-29), v0.2.0 (2026-10-29, M2–M4; in the pack since 2.4.0, replacing the three mods above), **v0.2.1** (2026-10-30, in the pack since 2.4.2): a left click just above the input line crashed the game (`NoSuchElementException` from `MessageHandler.process()`), because the delayed-message bar's hit area (`ChatView.inQueueBar`) was live while nothing was queued; it now answers only while the bar is drawn. Built and tested out of game; not yet tried in game.

**Unreleased (local, 2026-11-07; `mod_version` not bumped):** window moving/resizing locked by default (`windowEditing`, see Design → Windows), toggled in the settings (General) or with `/cakechat edit` (works in game: user, on the pre-port 1.20.4 build); the tab reset fix. **Multiloader port** (branch `cakechat-port`, 2026-11-07): Fabric + NeoForge on 1.20.4, 1.21.1 and 26.1.2, see "Multiloader status" at the end.

- **CakeUI HUD editor (2026-11-07, CakeUI API 3, optional):** `CakeUIBridge` (every version's common) reaches CakeUI only by reflection (no build or runtime dependency): at the first client tick, if `io.github.harrythecake.cakeui.api.CakeUIApi.VERSION >= 3`, it registers a `MovableSource` (java.lang.reflect.Proxy) whose `Movable`s are the chat windows (`~cakechat:window<n>`; box = `boxX/boxTop/boxWidth/boxHeight(true)`, place = `resize` then `move`, resizable, save = `saveConfigFile`), so CakeUI's editor moves and resizes them while `config.json` stays the only store. `config.editHudButton` (default true; General category) shows an "Edit HUD" button at the right end of the main window's tab row (only with CakeUI 3+); a click closes the chat and calls `openHudEditor(null)`. Self-test step 165 checks the bridge when CakeUI is in the run: copy `cakeui-fabric-1.20.4-<version>.jar` (a CakeUI 3+ build) into `1.20.4/fabric/run/mods/` (Fabric remaps it at runtime in dev; Gradle `modLocalRuntime`/`modRuntimeOnly` of the jar did not get it loaded). Fabric 1.20.4 checked in the dev client (PASS); other targets build + unit tests only.

## Files

Cake multiloader kit layout (`tools/multiloader/README.md` in the Monumenta-Cakedition repo). Mojang names everywhere (the Design notes below still say `ChatHud`/`InGameHud`/`Mouse`/`PlayerListHud`/`Text`/`MessageIndicator` in places: read `ChatComponent`/`Gui`/`MouseHandler`/`PlayerTabOverlay`/`Component`/`GuiMessageTag`).

```
cakechat/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/   # the kit (apply_kit.py copies it; don't edit here)
├── gradle.properties       # mod_id, mod_name, mod_version, mod_group (+ Gradle memory settings)
├── .github/workflows/build.yml   # mirror CI: JDK 17+21+25, ./gradlew build, a push to main with an unreleased
│                                 #   mod_version publishes v<version> with all 6 jars + cakechat-<version>.jar (= Fabric 1.20.4)
├── core/                   # plain Java, no Minecraft imports; compiled into every jar
│   ├── src/main/java/io/github/harrythecake/cakechat/
│   │   ├── ChatConfig.java      # config/cakechat/config.json (Gson; temp-file save; broken file → .broken); Tab.copy/resetTarget
│   │   ├── Compactor.java       # duplicate matcher + counter text
│   │   ├── Channels.java        # tab → server channel: /join on a channel tab, /join of the player's own channel back on a tab
│   │   │                        #   without one (never /leave); hides replies to its own joins; remembers the own channel
│   │   ├── TabRules.java        # which tabs take a message (priority, skip others, always add); routing text
│   │   ├── GuildTag.java        # the player's guild tag from the tab list lines ({guild} channel)
│   │   ├── Find.java            # find bar's search: plain/regex, match case, matches + ranges
│   │   └── platform/            # Platform (loader, configDir) + Services (ServiceLoader; META-INF/services in each loader part)
│   ├── src/main/resources/assets/cakechat/   # icon.png (placeholder, drawn by script), lang/{en_us,zh_cn,zh_tw}.json (same keys;
│   │                                         #   key.category.cakechat.main = 26.1's Controls category name)
│   └── src/test/java/…/         # JUnit 5: ChatConfigTest, CompactorTest, TabRulesTest (incl. the shipped Monumenta tabs on made-up
│                                #   lines), ChannelsTest, GuildTagTest, FindTest, LangTest (same keys in all 3 languages): 53 tests
└── <mc>/ (1.20.4, 1.21.1, 26.1.2)
    ├── version.properties   # from the kit
    ├── common/              # Minecraft-facing code for both loaders (compiles against vanilla + YACL's NeoForge jar, Mojang names)
    │   ├── src/main/java/io/github/harrythecake/cakechat/
    │   │   ├── CakeChat.java        # shared mod class: init (config), keys, clientTick, joined, the /cakechat command tree
    │   │   │                        #   (settings, edit, tablist) built for either loader's command source; config/windows/store
    │   │   ├── ChatEntry.java       # one message: content (replaceable), signature, indicator, tick, millis, compact key, count
    │   │   ├── ChatStore.java       # history (oldest first), compacting, limit, mirroring vanilla's in-place swaps, routing into tabs
    │   │   ├── ChatWindows.java     # all windows: views, rules, unread counts, mouse routing, move/resize/tab drags (moves and
    │   │   │                        #   resizes only with windowEditing on), toggleWindowEditing, send prefix
    │   │   ├── ChatView.java        # one window: wrapped lines, drawing (vanilla's layout + fade), tab bar, grip (editing on),
    │   │   │                        #   queue bar, scroll, hover
    │   │   ├── FindBar.java         # find bar UI in the open chat (Ctrl+F): field + Aa/.* toggles + count
    │   │   ├── ChatCopy.java        # message → clipboard text: plain, § codes (hex as §x§R§R§G§G§B§B), JSON
    │   │   ├── CopyMenu.java        # right-click menu on a message: copy text / codes / JSON, "Copied" flash
    │   │   ├── SettingsScreen.java  # YACL screen; labels cakechat.config.<key>, tooltips <key>.desc; General = windowEditing
    │   │   ├── SelfTest.java        # dev only, -Dcakechat.selftest=true (see Build)
    │   │   └── mixin/               # cakechat.mixins.json: ChatHudMixin (ChatComponent, priority 500), ChatScreenMixin,
    │   │                            #   InGameHudMixin (Gui), PlayerListHudAccessor (PlayerTabOverlay.getPlayerInfos)
    │   └── src/main/resources/cakechat.mixins.json
    ├── fabric/              # CakeChatFabric (client entrypoint: keys, END_CLIENT_TICK, JOIN, ClientCommandRegistrationCallback),
    │                        #   FabricPlatform, ModMenuEntry, fabric/mixin/MouseMixin (cakechat.fabric.mixins.json), fabric.mod.json
    │                        #   (depends fabric-api + yet_another_config_lib_v3; conflicts chatplus)
    └── neoforge/            # CakeChatNeoForge (@Mod: keys via RegisterKeyMappingsEvent, client tick, LoggingIn,
                             #   RegisterClientCommandsEvent, ScreenEvent.MouseDragged/MouseButtonReleased.Pre in place of Fabric's
                             #   MouseMixin, config screen extension point = Mods → Config), NeoForgePlatform,
                             #   META-INF/mods.toml (1.20.4) / neoforge.mods.toml (YACL required, chatplus incompatible)
```

## Build, run, test

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # all 6 jars → build/libs/
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=26.1.2   # one version (+ core)
```

`build/libs/cakechat-<fabric|neoforge>-<mc>-0.3.0.jar`, plus `cakechat-0.3.0.jar` = the Fabric 1.20.4 jar (what the pack uses).
YACL is a required runtime dependency, never bundled: 1.20.4 Fabric 3.6.6, 1.20.4 NeoForge 3.6.2 (3.6.4–3.6.6 have an empty
`loaderVersion` and don't load on NeoForge 20.4.251), 1.21.1 3.8.1, 26.1.2 3.9.7 (the Fabric 26.1.2 dev client also gets
MixinExtras 0.5.5, which YACL 3.9.7's mixins need; runtime only, not shipped).

**Self-test** (dev client, `-Dcakechat.selftest=true`, needs a world): adds made-up chat lines (never real players'), opens the
chat, logs the click event under the newest line, right-clicks a line (copy menu) and logs the three copy formats, checks the
window-editing lock (`edit lock ok=true`: no grip by default; `/cakechat edit` sent through the client-command path turns it on
and the grip answers; again turns it off), opens the find bar and the settings screen, takes screenshots
(`<mc>/<loader>/run/screenshots/cakechat-selftest-{open,copymenu,edit,find,settings}.png`) and logs `cakechat self-test done`.
Sends nothing to a server. With the kit's smoke script, from the repo root:

```
python tools/multiloader/smoke.py cakechat <mc> <fabric|neoforge> "--world=<smoke world>" --jvm=-Dcakechat.selftest=true "--done=cakechat self-test done"
```

## Decisions (2026-10-28)

- **UI:** in-chat UI (windows, tabs, find bar, scrollbar) is our own code on vanilla widgets and mixins: no UI library helps inside `ChatHud`. The settings screen uses **YACL** (already in the pack, so players download nothing extra; it has colour pickers and list editors). No owo-lib/LibGui.
- **Ships on by default** in the pack (it replaces ChatPlus); ChatPlus, More Chat History and Don't Clear Chat History leave the pack in the same release.
- **History stays in memory only.** Unlimited by default (`history.maxMessages = 0`), kept across disconnects. Never written to disk: player chat is personal data.
- **Not doing** (they break the pack's rules): ChatPlus's online translator (sends chat to a web service; CakeText does translation locally), speech to text (bundles Vosk, uses the microphone), screenshot auto-upload, image previews from links (fetching any URL leaks the player's IP).

## Design

- **Store vs vanilla:** vanilla's `ChatHud` is the way in and keeps its own 100 `messages` (other mods read and change them); CakeChat keeps its own `ChatStore` and draws it. Vanilla changes a line in place by `messages.set(i, …)` + `refresh()` (server deletion → "deleted" marker, CakeText's late translation, matched by `line.content() == original`). `ChatStore.onRefresh` keeps a shadow copy of vanilla's list and an identity map vanilla line → entry, and copies every replaced line's content into its entry. So CakeText 0.2.0 works unchanged; no API needed so far.
- **Windows (M2):** `config.windows` (first = main). Position (`x`, `bottom`) and size (`width`, `height`, `unfocusedHeight`) in GUI pixels; -1 = Minecraft's Chat Settings (the main window's default, so nobody's chat changes on update). Scale, opacity, background, line spacing stay Minecraft's, for all windows. Drawing transform: translate(x) → scale → translate(4), i.e. vanilla's box shifted. While the chat is open: a tab bar (12 px) under each box, a resize grip at the box's top-right (only with window editing on, below); the delayed-message bar sits under the main window's tab bar and CakeChat handles its click (vanilla's `ChatHud.mouseClicked` returns false).
- **Window editing lock (user's request 2026-11-07):** `config.windowEditing` (top-level boolean, **default false**; files without it load as false, so no config-version bump). Off: no grip drawn and `ChatView.inGrip` answers false; a tab press can't turn into a move (it stays a tab click, selected on release, `/join` logic unchanged); a press on the tab bar's empty part isn't claimed (`mouseClicked` returns false, so UMM's HUD drag etc. still get it). On: the move/resize behaviour below, unchanged. Toggled by the YACL option `cakechat.config.windowEditing` in the **General** category (after the window categories) or `/cakechat edit` (client command; toggles, saves the file, prints `cakechat.edit.on`/`.off` in the player's own chat; `ChatWindows.toggleWindowEditing`, which also ends a move/resize in progress). `ChatView.move`/`resize` stay as they are: a later CakeUI HUD editor is meant to move/resize windows through CakeChat.
- **Mouse:** drags and releases are wrapped where `Mouse` calls `Screen.mouseDragged`/`mouseReleased` (`MouseMixin`), never added to `ChatScreen`: UMM's bundled njols-ui-framework adds those two methods to `ChatScreen` for its HUD editor, and Mixin keeps only one (fixed 2026-10-29; before, UMM's HUD drag in the open chat was skipped). CakeChat claims only a drag it started. `ChatScreen.mouseClicked` HEAD → `ChatWindows.mouseClicked` (topmost window first: queue bar, grip → resize (editing on), tab → select on release or move after 3 px (editing on), tab bar → move (editing on; off: not claimed); a click in a box only makes it the active window and falls through to vanilla's text-click handling). Hover/click styles and indicators go to the window under the point; scroll and Page Up/Down to the window under the mouse, else the active one. Moves/resizes/tab selections save the file only (`saveConfigFile`); the settings screen's save rebuilds windows, rules and tab membership (`saveConfig` → `ChatWindows.rebuild` → `ChatStore.reroute`).
- **Tabs:** `TabRules` gives every tab of every window one index; `ChatEntry.tabs` is a BitSet of them, set on arrival and on reroute. Within a window: priority order (stable), pattern `find()` on the routing text, empty = all, `skipOthers` stops later tabs except `alwaysAdd` ones, invalid regex = matches nothing (the settings screen refuses invalid patterns anyway). Unread counts per tab (memory only), cleared on selecting it.
- **Routing text:** taken at `addMessage` HEAD (mixin priority 500, meant to run before CakeText's `@ModifyVariable` at HEAD, so tabs match the server's text even when CakeText shows the translation; verified in game with CakeText in translated mode 2026-10-29), private-use characters removed (CakeText's icons U+E100/U+E101), leading spaces stripped. A late CakeText swap never moves a message between tabs.
- **Default tabs (shipped in the jar, user's choice 2026-10-29):** main window = All, Global `^<g> ` (channel `g`), LFG `^<lfg> ` (`lfg`), Trade `^<tr> ` (`tr`), 中文 `^<zh> ` (`zh`, added 2026-10-29; config version 2 inserts it after Trade in existing default setups), Guild `^(?!<(?:lfg|tr|zh|es|mh)>)<.{2,5}.*>.*\[.{2,5}\]` (the user's own ChatPlus guild pattern, anchored; channel `{guild}`, see below), Whisper `whispers? to` (the user's ChatPlus `dm` pattern, also catching "You whisper to"). All but All have `server` = `(^|\.)playmonumenta\.com(:\d+)?$`, so they're hidden (and never send commands) elsewhere. Names are translation keys `cakechat.tab.*` shown in the game's language (`CakeChat.tabName`) until the player renames them.
- **Channel switching (`Channels`, user's spec 2026-10-29, fixed after the first in-game test the same day):** clicking a tab with a `channel` sends `/join <channel>` (not again if CakeChat already switched there). Clicking a tab without one joins the **player's own channel** again: the one they last picked themselves (their typed `/join X`, or a "Joined channel X." the server sent that CakeChat didn't ask for, e.g. a clickable link). Unknown own channel → nothing is sent. **Never `/leave`:** on Monumenta it takes you out of the channel (the first version used it and the player lost their guild channel EDE and `tr`). The own channel is remembered per server address in `config.channelMemory` (lower-cased address → channel), so it survives restarts. Monumenta's replies "Joined channel X." to CakeChat's own /join (within 10 s) are cancelled at `addMessage` HEAD (unsigned lines only) and never stored; replies to the player's own commands show. **CakeChat sends commands only when the player clicks a tab: never on join, on a timer, or by itself** (relevant to server mod rules).
- **Guild channel (`GuildTag`, 0.3.0, user's request 2026-11-03):** a tab channel `{guild}` (any case) stands for the player's guild channel. On the click, CakeChat reads the tab list in display order (`PlayerListHud.collectPlayerEntries` via `PlayerListHudAccessor`, names via `getPlayerName(...).getString()`), and takes the first `[TAG]` under the Guild header → `/join TAG` (then the usual `Channels.select`). TAG = 2–5 letters, upper or lower case (Monumenta's rule for guild tags, user 2026-11-03). Lenient, because the first strict version (exactly `Guild`, then `<shard> [TAG] Name`) found nothing in game (2026-11-03): `§` codes and private-use glyphs ignored; header = a line without a tag whose letters start with "guild"; blanks under it skipped; the next tagless line ends the section. No Guild section → the tag on the line ending in the player's own name. Nothing found → nothing sent, action-bar note `cakechat.guild.unknown`. **`/cakechat tablist`** prints the lines CakeChat reads (non-ASCII as `\uXXXX`) and the guild it finds, in the player's own chat only (never logged or sent): use it when Monumenta's tab list changes. The tab list is read only on a click or that command, never stored. Config version 3 sets `{guild}` on the default Guild tab when its channel is empty. Assumes Monumenta's guild channel name = the tab-list tag (true for the user's EDE). Works in game (user, 2026-11-03, lenient version; the first strict version found nothing).
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
- **ChatPlus import (M4): moved to Cakedition Updater 1.7.0** (user, 2026-11-03), together with the other imports from a player's old install. CakeChat 0.3.0 has no `/cakechat import`, no first-join offer, no "ChatPlus" settings category and no `chatPlusOffer` field (old files keep it; Gson ignores it and the next save drops it). The conversion rules are unchanged; see `updater/AGENTS.md`.
- **Outgoing translation (lowest priority, user's request):** type a message, have CakeText translate it, then send. Needs Chinese → English: only CakeText's local-server engine (Hunyuan) can do that; opus-mt is English → Chinese only.

## Milestones

Status marks: `[x]` done, `[ ]` open.

- [x] **M0 skeleton:** builds, loads config, YACL settings screen, Mod Menu entry, `/cakechat`, 3 languages, tests.
- [x] **M1 one window** (tested in game by the user 2026-10-28, test instance on Monumenta: look/fade/hover/clicks, >100 lines + red scrollbar, kept on shard switch + relog, F3+D clears, compacting, timestamps in all 3 placements, CakeText icons/hovers, UMM skill HUD): own message store (unlimited, kept on disconnect), CakeChat draws the chat instead of vanilla (size, text scale, opacity, line spacing, position), scrolling + scrollbar + scroll keys, timestamps, compact duplicates.
- [x] **M2 windows and tabs** (second in-game test passed 2026-10-29, all 15 steps: no /leave, own channel back and remembered, hidden join replies, 中文 tab, remove confirmations, routing incl. CakeText in translated mode, unread, prefix, second window move/resize, non-Monumenta servers, shard switch/relog, UMM HUD drag after the MouseMixin fix, with Ixeris): several windows, tabs with regex rules (priority, always add, skip others, auto prefix), unread counts, move by the tab bar, resize by the corner grip.
- [x] **M3 find, copy, peek, hide** (built and tested in game by the user 2026-10-29, all 11 steps; 35 tests): find bar (contains/regex, ignore case, highlight matches), copy a message (plain or formatted), peek-chat and hide-chat keys.
- [x] **M4 ChatPlus import** (moved to Cakedition Updater 1.7.0 on 2026-11-03; built 2026-10-29, 42 tests; tested in game by the user 2026-10-29, all 7 steps: first-join offer + [Import], no second offer, settings button with cancel/confirm, `/cakechat import`, imported settings; real 2.7.0 file → 1 window, 4 tabs, nothing skipped; ChatPlus's files unchanged by checksum): read the newest `config/chatplus/chatplus-v*.json`, convert windows/tabs/patterns/compact/timestamps/keys; offered once when that file exists and no CakeChat setup has been made, and from a settings button. Never changes ChatPlus's files. Test fixtures are made up, never a player's real file.
- [x] **Window editing lock** (2026-11-07, works in game: user): moving/resizing off by default, settings toggle + `/cakechat edit`.
- [x] **Tab reset fix** (2026-11-07, unit-tested, not yet tried in game): the settings screen's reset gave every tab a blank "All" tab's values (defaults were `new Tab()`); now `Tab.resetTarget()`: the shipped default tab it came from (by translation-key name, or by unchanged pattern once renamed; the name goes back to the key), else a copy of the tab itself (a tab the player added is left alone). **Repair (2026-11-09):** tabs the pre-fix reset already blanked (saved as name "All"/"全部" in plain text, every other field empty) can't be traced back by `resetTarget`, so a reset kept them "All" (found in the test instance: main-window tabs 2-7). Config version 4 (`ChatConfig.repairBlankedTabs`): in the main window, when tab 1 is still `cakechat.tab.all` (a ChatPlus import names its own "All" tab in plain text), each tab with exactly that fingerprint gets the shipped tab for its position back, unless that shipped tab is already in the window. 4 unit tests; all six targets build. Not yet tried in game.
- [ ] **Later:** highlight filters with sound, bookmarks (manual + auto), delete message, chat screenshot (clipboard/file only), hover highlight, send note, input box extras (character count, long-message auto-fill), player heads.
- [ ] **Last:** outgoing translation through CakeText.

## Invariants

- Commands go to the server only from a player's click on a tab (`/join`; never `/leave`) or a tab prefix on a message the player sends; never automatically.
- Nothing in the store or the config holds player chat on disk.
- Every UI string in en_us, zh_cn and zh_tw, same keys.
- `ChatConfig` stays plain Java (no Minecraft classes), so tests can load it.
- Test data uses made-up names (`qwertfox`, `Mock_Pl4yer`, …).
- A click area answers only while its element is drawn (the delayed-message bar: only with messages queued; vanilla's `process()` throws on an empty queue).

## Multiloader status (2026-11-07)

Ported 2026-11-05/07 from the Yarn 1.20.4 Fabric mod (migrateMappings + yarn2moj, files moved with `git mv`), the `windowEditing`
lock and tab reset fix from `main`'s `010de51` carried over into `core/` (ChatConfig, lang, ChatConfigTest) and every
`<mc>/common` (ChatView, ChatWindows, SettingsScreen, CakeChat's `/cakechat edit`). 1.20.4 Fabric behaves as before: same mod id,
config file and field names, keys, lang keys, commands, mixin targets (Mojang names now).

Every target: `./gradlew build` OK (49 core tests), smoke PASS on the title screen and with `--world` + `-Dcakechat.selftest=true`
(click event under the newest line, copy menu + the three copy formats, `edit lock ok=true`, find bar `found=1`, YACL settings
screen opens, `self-test done`, no error lines). Screenshots checked by eye (open/edit/copy menu/find/settings): no grip while
locked, grip at the box's top-right after `/cakechat edit`, "General" category in the settings.

Same on all targets: commands go to the server only from a tab click (`/join`, never `/leave`) or a tab prefix; `/cakechat`,
`/cakechat edit` and `/cakechat tablist` are client commands (no clickable chat buttons run them, so NeoForge 1.20.4's
`sendUnsignedCommand` gap doesn't matter here); ChatPlus is a declared conflict (Fabric `conflicts`, NeoForge `incompatible`);
YACL settings on both loaders.

| Target | Works | Differs / gaps (why) |
|---|---|---|
| 1.20.4 Fabric | everything, as before | none. |
| 1.20.4 NeoForge | everything; settings from Mods → Config (`ConfigScreenHandler`), keys via `RegisterKeyMappingsEvent`, client command via `RegisterClientCommandsEvent` | drags/releases in the open chat come from `ScreenEvent.MouseDragged.Pre` / `MouseButtonReleased.Pre` instead of Fabric's `MouseMixin` (same place: before the screen gets them). YACL pinned to 3.6.2 (3.6.4–3.6.6 don't load on NeoForge 20.4.251). |
| 1.21.1 Fabric | everything | `refreshTrimmedMessages` (renamed), `ChatComponent.render` has a `focused` flag; JSON copy uses `Component.Serializer.toJson(text, registries)` (the world's registries, else the built-in ones). |
| 1.21.1 NeoForge | everything (as 1.20.4 NeoForge; `IConfigScreenFactory`, `ClientTickEvent.Post`, `@Mod(dist = CLIENT)`) | as 1.21.1 Fabric. |
| 26.1.2 Fabric | everything | lines enter through the private `addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)` (all public add methods pass it). GUI is recorded render state (`GuiGraphicsExtractor`, no depth): lines first, then tab bar/grip/queue bar; the copy menu goes on the next stratum. Text hovers and the hand cursor come from `textRenderer(TOOLTIP_AND_CURSOR)`; clicks find their text through `captureClickableText` (CakeChat feeds every window's visible lines); the indicator tooltip is drawn by CakeChat (the chat screen no longer asks). JSON copy = `ComponentSerialization.CODEC` + registries. Keys in a registered category `cakechat:main` (lang `key.category.cakechat.main`; the old `key.categories.cakechat` stays for 1.20.4/1.21.1). Dev client only: MixinExtras 0.5.5 for YACL 3.9.7. |
| 26.1.2 NeoForge | everything (as 26.1.2 Fabric; keys via `RegisterKeyMappingsEvent.registerCategory`) | as 26.1.2 Fabric. |

Not tested on any target: real Monumenta chat (channels, `/join`, guild tab list), CakeText together with CakeChat, UMM's HUD
drag in the open chat (UMM is Fabric 1.20.4 only), moving/resizing with a real mouse drag (the self-test checks the lock state
and the grip hit test, not a drag). Known since before the port: the copy menu's "Copy with colour codes (§)" shows as
"… (": the `§)` in the label is read as a formatting code.
