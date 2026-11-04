# CakeWiki: reference for agents

In-game reader for https://monumenta.wiki.gg/, plus any other MediaWiki or VuePress site the player lists in `config/cakewiki/sites.json` (client-only Fabric 1.20.4 mod, id `cakewiki`, MIT). This folder is the source of truth; the standalone repo `HarrytheCake/CakeWiki` is written only by `git subtree push` / `tools/push_all.py` from the Monumenta-Cakedition repo. Keep this file current when changing the mod.

## Design

- **Fetch:** MediaWiki API at `https://monumenta.wiki.gg/api.php`, `action=parse` (rendered HTML). Send a descriptive User-Agent and cache on disk. Other sites come from the site list (see "Sites" below); everything after fetching is shared.
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
- **Ships in the pack since 2.3.0** (on by default; `pack/mods/cakewiki.pw.toml` → the GitHub release; no Modrinth project for now, the user's choice 2026-10-29).

## Files

```
src/main/java/io/github/harrythecake/cakewiki/
├── CakeWiki.java           # entrypoint: K key (KeyBinding "key.cakewiki.open"), /wiki [page] with live suggestions, lookupItem
├── Reader.java             # one per game: Sources (Site + Pages + converter per site, current one), WikiImages, Bookmarks, back/forward history, last page
├── Bookmarks.java          # config/cakewiki/bookmarks.json, newest first; unreadable file → bookmarks.json.bad
├── WikiScreen.java         # the reader: bar (◀ ▶ search ⟳ ↗), page area, loading/error states, links, tooltips
├── DevShots.java           # dev only: screenshot tour (runClient -PdevShot=…), inert in a normal game
│                           # (CakeWiki.target: "<site id>:<page>" for /wiki and the tour)
├── mixin/HandledScreenMixin.java  # K over an item in a container → its page
├── translate/              # CakeText Translator (optional); no Minecraft classes except the bridge
│   ├── Translator.java     # parts + keep flags → Strings/Integers (kept-part index), or null; changes() counter
│   ├── PageTranslator.java # a line of inlines → its translation; what's kept; per-line memo until refresh()
│   └── CakeTextBridge.java # TranslatorApi (CakeText 0.3.0+) by reflection; null when not installed
├── layout/                 # no Minecraft classes: unit-tested with fake Metrics
│   ├── Metrics.java        # text width, line height, loaded picture size, labels
│   ├── Layout.java         # Document → Laid; colours (dark theme), tables, infobox, collapsibles, tabs, wrapping
│   ├── Laid.java           # positioned Run/Pic/Fill/Control + anchors + height; linkAt/controlAt
│   ├── ViewState.java      # open collapsibles and selected tabs, keyed by block identity
│   └── Contents.java       # contents panel: open groups (by identity), reveal(page), visible rows
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
    ├── Site.java           # one site: info, ready, parse, suggest, resolve, pageUrl, link (URL → page link), navigation, browserOnly
    ├── NavItem.java        # a contents entry: text, page key, collapsible, children
    ├── SiteConfig.java     # config/cakewiki/sites.json → Entry list (Monumenta Wiki when missing/unusable); never written
    ├── WikiClient.java     # the MediaWiki Site: API calls (parse, opensearch, search, resolve); Transport interface for tests
    ├── VuePressSite.java   # the VuePress Site: HTML pages, title index from the route table / sitemap, kept on disk
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

### Sites (0.2.0, the user's request 2026-11-03)

- **Site list:** `config/cakewiki/sites.json`, `{"sites": [...]}`, comments allowed (Gson lenient). Entry: `id` (`[a-z0-9_-]`, for `/wiki <id>:<page>` and bookmarks), `type` (`mediawiki` default, or `vuepress`), `name` (attribution line), `url` (origin), `mainPage` (`Main Page` / `/`), `license` + `licenseUrl` (attribution; empty = not shown), `content` (VuePress: CSS selector of the text, default `#markdown-content`). Bad entries are skipped and logged; no file or nothing usable = just the Monumenta Wiki. The mod never writes the file and the pack ships none, so players see one site.
- **The first site is the default:** first opening, item lookups (K over an item switches to it). Each page's links stay on its own site.
- **Switching:** ⇄ button after ▶ (only with 2+ sites; tooltip names the current and next site) opens the next site's main page. `/wiki <id>:<page>` (`/wiki <id>:` = main page); suggestions complete site ids and that site's titles. A page's site is in history (`Visit.site`), `reader.last`, and bookmarks (`site` field; entries without one = `monumenta`). The ☰ list shows the current site's bookmarks.
- **Cache:** the Monumenta Wiki keeps `cakewiki/cache/pages/`; other sites `cakewiki/cache/sites/<id>/pages/` and `index.json`.
- **VuePress sites** (2.x, Theme Hope shapes): no API. Page = GET `<url><path>`; text = `content` (an empty one, as on a home layout, falls back to `<main>`); links and pictures made absolute before caching; SVG pictures dropped (not decodable); `.vp-hero-actions` buttons get ` · ` between them; title from `.vp-page-title h1`, else `<title>` before ` | `. HTTP 404 → `missingtitle`.
- **VuePress names:** the index comes from the home page's `app-*.js` route table (`["/path",{loader:…,meta:{title:"…"}}]`, read as text with a regex, never run; paths percent-decoded), else `sitemap.xml` (paths only). A page's key is its title, unless the title is empty or already taken (then its path); `/…` keys always work. Titles match ignoring case. Suggestions/resolve: titles starting with the text, then containing it, then paths containing it. The index is loaded on first use (`Site.ready()`, which `Pages.load` waits for, so cached pages link the same way), kept 24 h on disk, and used however old when offline; without any index, pages go by path.
- **Contents panel** (the site's sidebar; the user's request 2026-11-03): `Site.navigation(title)` → a `NavItem` tree (text, page key, collapsible, children); MediaWiki has none. VuePress: Theme Hope's sidebar config (a `JSON.parse('…')` string in the app script, `sidebar` or `locales["/"].sidebar`, keyed by part of the site such as `/guide/`) plus its folder lists for `"children":"structure"` groups (an object `{"/dir/":["page",…]}` in the script); string entries and `link`s resolve against `prefix` like the theme (`x` → `x.html` or `x/`, `README`/`index` → folder), entries naming no page are left out. Kept in `index.json` (`format` 2; an older file is fetched again). The longest matching prefix picks the tree; outside them (e.g. the home page) there is none. In the reader: ▤ button first in the bar (only when the page has contents; gold = open; open by default, per session) toggles a panel on the left of the page (`layout/Contents`: non-collapsible groups always open, groups on the way to the shown page opened, shown page highlighted and scrolled into view; open groups survive between pages while the tree is the same). Click: a group without a page or its ▸/▾ toggles, a page opens (on the shown page's site); the wheel scrolls the panel; cut-off names get a tooltip. While open, the page is narrower and starts right of the panel.
- **Not possible:** pages whose text the site renders only in the browser (empty in the static HTML) show just their header lines.

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
`./gradlew runClient -PdevShot="Monumenta Wiki|item:Snegovik Boots|Moonscarred Clockblade@380|Turtle Set!open" --args="--width 1600 --height 900"` → `run/screenshots/cakewiki-<n>.png`. `item:` = item lookup, `@y` = scroll, `!open` = open all top-level collapsibles, `!marks` = bookmark the page and open the list (writes `run/config/cakewiki/bookmarks.json`), `<site id>:<page>` = a page of another site in `run/config/cakewiki/sites.json` (`<site id>:` = its main page).

VuePress sites, opt-in against a real site (nothing site-specific in the repo): `./gradlew test --tests '*LiveVuePressTest' -Dcakewiki.liveTest=true -Dcakewiki.vuepress=<url> [-Dcakewiki.pages=8 -Dcakewiki.from=0 | -Dcakewiki.dump=<title or /path> | -Dcakewiki.nav=<title or /path>]` (`nav` prints the contents tree) lists the pages and converts them. In Git Bash set `MSYS_NO_PATHCONV=1`, or `/paths` in `-D…`/`-P…` arguments become Windows paths.

## Rules

- The mod downloads no code. Wiki pages and images are data, fetched only when the player opens them. A VuePress site's app script is read as text for its route table, never run.
- Translation goes only through CakeText Translator (whose settings decide the engine); CakeWiki itself sends wiki text nowhere.
- Every UI string in `en_us`, `zh_cn` and `zh_tw`, same keys.
- JUnit tests for all logic outside the game. Test HTML is hand-written in the wiki's shapes: no real wiki text in the repo (the live tests fetch it at run time).
