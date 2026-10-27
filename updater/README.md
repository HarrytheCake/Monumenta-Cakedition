# Cakedition Updater

A client-side Fabric mod for Minecraft **1.20.4** that keeps a [packwiz](https://packwiz.infra.link/) modpack and its Modrinth resource packs up to date, from inside the game.

It was made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack, and any packwiz pack can use it through its config file.

## What it does

**Modpack updates.** Each time you join a world or server, it checks whether the published pack has changed. If it has, a chat message offers two buttons:

- **Update when I close the game myself**: play on, and the update runs after you quit.
- **Update now (closes the game)**: disconnects cleanly, quits, and updates.

After the game exits, a terminal window runs packwiz-installer and tells you when you can start Minecraft again. The mod never restarts the game for you.

**Resource pack updates.** If a newer Modrinth version of an enabled resource pack exists (MonumentaRP by default), a separate chat message offers to update it. One click downloads it, checks its hash, puts it in the same place in your pack list and reloads resources. No restart needed.

**Backups.** Before every modpack update your server list and configs are saved, whether the update came from this mod, Prism's packwiz pre-launch command or your launcher updating a `.mrpack`. After the update a chat message offers **[Restore server list]** and **[Restore configs]**, and the Mod Menu settings button (or `/cakeupdate backups`) lists every backup. Configs are restored as the game closes, after every mod has saved its own files; the files a restore replaces are saved first. Backups live in `cakeupdater-backups/` in the game folder.

**Default files.** Files in `config/cakeupdater/defaults/` are copied into the game folder when they don't exist yet, like YOSBR does for configs. Ship `servers.dat` this way in a `.mrpack`, and a launcher update will never replace a player's server list.

**Updating a `.mrpack` install.** packwiz-installer only manages files it installed itself, so on a `.mrpack` install's first update it would leave renamed jars next to their new versions (two copies of a mod, and the game won't start) and reset optional mods to the pack's defaults. Ship `config/cakeupdater/adoption.json` in the `.mrpack`: every file it installs, in `packwiz.json`'s form (the Cakedition pack's `tools/mrpack.py` writes it). Before that first update the mod writes `packwiz.json` from it, with each optional mod on exactly when its file is there, so mods update in place, removed mods go, the player's choices stay and files the pack didn't ship are never touched.

Chat text and the updater window are in English, Simplified Chinese and Traditional Chinese.

## Requirements

- Minecraft 1.20.4, Fabric Loader 0.15+, Java 17+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the backups screen (`/cakeupdate backups` works without it)
- For modpack updates: an install made with packwiz-installer (it reads `packwiz.json`), or a `.mrpack` install that includes `config/cakeupdater/pack-version.txt` (and `adoption.json`, see above)
- For modpack updates: `packwiz-installer-bootstrap.jar` in the game folder. A packwiz install already has it, because the launcher's pre-launch command runs it. The mod never downloads it; if it's missing, the chat offers **[Open game folder]** and **[Download page]** instead of the update buttons.

## Commands

All are client-side; the chat buttons run them for you.

| Command | Effect |
|---|---|
| `/cakeupdate` | Show the update options, or "up to date" |
| `/cakeupdate onclose` | Update after you quit |
| `/cakeupdate now` | Quit now and update |
| `/cakeupdate cancel` | Cancel `onclose` |
| `/cakeupdate folder` | Open the game folder |
| `/cakeupdate backups` | Open the backups screen |
| `/cakeupdate restore servers <backup>` | Restore a backup's server list |
| `/cakeupdate restore configs <backup>` | Restore a backup's configs when the game closes |
| `/cakeupdate restore cancel` | Cancel a config restore |
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
  ],
  "backups": { "enabled": true, "keep": 5 }  // backups kept per kind
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

## License

[MIT](LICENSE)
