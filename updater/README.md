# Cakedition Updater

A client-side Fabric mod for Minecraft **1.20.4** that keeps a [packwiz](https://packwiz.infra.link/) modpack and its Modrinth resource packs up to date, from inside the game.

It was made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack, and any packwiz pack can use it through its config file.

## What it does

**Modpack updates.** Each time you join a world or server, it checks whether the published pack has changed. If it has, a chat message offers two buttons:

- **Update when I close the game myself**: play on, and the update runs after you quit.
- **Update now (closes the game)**: disconnects cleanly, quits, and updates.

After the game exits, a terminal window runs packwiz-installer and tells you when you can start Minecraft again. The mod never restarts the game for you.

**Resource pack updates.** If a newer Modrinth version of an enabled resource pack exists (MonumentaRP by default), a separate chat message offers to update it. One click downloads it, checks its hash, puts it in the same place in your pack list and reloads resources. No restart needed.

Chat text and the updater window are in English, Simplified Chinese and Traditional Chinese.

## Requirements

- Minecraft 1.20.4, Fabric Loader 0.15+, Java 17+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- For modpack updates: an install made with packwiz-installer (it reads `packwiz.json`), or a `.mrpack` install that includes `config/cakeupdater/pack-version.txt`

## Commands

All are client-side; the chat buttons run them for you.

| Command | Effect |
|---|---|
| `/cakeupdate` | Show the update options, or "up to date" |
| `/cakeupdate onclose` | Update after you quit |
| `/cakeupdate now` | Quit now and update |
| `/cakeupdate cancel` | Cancel `onclose` |
| `/cakeupdate resourcepacks` | Apply pending resource pack updates |

## Using it in your own modpack

On first launch the mod writes `config/cakeupdater/config.json5` with comments. Ship your own copy in the pack to point it at your pack:

```json5
{
  "chatPrefix": "MyPack",            // chat prefix and updater window title
  "packwiz": {
    "enabled": true,
    "packUrl": "https://raw.githubusercontent.com/you/your-pack/main/pack.toml"
  },
  "resourcePacks": [                 // [] turns resource pack updates off
    {
      "enabled": true,
      "modrinthProject": "Yj53Rs56", // Modrinth project ID or slug
      "gameVersions": [],            // [] = the running Minecraft version
      "versionTypes": ["release"]    // "release", "beta", "alpha"
    }
  ]
}
```

Comments are allowed; trailing commas are not. On Windows, `packUrl` and `chatPrefix` must not contain `& ^ | < >`.

## Platform status

Tested in game on Windows (Prism Launcher). The macOS (Terminal.app) and Linux terminal paths are written and unit-checked but not yet tried on real machines. With no terminal available (e.g. Flatpak), the updater runs without a window and shows a dialog when it's done.

## Building

```bash
./gradlew build     # needs JDK 21 to run Gradle; the jar targets Java 17
```

The jar is written to `build/libs/cakeupdater-<version>.jar`.

## Releases

GitHub Actions builds and tests every push. When `mod_version` in `gradle.properties` changes on `main`, it publishes release `v<version>` with the jar. Download the mod from [Releases](https://github.com/HarrytheCake/CakeUpdater/releases).

This repository is a mirror of the [`updater/` folder](https://github.com/HarrytheCake/Monumenta-Cakedition/tree/packwiz/updater) of Monumenta-Cakedition, where development happens. Developer notes are in [AGENTS.md](AGENTS.md).
