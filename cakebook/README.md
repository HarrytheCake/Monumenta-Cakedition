# CakeBook

A client-side Fabric mod for Minecraft **1.20.4**: a guide book and small wiki you read in game.

It was made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack, and any modpack or resource pack can ship its own books.

## What it does

- **Books.** Pages written in Markdown, with contents, links between pages, pictures, and the player's own key bindings shown in the text. Each page can be translated; missing translations fall back to English.
- **Your notes.** Write notes in game, insert where you're standing (area and shard with Unofficial Monumenta Mod), and link to book pages or other notes.
- **One search** over every page and note, in English and Chinese.

Open it with the book button in the pause menu or next to your inventory, a key (set it in Controls), `/cakebook`, or Mod Menu.

Languages: English, 简体中文, 繁體中文.

## Commands

All of them run on your side only and are never sent to the server.

| Command | Does |
|---|---|
| `/cakebook` | Open the book where you left it |
| `/cakebook open <page>` | Open a page, e.g. `help/notes` |
| `/cakebook search <words>` | Open the book with a search |
| `/cakebook note <title>` | Start a new note |
| `/cakebook reload` | Read the books again |
| `/cakebook folder` | Open the folder for your own books |

## Writing a book

A book is a folder of Markdown files, one per page and language:

```
<book>/book.json             {"title": {"en_us": "Pack guide"}, "order": 0, "icon": "minecraft:cake"}
<book>/en_us/index.md        the first page
<book>/en_us/mods/sodium.md  page "mods/sodium"
<book>/zh_cn/...             translations
<book>/images/map.png        pictures
```

Put it in `cakebook/books/` in the game folder (best for modpacks: pack updates replace the pages) or in a resource pack at `assets/<namespace>/cakebook/`. Links work like links between Markdown files on GitHub. The built-in **CakeBook help** book explains the format in full.

## Outside the game

CakeBook downloads nothing and sends nothing. It reads books from resource packs and the `cakebook/books` folder, saves notes in `config/cakebook/notes/`, opens web links in your browser only after you confirm, and `/cakebook folder` opens a folder in your file manager.

## Building

```
./gradlew build
```

Needs JDK 21 to run Gradle; the mod targets Java 17. The jar is in `build/libs/`.

## License

MIT, see [LICENSE](LICENSE).
