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

- Opened by a key (default **H**; the shipped defaults leave only H and J free) and `/wiki <page>`.
- The same key over an item in a container screen opens that item's page (`WikiClient.resolve`: exact title or redirect, else the top search hit).
- Search box with suggestions as you type (`opensearch`).
- Whole wiki for now; may later be cut to the pages players need.
- CakeText Translator integration: later.
- Whether the pack ships it: decided later.

## Files

```
src/main/java/io/github/harrythecake/cakewiki/
├── CakeWiki.java           # client entrypoint
├── doc/                    # page model + HTML conversion (no Minecraft classes: unit-testable)
│   ├── Document, Block, Inline, Link, Image   # sealed model: Heading, Paragraph, ListBlock, ImageBlock, Gallery,
│   │                                          # Table/Row/Cell, Infobox (ImageTab, InfoRow, InfoHeader),
│   │                                          # Collapsible, Tabs/Tab, Indented, Rule; Text/InlineImage/LineBreak
│   ├── HtmlConverter.java  # jsoup → model
│   └── CssColor.java       # inline CSS colours → 0xRRGGBB
└── net/                    # no Minecraft classes either
    ├── WikiClient.java     # API calls (parse, opensearch, search, resolve); Transport interface for tests
    ├── WikiException.java  # MediaWiki error code (missingtitle, …) or "http"
    ├── PageCache.java      # gzipped JSON per title; key = SHA-1 of the MediaWiki-normalised title
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

### Measured on the live wiki (2026-10-28)

Fetch about 0.3 s per page. Conversion is under 50 ms for most pages, but some item pages are 1.1 MB (Turtle Set, Snegovik Boots) because of one huge navigation box; those take 100–200 ms. So: convert off the render thread, and lay out a collapsible only when it's opened. Navigation boxes use dark backgrounds (`#0D091A`) with light text, so the reader uses a dark background. Inline item icons in text are common.

## Tests

`JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` runs the offline tests (hand-written HTML in the wiki's shapes, fake transport, temp-dir cache).

Opt-in, against the real wiki (`LiveWikiTest`, skipped by default):
- `./gradlew test --tests '*LiveWikiTest.convertsRealPages' -Dcakewiki.liveTest=true` converts known and random pages and prints sizes, block counts and timings.
- `... --tests '*LiveWikiTest.dumpPage' -Dcakewiki.liveTest=true -Dcakewiki.dump="<title>"` prints a page as an outline (`DocumentDump`) for checking by eye.

## Rules

- The mod downloads no code. Wiki pages and images are data, fetched only when the player opens them.
- Every UI string in `en_us`, `zh_cn` and `zh_tw`, same keys.
- JUnit tests for all logic outside the game. Test HTML is hand-written in the wiki's shapes: no real wiki text in the repo (the live tests fetch it at run time).
