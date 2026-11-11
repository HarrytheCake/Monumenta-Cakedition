---
order: 2
---
# Writing a book

For modpack and resource pack makers. A book is a folder of Markdown files, one per page, so it can be written in any text editor and previewed on GitHub.

## Where books go

- **In a modpack:** `cakebook/books/<book>/` in the game folder. Pack updates replace the pages, and players don't have to turn anything on.
- **In a resource pack or mod:** `assets/<namespace>/cakebook/<book>/`. Only enabled packs count; a pack higher in the list replaces files of the same name.

When both have the same file, the books folder wins. `<book>` is the book's id: lower-case letters, digits, `-`, `_` and `.`.

## Inside a book

```
<book>/book.json             title, order, icon
<book>/en_us/index.md        the first page
<book>/en_us/keys.md         page "keys"
<book>/en_us/mods/index.md   heading of the "mods" folder
<book>/en_us/mods/sodium.md  page "mods/sodium"
<book>/zh_cn/...             the same pages in another language
<book>/images/map.png        pictures
```

`book.json`:

```
{
  "title": { "en_us": "Pack guide", "zh_cn": "整合包指南" },
  "order": 0,
  "icon": "minecraft:cake"
}
```

Books are listed by `order`, lowest first; the first one opens first. A missing page or title falls back to Simplified Chinese (for Traditional Chinese) and then to English, so a translation can be partial.

## A page

The page title is its first `# ` heading. Optional settings go at the very top:

```
---
title: Sodium
order: 5
hidden: true
---
```

`order` sorts pages and folders in the contents (then the title does). `hidden: true` keeps a page out of the contents; links and search still reach it.

## What Markdown works

- Headings `#` to `######`, paragraphs, `-` and `1.` lists (indent two spaces to nest), `>` tip boxes, `---` lines, code blocks between lines of three backticks, simple `|` tables
- `**bold**`, `*italic*`, `~~struck~~`, `` `code` ``
- Links: `[text](keys.md)` and `[text](../index.md)` work like on GitHub (relative to the page's folder); `/keys` starts at the book's root; `book:<book>/<page>` goes to another book; `note:<id>` to a player's note; `https://…` to a website
- `{key:<control id>}` shows the player's own key, e.g. `{key:key.inventory}` → {key:key.inventory}
- Images on a line of their own: `![Map](../images/map.png)`, or with a width: `![Map|160](../images/map.png)`. Paths work like links, `minecraft:textures/...` loads from any resource pack. PNG only; images from the web are not loaded.

## Testing

Edit the files, then run `/cakebook reload`; `/cakebook folder` opens the books folder. Problems with a book (a bad `book.json`, a misnamed folder) are written to the game log.
