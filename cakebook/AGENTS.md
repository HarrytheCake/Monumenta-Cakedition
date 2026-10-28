# CakeBook — agent reference

Client-only Fabric mod (`cakebook`, package `io.github.harrythecake.cakebook`) for Minecraft **1.20.4**: a guide book and small local wiki read in game. It shows **books** (Markdown pages shipped by a modpack, a resource pack or a mod), keeps the player's own **notes**, and has one **search** over both. Started 2026-10-28.

Decisions made with the user (2026-10-28):
- Content: a pack guide, a Monumenta gameplay guide (both written by the pack, not by the mod) and player notes. Not copied wiki pages.
- Opened from the pause menu button, an inventory button, a key binding (unbound by default), `/cakebook` and Mod Menu.
- Pages are Markdown files per language, read from resource packs **and** from `<game folder>/cakebook/books/`. The jar is a reusable engine plus a short help book; the Cakedition pages ship with the pack, so text changes need only a pack update. The books folder exists because a resource pack in the pack would have to be turned on in each player's `options.txt`, which existing installs never get (yosbr only copies missing files).
- Notes: write/edit, insert location (UMM area + shard + coordinates), links between notes and pages, search.
- License MIT. Ships in the Cakedition pack **on by default** once released (Monumenta's mod rules must allow it; the user asks them).

## Files

```
cakebook/
├── build.gradle / settings.gradle / gradle.properties   # same stack as CakeUpdater (Loom 1.10.5, Yarn 1.20.4+build.3)
├── .github/workflows/build.yml   # CI in the standalone CakeBook repo: build + test, release v<mod_version>
└── src/main/
    ├── resources/
    │   ├── fabric.mod.json, cakebook.mixins.json
    │   └── assets/cakebook/
    │       ├── lang/{en_us,zh_cn,zh_tw}.json     # same keys in all three
    │       ├── icon.png                          # 64×64, 16×16 pixel art ×4 like CakeText's
    │       └── cakebook/help/…                   # the built-in "CakeBook help" book (3 pages × 3 languages)
    └── java/io/github/harrythecake/cakebook/
        ├── Markdown.java      # plain Java: the Markdown subset → Doc(meta, blocks of styled spans)
        ├── Links.java         # plain Java: link / image targets, GitHub-style relative paths
        ├── Book.java          # plain Java: one book, pages per language, table of contents
        ├── Library.java       # plain Java: all books + Builder (later files replace earlier ones)
        ├── Lang.java          # plain Java: language fallback (zh_hk → zh_tw → zh_cn → en_us), as in CakeUpdater
        ├── Search.java        # plain Java: one search over pages and notes
        ├── Notes.java         # plain Java: config/cakebook/notes/<id>.md
        ├── Config.java        # config/cakebook/config.json5
        ├── BookLoader.java    # resource manager + books folder → Library.Builder
        ├── Images.java        # PNG → texture, cached until the next reload
        ├── PageLayout.java    # Doc → positioned lines/boxes/images for one width; link click/hover lookup
        ├── RowList.java       # scrolling clickable rows (sidebar, link picker)
        ├── BookScreen.java    # the book: sidebar + page, history, note buttons
        ├── NoteEditScreen.java, PickPageScreen.java
        ├── MenuButtons.java   # pause menu + inventory book buttons
        ├── Location.java      # UMM area/shard by reflection (same calls as CakeText's UmmModule)
        ├── ModMenuEntry.java  # Mod Menu "configure" → the book (Mod Menu is compileOnly)
        ├── CakeBook.java      # entrypoint: reload listener, key, commands, buttons
        └── mixin/EditBoxWidgetAccessor.java, HandledScreenAccessor.java
```

The classes marked plain Java use no Minecraft classes (Gson in `Library` is fine), so the JUnit tests run them directly.

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
- Buttons: pause menu (only when the full menu shows) 24 px left of "Back to Game" (found by its translation key; falls back to 4,4); inventory (`InventoryScreen` only) right of the background, re-placed every frame because the recipe book moves it.
- Commands (client-side): `/cakebook`, `open <page>` (suggests every `book/path` and `note:id`), `search <text>`, `note <title>`, `reload`, `folder`.

## Invariants

- The mod downloads nothing and sends nothing; web links only open after the vanilla confirm screen.
- Every UI string is in en_us, zh_cn and zh_tw with the same keys; the help book has the same pages in all three (`BundledHelpTest`).
- No real player names or collected chat in tests or docs.

## Tests

`JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` runs 28 JUnit tests: `MarkdownTest`, `LinksTest`, `LibraryTest`, `SearchAndNotesTest`, `BundledHelpTest` (every help page in all three languages, every link and image resolves).

**Checking a pack's books:** `./gradlew test -Dcakebook.checkBooks=<folder holding one sub-folder per book>` runs the same checks on it (skipped without the property). Key ids in `{key:…}` aren't checked there; compare them with the `key_` lines of a live `options.txt`.

**In game (2026-10-28, test instance, alongside CakeChat):** the user reported "works well"; the log shows `2 book(s): [cakedition, help]` and nothing from CakeBook at WARN or above. Not yet exercised: notes (no `config/cakebook/notes/` was created, so no note was saved), Insert location on Monumenta, images (no book has one yet).

## The Cakedition pack guide

Drafted 2026-10-28 in the dev instance at `.minecraft/cakebook/books/cakedition/` (local, not tracked; a copy is in the test instance): book.json + 13 pages per language (index, first-steps, keys, updates, performance-tips, help, mods/{index,monumenta,performance,visuals,hud,chat,convenience,tools}). It passes `-Dcakebook.checkBooks`, and all 21 key ids exist in the live `options.txt`. Facts come from the pack's metafiles, Modrinth summaries, the shipped `options.txt` key lines, the mods' own lang files and CakeUpdater's lang files (button names). When CakeBook ships, the folder goes to `pack/cakebook/books/cakedition/**` (owned by the session that owns `pack/`). The Monumenta gameplay guide is not started: the user writes it, or it's written with them.

## Release

Same as CakeUpdater: bump `mod_version`, commit `cakebook/` only, push (only when the user asks), CI publishes `v<mod_version>` with the jar; then `packwiz_sync.py github mods HarrytheCake/CakeBook v<version>`. Standalone repo `HarrytheCake/CakeBook` (+ GitLab copy) still to be created by the user.
