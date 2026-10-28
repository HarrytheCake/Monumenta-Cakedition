# CakeWiki: reference for agents

In-game reader for https://monumenta.wiki.gg/ (client-only Fabric 1.20.4 mod, id `cakewiki`, MIT). This folder is the source of truth; the standalone repo `HarrytheCake/CakeWiki` is written only by `git subtree push` / `tools/push_all.py` from the Monumenta-Cakedition repo. Keep this file current when changing the mod.

## Design

- **Fetch:** MediaWiki API at `https://monumenta.wiki.gg/api.php`, `action=parse` (rendered HTML). Send a descriptive User-Agent and cache on disk.
- **Parse:** jsoup, bundled in the jar with Loom `include` (MIT).
- **Model + layout:** our own document model (headings, styled text, links, lists, images, tables, infoboxes, collapsibles) laid out for Minecraft's `TextRenderer`. Unknown elements fall back to their text.
- **Render:** vanilla `Screen` and widgets. No UI-library dependency.

Why own rendering: no Fabric library renders HTML. Book mods (Patchouli, Lavender) need their own formats, so we would write the HTML conversion anyway, and embedded Chromium (MCEF) downloads code at runtime, which our mods never do.

## Wiki facts (checked 2026-10-28)

- MediaWiki 1.43.6 with Portable Infobox, TabberNeue, Scribunto, CirrusSearch, EmbedVideo.
- Content license CC BY-SA 4.0: show attribution (title, link, license) on every page.
- About 4,600 articles. In 30 random articles: infobox (`aside.portable-infobox`) 27, collapsibles (`mw-collapsible`) 20, `wikitable` 16; inline `color`/`background` styles are common (item tiers, regions).
- Images are relative `/images/<file>?<hash>`, PNG and animated GIF (Java's ImageIO decodes GIF).
- Median page about 18 KB of HTML; `Changelogs` is about 1.2 MB, so layout must not block the render thread.

## Decisions (user, 2026-10-28)

- Opened by a key (default **K**, the user's choice 2026-10-29; H was the first default) and `/wiki <page>`.
- The same key over an item in a container screen opens that item's page (`WikiClient.resolve`: exact title or redirect, else the top search hit).
- Search box with suggestions as you type (`opensearch`).
- Whole wiki for now; may later be cut to the pages players need.
- Bookmarks (user's request, 2026-10-29): ☆/★ in the bar or Ctrl+D toggles the page; ☰ lists them (click opens, ✕ removes).
- **CakeText Translator integration** (user's request 2026-10-29): pages translated, **special nouns (mainly the linked ones) keep their original names**. Done in 0.1.0 (see "Translation" below).
- Whether the pack ships it: decided later.

## Files

```
src/main/java/io/github/harrythecake/cakewiki/
├── CakeWiki.java           # entrypoint: K key (KeyBinding "key.cakewiki.open"), /wiki [page] with live suggestions, lookupItem
├── Reader.java             # one per game: client, Pages, WikiImages, converter, Bookmarks, back/forward history, last page
├── Bookmarks.java          # config/cakewiki/bookmarks.json, newest first; unreadable file → bookmarks.json.bad
├── WikiScreen.java         # the reader: bar (◀ ▶ search ⟳ ↗), page area, loading/error states, links, tooltips
├── DevShots.java           # dev only: screenshot tour (runClient -PdevShot=…), inert in a normal game
├── mixin/HandledScreenMixin.java  # K over an item in a container → its page
├── translate/              # CakeText Translator (optional); no Minecraft classes except the bridge
│   ├── Translator.java     # parts + keep flags → Strings/Integers (kept-part index), or null; changes() counter
│   ├── PageTranslator.java # a line of inlines → its translation; what's kept; per-line memo until refresh()
│   └── CakeTextBridge.java # TranslatorApi (CakeText 0.3.0+) by reflection; null when not installed
├── layout/                 # no Minecraft classes: unit-tested with fake Metrics
│   ├── Metrics.java        # text width, line height, loaded picture size, labels
│   ├── Layout.java         # Document → Laid; colours (dark theme), tables, infobox, collapsibles, tabs, wrapping
│   ├── Laid.java           # positioned Run/Pic/Fill/Control + anchors + height; linkAt/controlAt
│   └── ViewState.java      # open collapsibles and selected tabs, keyed by block identity
├── image/
│   ├── ImageDecoder.java   # ImageIO → ARGB frames; GIF composed by hand (no Graphics2D/AWT); max 1024 px side
│   └── WikiImages.java     # textures: fetched on first draw, ≤6 downloads, disk cache, pixel-budget LRU
├── doc/                    # page model + HTML conversion (no Minecraft classes: unit-testable)
│   ├── Document, Block, Inline, Link, Image   # sealed model: Heading, Paragraph, ListBlock, ImageBlock, Gallery,
│   │                                          # Table/Row/Cell, Infobox (ImageTab, InfoRow, InfoHeader),
│   │                                          # Collapsible, Tabs/Tab, Indented, Rule; Text/InlineImage/LineBreak
│   ├── HtmlConverter.java  # jsoup → model
│   └── CssColor.java       # inline CSS colours → 0xRRGGBB
└── net/                    # no Minecraft classes either
    ├── WikiClient.java     # API calls (parse, opensearch, search, resolve); Transport interface for tests
    ├── WikiException.java  # MediaWiki error code (missingtitle, …) or "http"
    ├── Titles.java         # MediaWiki title matching (underscores = spaces, first letter case-insensitive)
    ├── PageCache.java      # gzipped JSON per title; key = SHA-1 of Titles.normalize
    └── Pages.java          # cache while fresh (24 h), else fetch; stale copy when offline, not for missingtitle
```

### Conversion rules (`HtmlConverter`)

- Root is `.mw-parser-output`. Skipped: `script`/`style`/`button`/`iframe`…, `.mw-editsection`, `#toc`/`.toc`, `.noprint`, `.magnify`, `.embedvideo` (videos aren't shown), `.mw-empty-elt`, `.tabber__header`, `display:none`.
- Inline styles carry down: `color`, `font-weight:bold`, `font-style:italic`, `text-decoration`; `<font color>`. Cell/row `background(-color)`/`bgcolor` become `Cell.background`.
- Links: `/wiki/T#a` → `Page(T, a)` (underscores → spaces, percent-decoded, `+` kept); `#a` → `Page("", a)`; `a.new` → `Missing`; other http(s) → `External`. A link wrapped around a picture (`a.image`, to the File: page) is dropped.
- `table.mw-collapsible`: first row is the title, the rest the content. `div.mw-collapsible`: `.mw-collapsible-content` is the content, anything outside it the title (empty when there is no content div).
- Infobox: `.pi-title`, `.pi-media-collection` (tab labels + one image each) or `.pi-image`, `.pi-header`, `.pi-data` (label/value), `.pi-horizontal-group` (one row per column); other wrappers (`section.pi-group`) are walked into.
- Tabber: panels labelled by `data-mw-tab-title`/`data-title`, else `aria-labelledby`, else the nth `.tabber__tab`.
- Nested tables keep their rows (only the table's own `tr`, through `thead`/`tbody`/`tfoot`).

### Reader behaviour

- **Opening:** K in the world opens the last page read (the main page "Monumenta Wiki" the first time); `/wiki <page>` opens a page (suggestions come from `opensearch`, completed on the game thread). The screen is opened with `MinecraftClient.send` so the closing chat screen doesn't close it.
- **Item lookup:** `HandledScreenMixin` injects at the first `KeyBinding.matchesKey` in `HandledScreen.keyPressed` (the inventory-key check). Vanilla reaches it only after `super.keyPressed` turned the key down, so text fields (anvil, creative search, recipe book) keep their K. The item's name (formatting stripped) goes through `WikiClient.resolve`. From the player's own inventory the reader returns to it on close; any other container is closed first with `closeHandledScreen()` (tells the server), and the reader returns to the game.
- **Loading:** `Pages.load` → convert on `Util.getMainWorkerExecutor()` → shown on the game thread if it's still the latest request. `missingtitle` → `resolve` (search) → that page, else "not found".
- **Layout:** in GUI pixels, content at most 560 wide. Header = title (H1), "Monumenta Wiki ↗ · CC BY-SA 4.0 ↗" (attribution + licence, both links), a "saved copy" note when stale. Laid out again on resize, on a collapsible/tab click (same scroll), and when a picture of guessed size has loaded (`Laid.unknownSizes` + `WikiImages.version()`).
- **Infobox float:** when the column is at least 2 × 220 + 20 wide, the infobox sits on the right; paragraphs, headings, lists, indents and rules flow beside it, anything else (tables, tabs, galleries, collapsibles, pictures) starts below it.
- **Wrapping:** breaks only at spaces. Text that touches the previous word without a space (a link and its punctuation, a style change mid-word, a non-breaking space) is `glued` and moves to the next line with it; only a group wider than the whole line is broken by characters. Spaces at line ends are dropped.
- **Tables:** column widths from content (min = widest unbreakable group, capped at 100; max = unwrapped), squeezed proportionally when too wide; grid slots no cell covers get the cell colour.
- **Pictures:** wiki px × 0.5 (`Layout.IMAGE_SCALE`); inline pictures up to 32 px tall are icons at text height. Colours that would be unreadable (contrast < 2) on their background fall back to ours.
- **Links:** page → navigate (history); `#anchor` on this page → scroll; `Special:`/`Category:` and external → browser through `ConfirmLinkScreen`; missing → nothing, tooltip explains.
- **Bookmarks:** stored in `config/` (not the cache), so CakeUpdater's config backups cover them; canonical titles (after redirects), matched with `Titles.normalize`. The ☰ dropdown is drawn above the page like the search suggestions, scrolls with the wheel, and closes on Esc or a click elsewhere.
- **Keys in the reader:** K closes (unless typing), Ctrl+D bookmark, Ctrl+F search, Enter submits (exact suggestion, else `resolve`), Esc leaves the search box first, Backspace/mouse 4 back, mouse 5 forward, arrows/PgUp/PgDn/Space/Home/End scroll.

### Translation (`translate/`, with CakeText Translator 0.3.0+)

- **Soft dependency:** `suggests caketext-translator >=0.3.0`; `CakeTextBridge.find()` looks up `io.github.harrythecake.caketext.translator.api.TranslatorApi` with `MethodHandles` (`version()` ≥ 1, `active`, `changes`, `translate`), so CakeWiki builds and runs without CakeText. Any failed call turns translation off for the session and logs once. Surface `wiki`, which the player can turn off in CakeText's "Where to translate".
- **Hook:** `Layout.page(..., UnaryOperator<List<Inline>> words)` passes every text line of the page through `words` in `tokens()` (so measuring and drawing see the same text); the header (title, attribution) and the infobox title are laid out without it: they're the page's name. Block identities don't change, so `ViewState` (open collapsibles, tabs) survives re-translation.
- **What stays in English (`PageTranslator.kept`):** links of any kind (a link split by a style change is one name), coloured runs of up to 4 words with a letter (tiers, regions, item names are coloured on this wiki; longer coloured text such as lore is translated), inline pictures. Kept parts reach CakeText as `{n}` stand-ins and never reach an engine; they come back wherever the translation puts them. Numbers go through CakeText's `{~}`.
- **Units:** a line is split at `LineBreak`s, then into sentences (`. ! ? : ;` + spaces + a capital, digit, quote or bracket), each translated and cached alone. Plain runs merge; the merged text takes the style covering most of it (bold/italic inside a sentence is lost in translation).
- **Not translated yet** → the original line (the same list object); CakeText queues it. `WikiScreen.ensureLayout` polls `changes()` and lays out again at most every 500 ms when it moved (`PageTranslator.refresh()` drops the memo). An answer that can't be mapped back (a kept index out of range, an empty part) shows the original.
- **UI:** 文 button left of ☆, only with CakeText installed (gold = translated; tooltip says when CakeText has wiki pages off); per session in `Reader.translate`, default on. A muted header line says the page is translated and that linked names stay in English.
- **Line breaking for Chinese** (`Layout.word`/`breakBetween`): a line may break between two CJK characters (Han, kana, Hangul, CJK punctuation, full-width forms), not before closing punctuation (`，。、；：！？）」』】》…％` and ASCII closers) or after opening punctuation. English still breaks only at spaces. This also helps Chinese text already on the wiki.
- Not tried in game yet (needs CakeText 0.3.0 in the same instance).

### Measured on the live wiki (2026-10-28)

Fetch about 0.3 s per page. Conversion is under 50 ms for most pages, but some item pages are 1.1 MB (Turtle Set, Snegovik Boots) because of one huge navigation box; those take 100–200 ms. So: convert off the render thread, and lay out a collapsible only when it's opened. Navigation boxes use dark backgrounds (`#0D091A`) with light text, so the reader uses a dark background. Inline item icons in text are common.

## Tests

`JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` runs the offline tests (hand-written HTML in the wiki's shapes, fake transport, temp-dir cache).

Opt-in, against the real wiki (`LiveWikiTest`, skipped by default):
- `./gradlew test --tests '*LiveWikiTest.convertsRealPages' -Dcakewiki.liveTest=true` converts known and random pages and prints sizes, block counts and timings.
- `... --tests '*LiveWikiTest.dumpPage' -Dcakewiki.liveTest=true -Dcakewiki.dump="<title>"` prints a page as an outline (`DocumentDump`) for checking by eye.

Screenshot tour in the dev client (opens a game window; `run/options.txt` presets GUI scale 3 and skips the accessibility screen):
`./gradlew runClient -PdevShot="Monumenta Wiki|item:Snegovik Boots|Moonscarred Clockblade@380|Turtle Set!open" --args="--width 1600 --height 900"` → `run/screenshots/cakewiki-<n>.png`. `item:` = item lookup, `@y` = scroll, `!open` = open all top-level collapsibles, `!marks` = bookmark the page and open the list (writes `run/config/cakewiki/bookmarks.json`).

## Rules

- The mod downloads no code. Wiki pages and images are data, fetched only when the player opens them.
- Translation goes only through CakeText Translator (whose settings decide the engine); CakeWiki itself sends wiki text nowhere.
- Every UI string in `en_us`, `zh_cn` and `zh_tw`, same keys.
- JUnit tests for all logic outside the game. Test HTML is hand-written in the wiki's shapes: no real wiki text in the repo (the live tests fetch it at run time).
