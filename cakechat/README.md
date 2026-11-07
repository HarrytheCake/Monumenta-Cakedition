# CakeChat

A client-side mod that improves the chat, for **Fabric and NeoForge** on Minecraft **1.20.4, 1.21.1 and 26.1.2**. Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack; it works on any server.

Chat windows and tabs with your own rules, unlimited history that survives server switches, compacted duplicate messages, timestamps, a find bar (Ctrl+F, plain or regex), copying a message (right-click), peek and hide keys. Chat windows stay where they are until you unlock them (settings → General, or `/cakechat edit`). Cakedition Updater can import your ChatPlus setup.

Settings: Mod Menu's configure button (Fabric), the mod list's Config button (NeoForge), or `/cakechat`. English, Simplified Chinese and Traditional Chinese.

## Requirements

- Minecraft 1.20.4, 1.21.1 or 26.1.2
- Fabric: Fabric Loader 0.15+, [Fabric API](https://modrinth.com/mod/fabric-api); optional [Mod Menu](https://modrinth.com/mod/modmenu)
- NeoForge: 20.4 (1.20.4), 21.1 (1.21.1) or 26.1 (26.1.2)
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl) for your loader and version (3.6+ on 1.20.4, 3.9+ on 26.1.2)
- Not together with ChatPlus (CakeChat replaces it)

## Building

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build
```

Builds Fabric and NeoForge jars for Minecraft 1.20.4, 1.21.1 and 26.1.2 into `build/libs/`
(`cakechat-<loader>-<mc>-<version>.jar`; `cakechat-<version>.jar` is the Fabric 1.20.4 one). Gradle runs on
JDK 25; each version compiles with its own Java (17, 21, 25). One version only: `-Pcake.versions=1.21.1`.

## License

MIT, see [LICENSE](LICENSE).
