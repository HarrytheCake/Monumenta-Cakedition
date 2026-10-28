# CakeChat

A client-side Fabric mod for Minecraft **1.20.4** that improves the chat. Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack; it works on any server.

**Early development.** So far: settings for history, compacting duplicate messages and timestamps. Planned: chat windows and tabs with your own rules, unlimited history that survives server switches, regex search, copying messages, and importing your ChatPlus setup.

Settings: Mod Menu's configure button, or `/cakechat`. English, Simplified Chinese and Traditional Chinese.

## Requirements

- Minecraft 1.20.4, Fabric Loader 0.15+, Java 17+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl) 3.6+
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu)

## Building

```
./gradlew build
```

The jar is in `build/libs/`. Building needs JDK 21; the mod runs on Java 17+.

## License

MIT, see [LICENSE](LICENSE).
