# Cakedition Updater — agent reference

Client-only Fabric mod (`cakeupdater`, package `io.github.harrythecake.cakeupdater`) for Minecraft **1.20.4**. It does three things:

1. **Modpack updates (packwiz).** It tells the player in chat when the published packwiz pack has changed. When the player chooses, it runs packwiz-installer **after the game closes**, in a terminal window, and then asks the player to start the game again. It never restarts the game itself; that's a deliberate design decision.
2. **Resource pack updates (Modrinth).** It tells the player in a **separate** chat message when a newer version of an enabled Modrinth resource pack exists. On click, it downloads the new version and reloads resources **live**, with no restart.
3. **Backups (1.4.0+).** It saves the server list and configs from before every modpack update, however the update happened (this mod, Prism's pre-launch packwiz, or a launcher updating a `.mrpack`), and lets the player restore them from a chat message after the update, Mod Menu or `/cakeupdate backups`.

The mod ships inside the Monumenta Cakedition pack as `pack/mods/cakeupdater.pw.toml`, a metafile pointing at the jar on this repo's GitHub release (`HarrytheCake/CakeUpdater`, built by CI). No built jar is committed anywhere. Other modpacks can reuse it through its config file.

## Files

```
updater/
├── build.gradle            # Fabric Loom 1.10.5; Java release 17; UTF-8 sources (lang strings are CJK)
├── gradle.properties       # MC 1.20.4, Yarn 1.20.4+build.3, Loader 0.16.9, Fabric API 0.97.3, mod_version
├── settings.gradle
├── gradlew / gradlew.bat   # Gradle 8.14.3 wrapper
├── .github/workflows/build.yml  # CI (active only in the standalone CakeUpdater repo): build + test, release on a new mod_version
└── src/main/
    ├── resources/
    │   ├── fabric.mod.json                     # environment: client; depends on fabric-api
    │   └── assets/cakeupdater/lang/            # en_us.json, zh_cn.json, zh_tw.json (same 52 keys)
    └── java/io/github/harrythecake/cakeupdater/
        ├── CakeUpdater.java          # entrypoint: events, /cakeupdate command, all chat text
        ├── CakeUpdaterPreLaunch.java # preLaunch entrypoint: leftover config restore, defaults (no Minecraft classes!)
        ├── Backups.java              # snapshot on close, update detection, restore, defaults, pruning (plain Java)
        ├── BackupsScreen.java        # backups list with restore buttons (Mod Menu / /cakeupdate backups)
        ├── ModMenuEntry.java         # Mod Menu's configure button → BackupsScreen (Mod Menu optional, compileOnly)
        ├── Config.java               # config/cakeupdater/config.json5 (template + lenient Gson load)
        ├── Net.java                  # shared HttpClient, User-Agent, GET/POST JSON helpers
        ├── UpdateCheck.java          # modpack: is the published pack.toml newer than what is installed?
        ├── ResourcePackUpdater.java  # resource packs: Modrinth lookup, download, swap, reload
        ├── HelperLauncher.java       # starts Helper in a terminal that outlives the game (per OS)
        └── Helper.java               # standalone main(): waits for game exit, runs packwiz-installer
```

`Helper` must stay **plain Java**: no Minecraft, Fabric or Gson classes, and no references to other mod classes. It runs in a separate JVM with only a temp copy of the mod jar on the classpath.

## Runtime flow

### Modpack update
```
ClientPlayConnectionEvents.JOIN (every join)
 └─ UpdateCheck.runAsync(packUrl)            GET pack.toml (background)
     ├─ packwiz.json exists → available = packFileHash.value != sha256(pack.toml)
     ├─ else pack-version.txt exists → available = its text != pack.toml `version`
     └─ else → not available (unknown install)
 └─ chat: "[<prefix>] Modpack update available (a → b) [Click to update]"   (once per remote hash)
      └─ /cakeupdate            → two buttons:
           ├─ /cakeupdate onclose  → updateOnClose = true   (+ [Cancel] → /cakeupdate cancel)
           └─ /cakeupdate now      → updateOnClose = true; client.send(disconnect + scheduleStop)
ClientLifecycleEvents.CLIENT_STOPPING
 └─ if updateOnClose → HelperLauncher.launch(gameDir, packUrl, language, prefix)
      1. copy mod jar → %TEMP%/cakeupdater-helper-*.jar   (so the update can replace the real jar)
      2. Windows: cmd /c start "<prefix> Updater" java.exe -cp <copy> Helper <pid> <gameDir> <url> <lang> <prefix>
         macOS:   temp .command script → open -a Terminal <script>
         Linux:   temp .sh script → first terminal on PATH (see LINUX_TERMINALS); none / Flatpak → run headless
Helper.main
 1. wait for the game PID to exit (+2 s for Windows file locks)
 2. delete old cakeupdater-helper-* temp files
 3. .minecraft/packwiz-installer-bootstrap.jar missing → print where to get it, exit 1 (never downloaded)
 4. java -jar packwiz-installer-bootstrap.jar -s client <url>   (GUI mode: optional-mod dialog works)
 5. print "Update finished! Start Minecraft again…" (or failure + exit code); wait for Enter
    no console → JOptionPane dialog instead
```

### Backups (server list and configs)
```
CLIENT_STOPPING (every close, before the updater starts)
 └─ Backups.snapshot(): servers.dat + config/** → cakeupdater-backups/last-close/ (only changed files, by size + mtime;
    deleted files removed). Skipped: config/yosbr/, cakeupdater/pack-version.txt, cakeupdater/defaults/,
    caketext/models/, and any file over 4 MB (caches such as mid/items.json).
onInitializeClient (every start)
 └─ Backups.detectUpdate(UpdateCheck.installedIdentity, pack-version.txt, keep)
     identity = "packwiz:<packwiz.json hash>" or "version:<pack-version.txt>"; kept in cakeupdater-backups/state.properties
     changed since the last start → last-close/ is moved to cakeupdater-backups/<yyyy-MM-dd_HH-mm-ss>/ (kind=update, from, to)
 └─ JOIN (once): chat "Modpack updated (a → b)…" [Restore server list] [Restore configs] [Backups]
/cakeupdate restore servers <backup>  → copied back now (MultiplayerScreen re-reads servers.dat when opened)
/cakeupdate restore configs <backup>  → cakeupdater-backups/restore-configs.txt + a shutdown hook: copied back after
                                        the game has closed and mods have saved; [Close Minecraft now] [Cancel]
CakeUpdaterPreLaunch                  → applies a restore the hook couldn't (crash/kill), then copies
                                        config/cakeupdater/defaults/** into the game folder where missing
```
- Every restore first saves the files it replaces in a `kind=restore` backup. Restoring configs only overwrites files the backup has.
- Pruning keeps the newest `backups.keep` of each kind, so restores never push out a pre-update backup.
- The `.mrpack` (`tools/mrpack.py`) ships `servers.dat` as `config/cakeupdater/defaults/servers.dat`, so a launcher's update never replaces the player's list. packwiz keeps it at the root with `preserve = true`.
- The first start with 1.4.0 only records the pack; there's nothing to back up yet.

### Resource pack update
```
JOIN → snapshot enabled pack names (render thread) → ResourcePackUpdater.check (background)
 1. sha1 of each enabled file/*.zip (cached by size + mtime)
 2. POST /v2/version_files {hashes, algorithm: sha1} → which zip is which Modrinth version
 3. per configured project: GET /v2/project/{p}, GET /v2/project/{p}/version?game_versions=[…]
    newest allowed version_type, by date_published; pending if newer than installed
 → chat per pack: "[<prefix>] Resource pack update: <title> a → b [Update resource pack]"   (once per pending set)
/cakeupdate resourcepacks → ResourcePackUpdater.apply
 1. download to resourcepacks/<file>.part, check sha512, move into place (skip if already present + valid)
 2. render thread: scanPacks(); replace old "file/x.zip" with the new one AT THE SAME INDEX;
    setEnabledProfiles(); options.refreshResourcePacks(manager)  → saves options.txt + reloads
```
The old zip is **kept, disabled**. packwiz-installer manages that file: deleting it would make the installer download it again. The next pack release that bumps the metafile removes it.

## Commands (client-side only, never sent to the server)

| Command | Effect |
|---|---|
| `/cakeupdate` | Show the two modpack update options (or "up to date"; or, without the bootstrap jar, [Open game folder] + [Download page]) |
| `/cakeupdate onclose` | Update after the player quits |
| `/cakeupdate now` | Disconnect cleanly, quit, then update |
| `/cakeupdate cancel` | Undo `onclose` |
| `/cakeupdate folder` | Open the game folder (where packwiz-installer-bootstrap.jar goes) |
| `/cakeupdate backups` | Open the backups screen |
| `/cakeupdate restore servers <backup>` | Restore that backup's server list now |
| `/cakeupdate restore configs <backup>` | Restore that backup's configs when the game closes |
| `/cakeupdate restore cancel` | Cancel a pending config restore |
| `/cakeupdate quit` | Leave the world and quit (the button after scheduling a config restore) |
| `/cakeupdate resourcepacks` | Apply all pending resource pack updates |

The chat buttons are `ClickEvent.RUN_COMMAND` with these commands. Fabric's client-command API catches them before they're sent to the server.

## Config: `config/cakeupdater/config.json5`

The mod writes it with comments on first launch (`Config.TEMPLATE`). Gson reads it leniently, so `//` and `/* */` comments are allowed but **trailing commas aren't**. Fields the file leaves out keep the defaults below.

| Key | Default | Meaning |
|---|---|---|
| `chatPrefix` | `"Cakedition"` | Chat prefix `[…]`; also the updater window title |
| `packwiz.enabled` | `true` | Modpack update check on/off |
| `packwiz.packUrl` | Cakedition `packwiz` branch `pack.toml` | Published pack.toml URL |
| `resourcePacks[]` | one entry for MonumentaRP | Modrinth packs to keep updated (`[]` = off) |
| `resourcePacks[].enabled` | `true` | |
| `resourcePacks[].modrinthProject` | `"Yj53Rs56"` | Project ID or slug |
| `resourcePacks[].gameVersions` | `[]` | `[]` = the running MC version |
| `resourcePacks[].versionTypes` | `["release"]` | Accepted Modrinth version types |
| `backups.enabled` | `true` | Snapshot on close + backup before updates |
| `backups.keep` | `5` | Backups kept per kind (update / restore) |

On Windows, `packUrl` and `chatPrefix` pass through `cmd /c start`, so they must not contain `& ^ | < >`. On macOS and Linux everything is single-quoted (`HelperLauncher.shellQuote`).

## Translations

- In-game text: `Text.translatable("cakeupdater.…")`, with files in `assets/cakeupdater/lang/`. Each button key `k` has a tooltip key `k.hover`.
- **Invariant:** every language file has the same keys and the same number of `%s` in each value.
- The updater window can't use Minecraft's lang system, so `Helper.MESSAGES` holds its own en_us/zh_cn/zh_tw copies. The language comes from `client.options.language`, which the mod passes in. zh_hk uses zh_tw, anything else falls back to en_us, and it also switches to en_us if the console charset can't encode the text.
- To add a language, add a lang JSON **and** a `Helper.MESSAGES` entry.

## Build

```bash
cd updater
JAVA_HOME="<JDK 21>" ./gradlew build        # output: build/libs/cakeupdater-<mod_version>.jar (Java 17 bytecode)
```
**CI** (`.github/workflows/build.yml`, GitHub only runs it in the standalone `HarrytheCake/CakeUpdater` repo, where this folder is the root): every push and pull request builds and tests on JDK 21 and uploads the jar as a workflow artifact. A push to `main` whose `mod_version` has no `v<mod_version>` release yet creates that release with `cakeupdater-<mod_version>.jar` attached. Pushing again with the same version builds but releases nothing.

To release inside the pack:
1. Bump `mod_version` and build/test locally.
2. Commit, then push this folder: `git subtree push --prefix=updater updater main` (from the repo root).
3. Wait for the CI release `v<mod_version>` (https://github.com/HarrytheCake/CakeUpdater/releases).
4. From the repo root: `python tools/packwiz_sync.py github mods HarrytheCake/CakeUpdater v<mod_version>`. It downloads the release jar, writes `pack/mods/cakeupdater.pw.toml` with its sha512, and runs `refresh`.
5. Commit and push the pack (`tools/push_all.py`). The local `main` publishes to the remote `packwiz` branch.

The pack must only point at a release that already exists, which is why step 4 waits for CI.

## Invariants and gotchas

- **Threading:**
  - Network and hashing run on background threads (`CompletableFuture`).
  - Anything that touches the client (chat, `ResourcePackManager`, options) goes through `client.send` or `client.submit`.
  - Read `getEnabledNames()` on the render thread, before going async.
- **Quitting from a command:** use `client.send(...)` (queued), not `execute`. Disconnecting inside the command handler breaks the connection mid-packet.
- **Temp jar copy:** Windows can't replace a jar a running JVM has open. Never run `Helper` from the real mod jar.
- **`java.exe`, not `javaw.exe`:** on Windows, `javaw` has no console. `HelperLauncher.consoleJava()` swaps it.
- **Java's `ProcessBuilder` starts children with no window (`CREATE_NO_WINDOW`).** That's why the Windows branch uses `cmd /c start`.
- **`packwiz.json` `packFileHash`** is exactly `sha256` of the `pack.toml` bytes (verified).
- **Modrinth** expects a project-identifying User-Agent (`Net.USER_AGENT`).
- **Pre-launch must not load Minecraft classes** (they'd load before mixins apply). `CakeUpdaterPreLaunch` and `Backups` use their own slf4j logger and only the inlined `CakeUpdater.MOD_ID` constant; never `CakeUpdater.LOGGER` or `Config` there.
- **Never restore `pack-version.txt`** or yosbr defaults: the update notice would come back and pack defaults would go stale (`Backups.SKIP`).
- **The mod downloads no code (1.3.0+).** It runs the `packwiz-installer-bootstrap.jar` a packwiz install already has in `.minecraft` (the launcher's pre-launch command needs it). Without it (e.g. a `.mrpack` install), `/cakeupdate`, `onclose` and `now` show [Open game folder] + [Download page] (`Helper.BOOTSTRAP_PAGE`) instead of updating, and `Helper` prints the same advice. Before 1.3.0 `Helper` downloaded bootstrap v0.0.3 itself; this was removed for mod-site rules. Its only network use is the update check (`pack.toml`, Modrinth API) and resource packs the player chooses to update.
- **Logging:** Minecraft's log format omits logger names, so search logs for message text ("Modpack update check", "Resource pack update", "Starting updater").

## Testing

Proven:
- Out of game:
  - update-check cases
  - config parsing (template and a creator's file)
  - the Modrinth check/download against the live API
  - the Windows `cmd /c start` launch with spaces and parentheses in paths
  - Helper end to end with exit code 0
  - the `sh` script quoting
- In game (Windows, Prism): the modpack "update now" flow and the resource pack 6.2.0 → 6.3.0 live swap.

- `Backups` out of game (2026-10-27, 41 checks in a fake game folder): snapshot skips, incremental sync and deletions, update detection once per change, server and config restore with safety backups, cancel, defaults only when missing, per-kind pruning.

Not proven:
- the backups chat message, screen and restores in game
- the "update when I close" button
- Terminal.app on macOS
- Linux terminals and Flatpak

**Out-of-game harness:** run the compiled classes (`build/classes/java/main`) with Gson, Fabric Loader, the `com.mojang` libraries and the Yarn-named `minecraft-merged` jar from `~/.gradle/caches/fabric-loom/minecraftMaven/…` on the classpath. Call package-private methods by reflection. Don't use the copy in the project's `.gradle/loom-cache`: that path is over 260 characters on Windows, and Java's classpath rejects the `\\?\` form.

## Possible future work (discussed, not started)

- **Forge/NeoForge:** move to Mojang mappings and a common module plus per-loader glue (entrypoint, client command registration, join/stop events, paths), probably with Architectury Loom. First check that `RUN_COMMAND` clicks reach Forge/NeoForge client commands.
- **Multiple MC versions (1.12.2 → 26.x):**
  - Make the core Java 8 compatible. `ProcessHandle`, `HttpClient`, records, text blocks and `HexFormat` would all need replacing.
  - Add thin per-version adapters, e.g. with Stonecutter.
  - Prioritise 1.20.1, 1.21.1 and the newest version.
  - 1.12.2 is the most expensive: no Brigadier, MCP names, an old resource pack system.
