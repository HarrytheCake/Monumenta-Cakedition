# Cakedition Updater — agent reference

Client-only Fabric mod (`cakeupdater`, package `io.github.harrythecake.cakeupdater`) for Minecraft **1.20.4**. It does three things:

1. **Modpack updates (packwiz).** It tells the player in chat when the published packwiz pack has changed. When the player chooses, it runs packwiz-installer **after the game closes**, in a terminal window, and then asks the player to start the game again. It never restarts the game itself; that's a deliberate design decision.
2. **Resource pack updates (Modrinth).** It tells the player in a **separate** chat message when a newer version of an enabled Modrinth resource pack exists. On click, it downloads the new version and reloads resources **live**, with no restart.
3. **Backups (1.4.0+).** It saves the server list and configs from before every modpack update, however the update happened (this mod, Prism's pre-launch packwiz, or a launcher updating a `.mrpack`), and lets the player restore them from a chat message after the update, Mod Menu or `/cakeupdate backups`.

The mod ships inside the Monumenta Cakedition pack as `pack/mods/cakeupdater.pw.toml`, a metafile pointing at the jar on this repo's GitHub release (`HarrytheCake/CakeUpdater`, built by CI). No built jar is committed anywhere. Other modpacks can reuse it through its config file.

1.5.0 adds, on top of those three:
- **Urgent updates.** The published pack lists withdrawn versions in `config/cakeupdater/withdrawn.json`; an install on one gets a red notice with the reason on every join and a toast on the title screen. Never forced.
- **In-game changelog.** The pack ships `config/cakeupdater/changelog/<version>/<lang>.md`; the "Modpack updated" message shows what every version the update brought changed, with a [Changelog] screen.
- **Memory warning.** One chat warning when the game's max heap is outside 3–12 GB (configurable).

1.6.0 (user's request 2026-10-29, `docs/adr/0004`): **updates come from Modrinth first**, so they count as Modrinth downloads; then GitHub, then GitLab. The `.mrpack` carries the packwiz pack (`config/cakeupdater/packwiz/`, added by `tools/mrpack.py`), so the helper rebuilds it on disk and runs packwiz-installer as before. Also: the bootstrap runs with `--bootstrap-no-update` once `packwiz-installer.jar` exists, so an update needs no GitHub API call.

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
    │   └── assets/cakeupdater/lang/            # en_us.json, zh_cn.json, zh_tw.json (same 63 keys)
    └── (src/test/java/…)                       # JUnit 5: ChangelogTest, UrgentAndMemoryTest (run by `gradlew build`)
    └── java/io/github/harrythecake/cakeupdater/
        ├── CakeUpdater.java          # entrypoint: events, /cakeupdate command, all chat text
        ├── CakeUpdaterPreLaunch.java # preLaunch entrypoint: leftover config restore, defaults (no Minecraft classes!)
        ├── Backups.java              # snapshot on close, update detection, restore, defaults, pruning (plain Java)
        ├── Adoption.java             # .mrpack install's first update: packwiz.json from adoption.json (no MC classes)
        ├── BackupsScreen.java        # backups list with restore buttons (Mod Menu / /cakeupdate backups)
        ├── ModMenuEntry.java         # Mod Menu's configure button → BackupsScreen (Mod Menu optional, compileOnly)
        ├── Changelog.java            # changelog/<version>/<lang>.md: versions between two, language fallback, parse (plain Java)
        ├── ChangelogScreen.java      # scrollable changelog (/cakeupdate changelog, the [Changelog] button)
        ├── Withdrawn.java            # withdrawn.json next to the published pack.toml: is the installed version withdrawn?
        ├── MemoryCheck.java          # max heap outside the configured range → warn once per setting (plain Java)
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
 └─ UpdateCheck.runAsync(config.packwiz)     (background; the first source that answers wins)
     ├─ 1. Modrinth (packwiz.modrinth() set + pack-version.txt known):
     │     GET api.modrinth.com/v2/project/<p>/version?loaders=["fabric"]&game_versions=["<mc>"]
     │     → newest version_type "release" with a .mrpack primary file; available = Changelog.compare(it, installed) > 0;
     │       Result.mrpack = {versionId, url, sha512}, remoteHash = "modrinth:<versionId>"
     ├─ 2. each packwiz.packUrls() (packUrl, then fallbackUrls: GitHub, then GitLab): GET pack.toml
     │     ├─ packwiz.json exists → available = packFileHash.value != sha256(pack.toml)
     │     ├─ else pack-version.txt exists → available = its text != pack.toml `version`
     │     └─ else → not available (unknown install);  Result.packUrl = the address that answered
 │   └─ update available + pack-version.txt known → GET <pack.toml folder>/config/cakeupdater/withdrawn.json at each
 │      address in turn (404 / bad JSON everywhere = nothing withdrawn); installed version listed → urgent
 └─ chat: "[<prefix>] Modpack update available (a → b) [Click to update]"   (once per remote hash)
     urgent instead: red "Urgent update: version a was withdrawn. Please update to b." + the reason line, EVERY join
CLIENT_STARTED → the same check; urgent → SystemToast (PERIODIC_NOTIFICATION) on the TitleScreen, once per start
      └─ /cakeupdate            → two buttons:
           ├─ /cakeupdate onclose  → updateOnClose = true   (+ [Cancel] → /cakeupdate cancel)
           └─ /cakeupdate now      → updateOnClose = true; client.send(disconnect + scheduleStop)
ClientLifecycleEvents.CLIENT_STOPPING
 └─ if updateOnClose → Adoption.adopt(gameDir)   (only if packwiz.json is missing and adoption.json exists; see below)
                     → HelperLauncher.launch(gameDir, latest.mrpack, packUrls (the one that answered first), language, prefix)
                       (on Windows a source with & ^ | < > % " is left out: it would pass through cmd)
      1. copy mod jar → %TEMP%/cakeupdater-helper-*.jar   (so the update can replace the real jar)
      2. Windows: cmd /c start "<prefix> Updater" java.exe -cp <copy> Helper <pid> <gameDir> <url> <lang> <prefix>
                  [--mrpack <url> <sha512>] [--fallback <url>]...
         macOS:   temp .command script → open -a Terminal <script>
         Linux:   temp .sh script → first terminal on PATH (see LINUX_TERMINALS); none / Flatpak → run headless
Helper.main
 1. wait for the game PID to exit (+2 s for Windows file locks)
 2. delete old cakeupdater-helper-* temp files
 3. .minecraft/packwiz-installer-bootstrap.jar missing → print where to get it, exit 1 (never downloaded)
 4. for each source until one finishes (exit 0), saying which in the window:
     a. Modrinth: download the .mrpack, check sha512, MrpackPack.rebuild into %TEMP%/cakeupdater-pack-* (see below)
        → <that folder>/pack.toml; the folder is deleted afterwards
     b. the pack.toml URL, then each --fallback
    java -jar packwiz-installer-bootstrap.jar [--bootstrap-no-update] -s client <pack.toml path or URL>
    (GUI mode: optional-mod dialog works; --bootstrap-no-update when packwiz-installer.jar is already there)
 5. print "Update finished! Start Minecraft again…" (or failure + exit code); wait for Enter
    no console → JOptionPane dialog instead
```

### Updating from the `.mrpack` (1.6.0+, `MrpackPack`)
`tools/mrpack.py` puts the packwiz pack in the `.mrpack` at `overrides/config/cakeupdater/packwiz/`: `pack.toml`, `index.toml`, every `*.pw.toml` at its pack path, and `files.tsv` = one line per other pack file: `<path>	<sha256>	mrpack:<zip entry>` (bundled override; preserve=true files are at their `config/cakeupdater/defaults/` entry) or `	url:<url>` (files over 1 MB, i.e. the Paperbox zips, linked from raw.githubusercontent.com at the commit that last changed them). `rebuild` writes the metadata and every file into the work folder, checking each against its sha256; a `url:` file the install already has with the right hash is copied from the game folder, so only changed ones are downloaded. Paths that would land outside the work folder are refused. packwiz-installer reads a local `pack.toml` path (checked 2026-10-29: plain path and `file:` URL both install fully). A `.mrpack` without `files.tsv` (built before 1.6.0) fails the Modrinth source, and the helper goes on to GitHub.

### Adoption (1.4.1+): a `.mrpack` install's first update

packwiz-installer only manages files listed in its own `packwiz.json`, which a `.mrpack` install doesn't have. Read in its source (`DownloadTask.kt`, `UpdateManager.kt`) and reproduced end to end on 2026-10-28, its first run there:
- leaves a jar the pack has since renamed (a mod update) and adds the new one: **two copies of a mod, Fabric won't start**;
- leaves mods the pack removed;
- opens the optional-mod window with the pack's defaults, and deletes an optional mod the player had on unless they tick it again.

So `tools/mrpack.py` ships `config/cakeupdater/adoption.json` = `{"files": {<index path>: <packwiz.json cachedFiles entry>}}` for every file the `.mrpack` installs: `hash` (the index's sha256), `linkedFileHash` + `cachedLocation` for metafiles, `isOptional` for optional ones (not `servers.dat`: the `.mrpack` puts it in defaults). `Adoption.adopt` writes `packwiz.json` from it: optional files are on (`optionValue: true`) when their file exists, else `{isOptional: true, optionValue: false}` (what packwiz-installer itself stores for an off option). **No `packFileHash`**, so `UpdateCheck.installedIdentity` stays `version:…` until packwiz-installer finishes and writes the hash; a failed update can't pass for a done one. Once `packwiz.json` exists, adoption never runs again.

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
 └─ JOIN (once): chat "Modpack updated (a → b)…", then up to 5 changelog items (all versions after a up to b,
    newest first) + "…and N more", then [Changelog] [Restore server list] [Restore configs] [Backups]
    (the changelog rides on this message, so it needs backups.enabled; a → a, or an unknown b, shows none)
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

### Changelog files (1.5.0+)
`config/cakeupdater/changelog/<version>/<lang>.md`, shipped in the pack, every version's folder kept forever (a player who skips versions sees them all). Lines: `**Heading**` or `## Heading` → heading; `- item` / `* item` → item (only these appear in chat); anything else → plain text; `---` dropped; links become their text, `**` and backticks are removed. Versions sort by their numbers (`2.10.0` > `2.9.1`, `2.3.0-beta` < `2.3.0`). Language: the game's, then `zh_hk → zh_tw`, any `zh_* → zh_cn`, then `en_us`. The pack's CI (`.github/workflows/mrpack.yml`) puts `en_us.md` + `zh_cn.md` of the released version at the top of the GitHub release notes.

### Withdrawn versions (1.5.0+)
`config/cakeupdater/withdrawn.json`: `{"<version>": {"en_us": "<reason>", "zh_cn": "…", "zh_tw": "…"}}` (a plain string means English; `{}` means no reason). Always read from the **published** pack (the folder of `packwiz.packUrl`), never the local copy, so a version can be withdrawn after release. Urgent only when an update is available. Reason language: same fallback as the changelog, else any reason given.

### Memory warning (1.5.0+)
First join of each start: `Runtime.maxMemory()` in MB vs `memory.minGb` (with 5 % room: `-Xmx3G` reports a little under 3072 MB) and `memory.maxGb`; `0` = no limit. Warned values are kept in `config/cakeupdater/memory-warned.txt`, so the same setting warns once; back in range deletes it.

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
| `/cakeupdate changelog` | Open the changelog of this start's update (else every shipped changelog) |
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
| `packwiz.modrinthProject` | absent → Cakedition's `Yx4XUCFJ` when `packUrl` is Cakedition's, else none | Modrinth project to update from first (`""` = don't) |
| `packwiz.packUrl` | Cakedition `packwiz` branch `pack.toml` | Published pack.toml URL (after Modrinth) |
| `packwiz.fallbackUrls` | absent → Cakedition's GitLab copy when `packUrl` is Cakedition's, else none | More pack.toml addresses, in order |
| `resourcePacks[]` | one entry for MonumentaRP | Modrinth packs to keep updated (`[]` = off) |
| `resourcePacks[].enabled` | `true` | |
| `resourcePacks[].modrinthProject` | `"Yj53Rs56"` | Project ID or slug |
| `resourcePacks[].gameVersions` | `[]` | `[]` = the running MC version |
| `resourcePacks[].versionTypes` | `["release"]` | Accepted Modrinth version types |
| `backups.enabled` | `true` | Snapshot on close + backup before updates |
| `backups.keep` | `5` | Backups kept per kind (update / restore) |
| `memory.enabled` | `true` | Memory warning on/off |
| `memory.minGb` / `memory.maxGb` | `3` / `12` | Warn outside this range (`0` = no limit) |
| `memory.guideUrl` | Cakedition install guide | Linked from the warning (`""` = no link) |

The "absent" defaults matter: config files written before 1.6.0 have neither key, and another modpack's config must not start checking Cakedition's Modrinth project. On Windows, `packUrl`, the fallbacks, the `.mrpack` URL and `chatPrefix` pass through `cmd /c start`, so they must not contain `& ^ | < > %`. On macOS and Linux everything is single-quoted (`HelperLauncher.shellQuote`).

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
**CI** (`.github/workflows/build.yml`, GitHub only runs it in the standalone `HarrytheCake/CakeUpdater` repo, where this folder is the root): every push and pull request builds and tests on JDK 21 and uploads the jar as a workflow artifact. A push to `main` whose `mod_version` has no `v<mod_version>` release yet creates that release with `cakeupdater-<mod_version>.jar` attached and its SHA-256 at the top of the notes. Pushing again with the same version builds but releases nothing.

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
- **Adoption is only as good as `adoption.json`:** it must describe exactly the `.mrpack` it ships in. A `.mrpack` without one (the 2.2.0 one, or another pack's) updates the old way, with the problems above.
- **Never restore `pack-version.txt`** or yosbr defaults: the update notice would come back and pack defaults would go stale (`Backups.SKIP`).
- **The mod downloads no code (1.3.0+).** It runs the `packwiz-installer-bootstrap.jar` a packwiz install already has in `.minecraft` (the launcher's pre-launch command needs it). Without it (e.g. a `.mrpack` install), `/cakeupdate`, `onclose` and `now` show [Open game folder] + [Download page] (`Helper.BOOTSTRAP_PAGE`) instead of updating, and `Helper` prints the same advice. Before 1.3.0 `Helper` downloaded bootstrap v0.0.3 itself; this was removed for mod-site rules. Its network use: the update check (Modrinth API, `pack.toml`, `withdrawn.json`), the `.mrpack` and the pack files it links (data, not code) when the player updates, and resource packs the player chooses to update.
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

- Adoption end to end (2026-10-28): a 2.2.1 `.mrpack` installed like a launcher does (Iris on, minimap off, a player's own jar, an edited `servers.dat`), then updated by the real bootstrap + packwiz-installer (window mode, as `Helper` runs it) from a locally served pack with the Cakedition Updater jar renamed, Log Begone removed and the version bumped. Without adoption: two Cakedition Updater jars, Log Begone left. With adoption: one jar, Log Begone deleted, Iris kept, minimap still off, the player's jar and server list untouched, no option window, `packFileHash` written.
- `Backups` out of game (2026-10-27, 41 checks in a fake game folder): snapshot skips, incremental sync and deletions, update detection once per change, server and config restore with safety backups, cancel, defaults only when missing, per-kind pruning.

- 1.5.0 out of game (2026-10-28, JUnit, 12 tests): version order, skipped versions newest first, unknown/same versions, zh_tw → zh_cn → en fallback, Markdown parsing, withdrawn lookup and reasons, memory range and warn-once.

- 1.6.0 (2026-10-29): JUnit 19 tests (7 new: config defaults only for Cakedition's own `packUrl`, Modrinth answer parsing and version order, the query URL, cmd-safe arguments, `.mrpack` rebuild with only changed linked files downloaded, refused bad hashes / unsafe paths / pre-1.6.0 pack files / a wrong download). **End to end** with the real helper, bootstrap and packwiz-installer (window mode): a 2.2.3 install (from GitHub at `62d9877`) updated from a locally served 2.3.0 `.mrpack` came out byte-identical to a clean 2.3.0 install, `packFileHash` included; with a wrong sha512 the helper fell back to the next pack.toml address and gave the same result. Not tried: the real Modrinth CDN (the first released pack after this does it), the in-game check against the real Modrinth API.

Not proven:
- the backups chat message, screen and restores in game (the user reported 2026-10-28 that the config backup works)
- 1.5.0 in game: urgent notice + toast, changelog lines + screen, memory warning
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
