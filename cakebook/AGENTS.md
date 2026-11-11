# CakeBook — agent reference

Client-only mod (`cakebook`, package `io.github.harrythecake.cakebook`) for **Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2** (Cake multiloader layout, see `../tools/multiloader/README.md`; the Cakedition pack ships the Fabric 1.20.4 jar): a guide book and small local wiki read in game. It shows **books** (Markdown pages shipped by a modpack, a resource pack or a mod), keeps the player's own **notes**, and has one **search** over both. Started 2026-10-28.

Decisions made with the user (2026-10-28):
- Content: a pack guide, a Monumenta gameplay guide (both written by the pack, not by the mod) and player notes. Not copied wiki pages.
- Opened from the pause menu button, an inventory button, a key binding (B by default), `/cakebook` and Mod Menu.
- Pages are Markdown files per language, read from resource packs **and** from `<game folder>/cakebook/books/`. The jar is a reusable engine plus a short help book; the Cakedition pages ship with the pack, so text changes need only a pack update. The books folder exists because a resource pack in the pack would have to be turned on in each player's `options.txt`, which existing installs never get (yosbr only copies missing files).
- Notes: write/edit, insert location (UMM area + shard + coordinates), links between notes and pages, search.
- License MIT. **Ships in the Cakedition pack since 2.3.0 (2026-10-29), on by default** (`pack/mods/cakebook.pw.toml` → the v0.1.0 GitHub release).

## Files

```
cakebook/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/   # the Cake multiloader kit (apply_kit.py copies them; don't edit here)
├── gradle.properties                # mod_id, mod_name, mod_version, mod_group
├── .github/workflows/build.yml      # CI in the standalone CakeBook repo: JDK 17/21/25, all six jars + cakebook-<version>.jar (see Release)
├── core/                            # plain Java, no Minecraft imports; compiled into every jar
│   ├── build.gradle                 # passes -Dcakebook.checkBooks to the tests
│   ├── src/main/java/io/github/harrythecake/cakebook/
│   │   ├── CakeBook.java            # MOD_ID, LOGGER, the loaded config/books/notes
│   │   ├── Markdown.java            # the Markdown subset → Doc(meta, blocks of styled spans)
│   │   ├── Links.java               # link / image targets, GitHub-style relative paths
│   │   ├── Book.java, Library.java  # one book (pages per language, contents); all books + Builder (later files win)
│   │   ├── Lang.java                # language fallback (zh_hk → zh_tw → zh_cn → en_us), as in CakeUpdater
│   │   ├── Search.java, Notes.java  # one search over pages and notes; config/cakebook/notes/<id>.md
│   │   ├── Config.java              # config/cakebook/config.json5
│   │   └── platform/Platform.java, Services.java   # what the loader gives (config dir, game dir, is-mod-loaded)
│   ├── src/main/resources/assets/cakebook/
│   │   ├── lang/{en_us,zh_cn,zh_tw}.json     # same keys in all three
│   │   ├── icon.png                          # 64×64, 16×16 pixel art ×4 like CakeText's
│   │   └── cakebook/help/…                   # the built-in "CakeBook help" book (3 pages × 3 languages)
│   └── src/test/java/…              # the JUnit tests (plain Java)
└── <mc>/ (1.20.4, 1.21.1, 26.1.2)
    ├── version.properties           # from the kit
    ├── common/src/main/             # Mojang names, both loaders
    │   ├── java/io/github/harrythecake/cakebook/
    │   │   ├── CakeBookClient.java  # open key, tick, reload listener, the /cakebook command tree (generic over the source)
    │   │   ├── BookLoader.java      # resource manager + books folder → Library.Builder
    │   │   ├── Images.java          # PNG → texture, cached until the next reload
    │   │   ├── PageLayout.java      # Doc → positioned lines/boxes/images for one width; link click/hover lookup
    │   │   ├── RowList.java         # scrolling clickable rows (sidebar, link picker)
    │   │   ├── BookScreen.java      # the book: sidebar + page, history, note buttons
    │   │   ├── NoteEditScreen.java, PickPageScreen.java
    │   │   ├── MenuButtons.java     # pause menu + inventory book buttons (afterInit hook, loader-neutral)
    │   │   ├── Location.java        # UMM area/shard by reflection (same calls as CakeText's UmmModule)
    │   │   ├── SelfTest.java        # -Dcakebook.selftest=<page>: dev-client check (see Tests)
    │   │   └── mixin/EditBoxWidgetAccessor.java, HandledScreenAccessor.java (+ RecipeBookScreenAccessor on 26.1.2)
    │   └── resources/cakebook.mixins.json
    ├── fabric/                      # CakeBookFabric (ClientModInitializer: Fabric API events), ModMenuEntry, FabricPlatform, fabric.mod.json
    └── neoforge/                    # CakeBookNeoForge (@Mod: NeoForge events, Mods-list "Config" = the book), NeoForgePlatform,
                                     # META-INF/mods.toml (1.20.4) / neoforge.mods.toml (1.21.1+);
                                     # 1.20.4 only: mixin/ClientPacketListenerMixin + cakebook.neoforge.mixins.json (see Multiloader status)
```

The classes in `core` use no Minecraft classes (Gson in `Library`/`Config` is fine), so the JUnit tests run them directly. Each version's `common` is the same code apart from the API changes listed under **Multiloader status**.

## Book format

```
<book id>/book.json             {"title": {"en_us": …, "zh_cn": …} or "…", "order": 0, "icon": "minecraft:cake"}
<book id>/<lang>/index.md       home page (path "index")
<book id>/<lang>/mods/index.md  the "mods" folder's own page (its title names the folder in the contents)
<book id>/<lang>/mods/x.md      page "mods/x"
<book id>/<anything else>       other files (images); a folder counts as a language only if it looks like xx_yy (or lzh)
```

- Sources: `assets/<any ns>/cakebook/<book>/…` from the resource manager (top pack wins per file), then `<game>/cakebook/books/<book>/…` (wins over resources). Book ids: `[a-z0-9_.-]+`; a book with no pages is dropped and logged.
- Front matter (`---` block): `title`, `order` (default 1000), `hidden: true` (not in contents; links and search still work). Title fallback: front matter → first `#` heading → file name.
- Books sort by `order`, then id. `config.firstBook` (default "") picks the book opened first; otherwise the first in the list. The help book has order 9000 so a pack's book comes first.
- Language: page and title fall back per `Lang.fallback`; a page that exists only in some other language is still shown.
- Markdown: headings (ATX and setext), paragraphs (lines join with a space, except between two CJK characters), `-`/`*`/`+`/`1.` lists (2 spaces = one level, max 4), `>` quotes, `---` rules, fenced code, image lines `![alt|width](src)`, `|` tables (each row becomes a line, cells joined with " · "), hard breaks (two trailing spaces or `\`). Inline: `**`/`__` bold, `*` italic, `~~`, `` ` `` and ``` `` ``` code, `[text](target)`, `<https://…>` and bare https URLs, `{key:<binding id>}` (rendered as `Text.keybind`), backslash escapes. Unclosed markers stay text.
- Links (`Links.resolve`): `https://` → confirm screen; relative paths from the page's folder, `.md` optional, `/` = book root, folder → its `index`, `#fragment` ignored; `book:<id>[/<path>]`; `note:<id>`; other schemes are dropped. Inside a note, a bare name is a note id.
- Images (`Links.image`): relative to the page **file** (so `en_us/mods/x.md` uses `../../images/a.png`), or `namespace:path` for any resource. Web images are never fetched. Width defaults to the PNG's pixel size, capped at the page width.

## Notes

- `config/cakebook/notes/<id>.md`, front matter `title: "…"`, body after it. The id comes from the title on creation (`Notes.slug`: a-z0-9 and dashes, max 40, "note" for titles with no Latin letters, `-2`, `-3`… when taken) and never changes, so `note:` links survive renames. Files without front matter load with the id as title.
- In config/ on purpose: the player owns them, and Cakedition Updater's backups cover config/.
- Saved through `<id>.md.tmp` + atomic move. Escape and Save both save; Cancel discards; an empty new note isn't created; an empty title becomes the first line of the body (or "Untitled note").
- Insert location: `**Location:** <area> · <shard> · x y z` (label in the player's language). Without UMM: dimension id + coordinates.
- Insert link: `PickPageScreen` → `[title](book:…)` or `[title](note:…)` at the cursor (the editor keeps its text and cursor across the screen switch).

## Runtime flow

- Resource reload (start, F3+T, pack/language change) → `BookLoader.load` → `CakeBook.library`; `Images.clear()` first. `/cakebook reload` does the same with the current resource manager.
- `BookScreen`: history (`BACK`/`FORWARD`), the last page and its scroll, and expanded sidebar entries are static, so they survive closing the screen until the game quits. The sidebar shows books (expand arrow; click opens the home page), each book's contents (folders collapse), then "My notes"; while the search box has text it shows hits instead (tooltip = book + snippet).
- Keys: Ctrl+F search, arrows/PgUp/PgDn/Space/Home/End scroll, Backspace back, mouse buttons 4/5 back/forward, the open key closes.
- Links: the page's text carries `cakebook:link:<raw target>` in the style insertion; `BookScreen` resolves it relative to the shown page on click.
- Buttons: pause menu (only when the full menu shows) 24 px left of "Back to Game" (found by its translation key; falls back to 4,4); inventory (`InventoryScreen` only) left of the background (left of the recipe book while it is open; since 0.1.2, the right side was under vanilla's status effects), re-placed every frame because the recipe book moves it.
- Commands (client-side): `/cakebook`, `open <page>` (suggests every `book/path` and `note:id`), `search <text>`, `note <title>`, `reload`, `folder`.

## Invariants

- The mod downloads nothing and sends nothing; web links only open after the vanilla confirm screen.
- Every UI string is in en_us, zh_cn and zh_tw with the same keys; the help book has the same pages in all three (`BundledHelpTest`).
- No real player names or collected chat in tests or docs.

## Tests

`JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build` builds all six jars into `build/libs/` (`cakebook-<fabric|neoforge>-<mc>-<version>.jar`, plus `cakebook-<version>.jar` = the Fabric 1.20.4 jar under its old name) and runs 28 JUnit tests in `core` (27 pass, 1 skipped: the opt-in `checkBooks`): `MarkdownTest`, `LinksTest`, `LibraryTest`, `SearchAndNotesTest`, `BundledHelpTest` (every help page in all three languages, every link and image resolves). One version only: `-Pcake.versions=1.21.1`.

**Self-test (dev client):** `-Dcakebook.selftest=<page>` (e.g. `help`), with a world: `python tools/multiloader/smoke.py cakebook <mc> <loader> --world=<world> --jvm=-Dcakebook.selftest=help --done="CakeBook self-test done"`. Once the player is in the world it opens the book at that page, the note editor (inserts the location), the inventory (checks the book button is left of it) and the pause menu (button next to Back to Game), ~5 s each, logs `CakeBook self-test done (<n> problems)`; problems are logged as errors. The window title names the step (`CakeBook self-test book|note|inventory|pause`) so `winshot.py` can capture each one.

**Checking a pack's books:** `./gradlew test -Dcakebook.checkBooks=<folder holding one sub-folder per book>` runs the same checks on it (skipped without the property). Key ids in `{key:…}` aren't checked there; compare them with the `key_` lines of a live `options.txt`.

**In game (2026-10-28, test instance, alongside CakeChat):** the user reported "works well"; the log shows `2 book(s): [cakedition, help]` and nothing from CakeBook at WARN or above. Not yet exercised: notes (no `config/cakebook/notes/` was created, so no note was saved), Insert location on Monumenta, images (no book has one yet).

## The Cakedition pack guide

Drafted 2026-10-28 in the dev instance at `.minecraft/cakebook/books/cakedition/` (local, not tracked; a copy is in the test instance): book.json + 13 pages per language (index, first-steps, keys, updates, performance-tips, help, mods/{index,monumenta,performance,visuals,hud,chat,convenience,tools}). It passes `-Dcakebook.checkBooks`, and all 21 key ids exist in the live `options.txt`. **2026-11-03:** a chapter `cake/` (order 4, "Cakedition's own mods") with a summary `index` and one page per own mod (updater, chat, wiki, book, fixes, translator, collector), in all three languages; the mod pages under `mods/` now keep one line per own mod that links there, and `updates.md` links the import. 22 pages per language; passes `-Dcakebook.checkBooks` (run it on a folder holding only `cakedition/`: Moe_block's `monumenta-zh` is zh_cn only and fails the per-language check by design). CakeUpdater 1.7.0 opens `cakedition/cake/index` from chat on first launch and after updates: keep that path. Facts come from the pack's metafiles, Modrinth summaries, the shipped `options.txt` key lines, the mods' own lang files and CakeUpdater's lang files (button names). **Shipped in 2.3.0** as `pack/cakebook/books/cakedition/**` (a CakeWiki entry and its key were added then). The dev instance's folder stays the source: edit there, copy to `pack/cakebook/books/` by hand, `refresh`. Moe_block's `monumenta-zh` book is not shipped until its permission is recorded. The Monumenta gameplay guide is not started: the user writes it, or it's written with them.

## Multiloader status (2026-11-11)

Ported from the Yarn 1.20.4 Fabric mod (migrateMappings + yarn2moj, files moved with `git mv`; started 2026-11-04, finished 2026-11-11 on branch `cakebook-port`). 0.1.2's inventory button on the left (`bbf5b27`) is in every version; the help book's "to the right of your inventory" line, which 0.1.2 missed, now says left (en_us, zh_cn, zh_tw). 1.20.4 Fabric behaves as before: same mod id, config file and fields, key (`key.cakebook.open`, B, category `key.categories.cakebook`), lang keys, commands, mixin targets (Mojang names now).

Same on all targets: books from resource packs + `<game>/cakebook/books/`, notes, search, links, images, the `/cakebook` client command tree, the open key, the pause-menu and inventory buttons, Mod Menu (Fabric) / Mods → Config (NeoForge) opening the book.

| Target | Built + unit tests | Run in a dev client | Differs / gaps (why) |
|---|---|---|---|
| 1.20.4 Fabric | yes | smoke PASS with `--world` + `-Dcakebook.selftest=help` (2026-11-11: book `help` 55 lines, note editor inserted the location, inventory button at x 103 left of the inventory at 125, pause button 24 px left of Back to Game, 0 problems, no error lines; screenshots of all four steps checked) | none. |
| 1.20.4 NeoForge | yes | not run (user's rule since 2026-11-07: game runs only on Fabric 1.20.4) | `ScreenEvent.Init.Post` / `Render.Pre` instead of Fabric's screen events, `ConfigScreenHandler`, `TickEvent.ClientTickEvent` (END). NeoForge 20.4 doesn't run client commands from chat click events (`sendUnsignedCommand`), so CakeUpdater's "open the guide" chat button (`/cakebook open …`) would reach the server: a NeoForge-1.20.4-only mixin (`ClientPacketListenerMixin`, `cakebook.neoforge.mixins.json`) runs only `/cakebook …` client-side, as CakeUpdater does for `/cakeupdate`. |
| 1.21.1 Fabric | yes | not run | `ResourceLocation.fromNamespaceAndPath`. |
| 1.21.1 NeoForge | yes | not run | `@Mod(dist = CLIENT)`, `IConfigScreenFactory`, `ClientTickEvent.Post`, `neoforge.mods.toml`. |
| 26.1.2 Fabric | yes | not run | GUI is `GuiGraphicsExtractor` / `extractRenderState` (`text`, `centeredText`, `item`, images via `blit(RenderPipelines.GUI_TEXTURED, …)`); input events `MouseButtonEvent` / `KeyEvent`; `MultiLineEditBox.builder()`; hovers drawn with `setTooltipForNextFrame` (only `ShowText`: all of a page's hovers are text; `componentHoverEffect` is private); the click lookup measures the line itself (`StringSplitter.componentStyleAtWidth` is gone); a web link that isn't a valid URI gets no `ClickEvent` but still opens through the book (insertion); the recipe book comes from `RecipeBookScreenAccessor`; textures `DynamicTexture(label, image)`; `Identifier`; `client.schedule`; key category `cakebook:cakebook` registered with `KeyMapping.Category.register` (lang `key.category.cakebook.cakebook`; the old `key.categories.cakebook` stays for 1.20.4/1.21.1); Fabric API `KeyMappingHelper`, `ResourceLoader`, `ScreenEvents.beforeExtract`, `Screens.getWidgets`. |
| 26.1.2 NeoForge | yes | not run | as 26.1.2 Fabric; key category through `RegisterKeyMappingsEvent.registerCategory`, books through `AddClientReloadListenersEvent`. |

Not tested on any target: Insert location with UMM on Monumenta (UMM is Fabric 1.20.4 only), images in a real book, CakeUpdater's guide button on NeoForge 1.20.4 (the mixin is built, not run).

## Release

Same as CakeUpdater: bump `mod_version`, commit `cakebook/` only, push (only when the user asks), CI (`.github/workflows/build.yml`, JDK 25 for Gradle) builds all six jars and publishes `v<mod_version>` with every jar plus `cakebook-<version>.jar` (the Fabric 1.20.4 jar under its old name, the one the pack downloads); then `packwiz_sync.py github mods HarrytheCake/CakeBook v<version>`. Standalone repo `HarrytheCake/CakeBook` (remote `cakebook`, first pushed 2026-10-29; no GitLab copy yet).
