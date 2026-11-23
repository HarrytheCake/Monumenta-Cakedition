# CakeConfighub: agent reference

Client-only mod (`cakeconfighub`, package `io.github.harrythecake.cakeconfighub`) for **Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2** (Cake multiloader kit, `tools/multiloader/README.md` in the Cakedition repo). Two features on one engine: the **config hub** (one screen for the UI/HUD options of many mods; Save writes each value into that mod's own config file and asks the mod to reload) and **pack defaults** (the pack's wanted values, patched into those files at startup while the player hasn't changed them). MIT. Started 2026-11-24 by moving CakeUI's config hub out (built in CakeUI as C1–C5 2026-11-19, game-tested by the user 2026-11-23, never released there). **Version 0.1.0, not released, not in the pack.**

- **Decisions:** ADR 0011 (config maps, value-only writes, whose value wins, never a command) and ADR 0013 (its own mod), plus the design notes (all local to the Cakedition repo, never pushed). Terms: [`GLOSSARY.md`](GLOSSARY.md) (config map, config hub, pack default, untouched, record, reload hook).
- **Standalone:** needs no other mod (own JSON5 reader `json/`, copied from CakeUI; own `platform/` service) and no mod needs it. Other mods plug in by metadata (`cakeconfighub:reload`) or the optional API.
- **Mirror:** `HarrytheCake/CakeConfighub` (remote `cakeconfighub`, added 2026-11-24; the repo doesn't exist until the user creates it). Its `tools/push_all.py` TARGETS entry is commented out: enable it after the first push. The first push releases v0.1.0 (CI from CakeUI's), only when the user says, after their game test. Source of truth is this folder; never commit in the standalone repo.

## Invariants

- **Value-only writes:** only the value's characters change; comments, key order, formatting and keys the map doesn't know survive. A missing key is added in the file's own style.
- **Never creates a missing file** (the mod writes its own first); a file that doesn't parse is left alone and the hub shows why. Writes go to `<file>.cakeconfighub-tmp`, then replace the file (atomic where possible); a BOM is kept.
- **Never a command:** reloads go through the mod's hook (metadata or API), else "takes effect after a restart". An unregistered client command would go to the server as chat (Monumenta).
- **Pack defaults only at startup** (Fabric `preLaunch`), only while the option is untouched; the hub's Save never touches the record; `/cakeconfighub reload` re-reads the maps but never applies pack defaults.
- **Nothing written without a press or a pack default.** The key binding is unbound by default.
- `cakeui:reload` (the key while the hub lived in CakeUI) is **not** read.

## Files

```
cakeconfighub/
├── gradle.properties                mod_id, mod_version (0.1.0), cake_fabric_api=false
├── .github/workflows/build.yml      mirror CI (CakeUI's): six jars + cakeconfighub-<v>.jar (= Fabric 1.20.4), release v<mod_version> once
├── core/src/main/java/io/github/harrythecake/cakeconfighub/
│   ├── CakeConfighub.java           MOD_ID, LOGGER, SELF_TEST (-Dcakeconfighub.selftest)
│   ├── api/CakeConfighubApi.java    registerConfigReload(modId, Runnable); API_VERSION 1
│   ├── json/                        Json5, JValue, Json5Exception (copy of CakeUI's reader)
│   ├── platform/                    Platform (configDir, gameDir, isModLoaded, modAssetFolders, modVersion, modMetadata), Services
│   └── configmap/                   the engine (below)
├── core/src/main/resources/assets/cakeconfighub/   icon.png, lang/{en_us,zh_cn,zh_tw}.json (same keys in each)
├── core/src/test/java/…/configmap/  ConfigDocTest, ConfigMapTest, PackDefaultsTest, HubModelTest
└── <mc>/                            1.20.4, 1.21.1, 26.1.2
    ├── common/…/cakeconfighub/      CakeConfighubClient (key, tick, open next tick, reload), ConfigHubScreen, ConfigHubModScreen, ConfigHubSelfTest
    │   (1.20.4 only) resources/assets/cakeconfighub/configmaps/*.json5   the bundled third-party maps
    ├── fabric/…/fabric/             CakeConfighubPreLaunch (preLaunch), CakeConfighubFabric (client), FabricApiHooks, ModMenuIntegration, FabricPlatform
    └── neoforge/…/neoforge/         CakeConfighubNeoForge (@Mod, client only), NeoForgePlatform
```

## Engine (core `configmap/`)

- `ConfigDoc.parse(Format, text)` reads another mod's config file and edits it **value by value** (`get(path)` / `set(path, value)`; missing JSON objects / TOML tables are created). Formats (`Format`): `json` and `json5` (`JsonDoc`, one scanner, JSON5 extras read in both), `toml` (`TomlDoc`, line-based; arrays, inline tables, multi-line strings, dates and `[[array tables]]` are read past, never edited), `options` (vanilla `key:value`, the key ends at the first ':') and `properties` (`LineDoc`; values are raw text). Reads give Boolean / Double / String / `ConfigDoc.OTHER` (non-scalar) / null (missing); `set` takes Boolean, Long (no decimal point), Double (always one), String.
- `ConfigMap.read(JValue, source)` = a map file: `mod` (an id or a list, e.g. `caketext-translator` / `caketext_translator`), `versions` (`VersionRange`: `>=2.13 <3`, `*`; build metadata ignored, pre-releases lower), `file` (inside the game folder only), `type`, `reload` auto/restart, `hub` (false = pack defaults only), `title`, `sections[].options[]` or flat `options`. Option types bool, int, float (min/max/step), enum (`values`: scalars or `{value, label}`), string, colour (`colourAs` string `#RRGGBB`/`#AARRGGBB` with `alpha`, or `int` = signed 32-bit); `Option.read(raw)` → typed, `toFile(typed)`, `allows(typed)`, `fallback` (= `default`). Bad options are skipped with a problem; a map without mod/file/type or with a newer `format` is null.
- `ConfigFile.open(map, gameDir)`: `read`/`has`/`write`/`save()` with the invariants above.
- **Map sources** (`ConfigHub.loadMaps()`), keyed by **map id = file name without `.json5`**, a later source replacing an earlier one: every loaded mod's `assets/<modid>/configmaps/*.json5` (`Platform.modAssetFolders("configmaps")`; CakeConfighub's own jar first, so a mod's own map for itself wins over CakeConfighub's copy) → `config/cakeconfighub/configmaps/` (a pack's). The CakeUI-layout source of the CakeUI days is gone (ADR 0013).
- **Pack defaults** (`PackDefaults`): `config/cakeconfighub/pack-defaults.json5` (`{ format: 1, <map id>: { <option key>: { value, history: [...] } | <bare value> } }`, pack-owned) and the per-player record `config/cakeconfighub/pack-defaults-applied.json5` (`PackDefaults.Record`, what CakeConfighub last wrote). `apply(maps, record, gameDir, loaded, versionOf)`, per option: **untouched** = key missing, or the file's value equals the record, the pack value or a `history` value → the pack value is written and recorded; anything else is the player's → kept, nothing recorded; a mod not loaded / out of `versions` / file missing or unparsable → skipped; an unknown map id or option, or a pack value outside the option's range → problem. Each file is saved once; the record changes only for saved files. `ConfigHub.applyPackDefaults()` runs it from the Fabric `preLaunch` entrypoint (no Minecraft classes; nothing without `pack-defaults.json5`; logs a summary, each change and each problem; never throws). A file read by Mixin itself (`sodium-mixins.properties`) is read before `preLaunch`: changes there take effect one launch later.
- **Hub model** (`HubModel`, `ConfigHub.buildHub()` reads the maps on every open): mods with `hub` on whose mod is loaded, sorted by title; greyed with `unavailable` = "version" or the file's problem; `value` / `set` (unsaved changes, dropped when equal to the file) / `packDefault` / `resetValue` (pack default, else the map's `default`) / `reset` / `resetMod` / `save` (each file once; never touches the record) / `discard`.
- **Reload hooks** (`ConfigReloads`): `CakeConfighubApi.registerConfigReload(modId, Runnable)` wins; else the mod's metadata `cakeconfighub:reload` (Fabric `custom`, NeoForge `[modproperties.<id>]`, read through `Platform.modMetadata`) names a class with `public static void cakeconfighubReloadConfig()`, called by reflection on the render thread right after its file was saved. A non-static method or a failure → "after a restart".

## Screens and entry points

- `ConfigHubScreen` (title "CakeConfighub"): one button per mod, greyed with a tooltip; scrolls. `ConfigHubModScreen`: section titles + options (bool/enum = `CycleButton`, int/float with min+max = slider snapped to `step`, else an `EditBox` (red when it doesn't parse/fit); colour box + swatch); "Reset" per option (tooltip names the pack default or default); gold ◆ after a pack-managed label (tooltip "Pack default: …"), yellow * on unsaved ones; Reset this mod / Save / Cancel; Save → reload hook → "uses the new settings now" or "after a restart".
- **Opening:** Mod Menu's Configure (Fabric, `modmenu` entrypoint, Mod Menu compile-only), the mod list's Config button (NeoForge), the key "Open CakeConfighub" (**unbound**, own category `key.categories.cakeconfighub`; 26.1.2: category id `cakeconfighub:main`), `/cakeconfighub` (opens the hub on the next tick, after the chat screen closed). `/cakeconfighub reload` (`ConfigHub.reloadMaps()`) re-reads the maps, logs each problem and answers "N config map(s) read, M problem(s)".
- **Fabric API is optional** (`cake_fabric_api=false`; compile-only, plus runtime-only in the dev client): `CakeConfighubFabric` registers the tick (`fabric-lifecycle-events-v1`), the key (`fabric-key-binding-api-v1`; 26.1.2 `fabric-key-mapping-api-v1`) and the command (`fabric-command-api-v2`) only when those modules are loaded (`FabricApiHooks` is the only class that touches Fabric API). Without it: pack defaults still apply, and Mod Menu (which needs Fabric API itself) is the only way in.

## Targets

| Target | Pack defaults | Mod asset maps | Bundled third-party maps | Key | Command | Run in game |
|---|---|---|---|---|---|---|
| Fabric 1.20.4 | yes (`preLaunch`) | yes | yes | with Fabric API | with Fabric API | self-test (a) + (b) |
| NeoForge 1.20.4 | no (no early hook yet) | yes | yes | yes | **no** (would need a client-command mixin) | build + unit tests only |
| Fabric 1.21.1 | yes | yes | no | with Fabric API | with Fabric API | build + unit tests only |
| NeoForge 1.21.1 | no | yes | no | yes | yes (`RegisterClientCommandsEvent`) | build + unit tests only |
| Fabric 26.1.2 | yes | yes | no | with Fabric API | with Fabric API | build + unit tests only |
| NeoForge 26.1.2 | no | **no** (its jar-file API differs: only `config/cakeconfighub/configmaps/`) | no | yes | yes (`RegisterClientCommandsEvent`) | build + unit tests only |

Game runs only on Fabric 1.20.4 (the user's rule).

## Bundled maps (1.20.4 jars only)

`1.20.4/common/src/main/resources/assets/cakeconfighub/configmaps/`: `minecraft` (options.txt UI options with vanilla lang keys, never key bindings; `reload: restart`), `appleskin`, `asteorbar` (layout/text/display switches, not its colours), `unofficial-monumenta-mod` (ability bar, effect and chest-count overlays; UMM is GPL-3.0: only its file is edited), `zoomify`, `betterpingdisplay`, `highlighter` (TOML), `tslatentitystatus`, `shulkerboxtooltip` (AutoConfig/Jankson, from its `Configuration` class), `betterf3` (`general.*`; keys checked against a real file by the user 2026-11-23); `hub: false` (pack defaults only, client-lag phase 2): `sodium`, `sodium-extra`, `lambdynlights`, `entity_model_features`, `entity_texture_features`. `versions` = the pack's minor version of each mod: **after updating one of these mods in the pack, check its map** (keys, ranges) and widen the range. Labels use the mod's own lang keys (AutoConfig `text.autoconfig.<mod>.option.*`, YACL `yacl3.config.zoomify.*`, UMM `unofficial-monumenta-mod.config.*`), else `cakeconfighub.map.*` (en_us, zh_cn, zh_tw). ShulkerBoxTooltip and BetterF3 write their file only when a player saves their own settings, so they stay greyed until then. A mod without a reload hook that saves its own settings while running (UMM) can write its in-memory values over a hub edit before the restart. Cake mods ship their own maps (`assets/<id>/configmaps/<id>.json5`, `versions: "*"`) and `cakeconfighub:reload` hooks.

## Build, test, run

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # all six jars + unit tests
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=1.20.4   # one version (+ core)
```

`build/libs/cakeconfighub-<fabric|neoforge>-<mc>-0.1.0.jar`, plus `cakeconfighub-0.1.0.jar` = the Fabric 1.20.4 jar.

**Self-test** (dev client, Fabric 1.20.4): `-Dcakeconfighub.selftest=1`. At `preLaunch`, `HubSelfTestFiles` writes a made-up map (`config/cakeconfighub/configmaps/cakeconfighub_selftest.json5`, mod `cakeconfighub`), its file (`config/cakeconfighub-selftest/hub.json5`, with a comment) and pack defaults (size 3 → 5 by `history`, name "pack" vs the player's "player"), so the startup run applies them. `ConfigHubSelfTest` starts 3 s after the title screen and logs `CakeConfighub self-test: hub: … (PASS|FAIL)` for: pack defaults at startup, record, listed and editable, ◆ pack default mark, saved value by value (comment kept), reload hook (registered through the API), reset (an option back to its pack default, Reset this mod); scrolls the page; with CakeUI installed it also opens CakeUI's settings by reflection and checks there is no "Other mods…" button. It removes its files and ends with `CakeConfighub self-test done`.

```
python tools/multiloader/smoke.py cakeconfighub 1.20.4 fabric "--jvm=-Dcakeconfighub.selftest=1" "--done=CakeConfighub self-test done"
```

Screenshots: `1.20.4/fabric/run/screenshots/cakeconfighub-selftest*.png` (hub list, mod page, after save + scroll, CakeUI's settings). For the check with CakeUI, put CakeUI's Fabric 1.20.4 jar into `1.20.4/fabric/run/mods/` (Fabric Loader remaps it in the dev run) and remove it afterwards.

## History

- 2026-11-24: split out of CakeUI (ADR 0013): code moved with `git mv` (package `io.github.harrythecake.cakeui.configmap` → `io.github.harrythecake.cakeconfighub.configmap`), paths `config/cakeui/…` → `config/cakeconfighub/…`, metadata key `cakeui:reload` → `cakeconfighub:reload`, lang `cakeui.hub.*` / `cakeui.map.*` → `cakeconfighub.*`, layout map source dropped; new: own entry points, Mod Menu screen, key, `/cakeconfighub [reload]`.
