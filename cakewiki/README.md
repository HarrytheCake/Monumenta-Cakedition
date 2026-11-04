# CakeWiki

A client-only Fabric mod for Minecraft 1.20.4 that reads the [Monumenta Wiki](https://monumenta.wiki.gg/) inside the game. Made for the Monumenta Cakedition modpack.

**Status: in development (0.2.0, not released).**

## Other sites

The reader can also show other wikis and guides: list them in `config/cakewiki/sites.json` (MediaWiki sites, or static sites built with VuePress 2). Without that file it reads only the Monumenta Wiki. With more than one site, the ⇄ button switches between them and `/wiki <id>:<page>` opens a page of a given site. Each site's own name and license are shown on its pages; only add sites whose content you may read this way.

```json
{
  "sites": [
    {"id": "monumenta", "type": "mediawiki", "name": "Monumenta Wiki", "url": "https://monumenta.wiki.gg",
     "mainPage": "Monumenta Wiki", "license": "CC BY-SA 4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/"},
    {"id": "guide", "type": "vuepress", "name": "Some Guide", "url": "https://guide.example.org", "license": "© Its Author"}
  ]
}
```

## Translation

With [CakeText Translator](https://github.com/HarrytheCake/CakeText) 0.3.0+ installed (optional), pages are shown in Simplified or Traditional Chinese: its string files first, then whichever engine it uses. Linked names (items, places, bosses, other pages) and short coloured names stay in English, the way they're written in game; the page title and the infobox title too. The 文 button in the bar switches between translation and original. Without CakeText the reader works the same, in English.

## Wiki content

Pages are fetched from monumenta.wiki.gg when you open them. The mod contains no wiki text. Wiki text is licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) by the wiki's contributors; every page in the reader names its source and links back to it.

## Building

```
./gradlew build
```

Needs JDK 21 to run Gradle; the mod targets Java 17. The jar is in `build/libs/`.

## License

MIT (see `LICENSE`). Bundles [jsoup](https://jsoup.org/) (MIT).
