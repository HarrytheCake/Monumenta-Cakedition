# CakeWiki

A client-only Fabric mod for Minecraft 1.20.4 that reads the [Monumenta Wiki](https://monumenta.wiki.gg/) inside the game. Made for the Monumenta Cakedition modpack.

**Status: in development (0.1.0, not released).**

## Wiki content

Pages are fetched from monumenta.wiki.gg when you open them. The mod contains no wiki text. Wiki text is licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) by the wiki's contributors; every page in the reader names its source and links back to it.

## Building

```
./gradlew build
```

Needs JDK 21 to run Gradle; the mod targets Java 17. The jar is in `build/libs/`.

## License

MIT (see `LICENSE`). Bundles [jsoup](https://jsoup.org/) (MIT).
