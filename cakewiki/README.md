# CakeWiki

A client-only mod that reads the [Monumenta Wiki](https://monumenta.wiki.gg/) and other wikis inside the game. Made for the Monumenta Cakedition modpack.

**Builds:** Fabric and NeoForge for Minecraft 1.20.4, 1.21.1 and 26.1.2, plus Fabric for 1.21.11 (release v0.2.0 has Fabric 1.20.4, 1.21.1, 1.21.11 and NeoForge 1.20.4 only). Pick `cakewiki-<loader>-<minecraft>-<version>.jar` from the [releases](https://github.com/HarrytheCake/CakeWiki/releases). Fabric needs Fabric API.

## Use

- **Y** opens the reader (where you left off); **Y** over an item in your inventory or a container opens that item's page. Change the key in Controls.
- `/wiki <page>` opens a page, with suggestions as you type.
- In the reader: search box, back/forward, bookmarks (☆, Ctrl+D), contents panel on sites that have one, links open in the reader or (outside links) in your browser after asking.

## Sites

On first launch CakeWiki writes `config/cakewiki/sites.json` with two sites:

- the **Monumenta Wiki** (monumenta.wiki.gg), offered on Monumenta's servers (`playmonumenta.com`);
- **Wynncraft中文攻略** (wynncraft.cn, by EternityTQ, shown with the author's permission), offered on Wynncraft (`play.wynncraft.com`).

On a server that some site belongs to, the reader offers only that server's sites (plus sites with no `servers`); in single-player, menus and other servers it offers every site. With more than one site offered, the ⇄ button switches between them; `/wiki <id>:<page>` opens a page of any site.

Edit the file to add, remove or re-tie sites (MediaWiki sites, or static sites built with VuePress 2), then restart the game. CakeWiki never changes the file once it exists; delete it to get the default back. Each site's name and license are shown on its pages; only add sites whose content you may read this way.

```json
{
  "sites": [
    {"id": "monumenta", "type": "mediawiki", "name": "Monumenta Wiki", "url": "https://monumenta.wiki.gg",
     "mainPage": "Monumenta Wiki", "license": "CC BY-SA 4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
     "servers": ["playmonumenta.com"]},
    {"id": "guide", "type": "vuepress", "name": "Some Guide", "url": "https://guide.example.org", "license": "© Its Author",
     "servers": ["play.example.org"]}
  ]
}
```

`servers`: server addresses the site belongs to; each also covers its subdomains, the port doesn't matter. Leave it out to offer the site everywhere.

## Translation

With [CakeText Translator](https://github.com/HarrytheCake/CakeText) 0.3.0+ installed (optional), pages are shown in Simplified or Traditional Chinese: its string files first, then whichever engine it uses. Linked names (items, places, bosses, other pages) and short coloured names stay in English, the way they're written in game; the page title and the infobox title too. The 文 button in the bar switches between translation and original. Without CakeText the reader works the same, in English.

## Wiki content

Pages are fetched from each site when you open them and cached on your PC. The mod contains no wiki text. Monumenta Wiki text is licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) by the wiki's contributors; every page in the reader names its source and links back to it.

## Building

```
./gradlew build
```

Gradle runs on JDK 25; each Minecraft version compiles with its own Java (17 for 1.20.4, 21 for 1.21.x, 25 for 26.1.2), found by Gradle's toolchains. Jars land in `build/libs/`. One version only: `./gradlew build -Pcake.versions=1.21.11`.

## License

MIT (see `LICENSE`). Bundles [jsoup](https://jsoup.org/) (MIT).
