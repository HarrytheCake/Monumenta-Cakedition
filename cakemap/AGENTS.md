# CakeMap: agent reference

Client-only mod (`cakemap`, package `io.github.harrythecake.cakemap`) for **Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2** (Cake multiloader layout since 2026-11-07, see "Multiloader status"; the pack would ship the Fabric 1.20.4 jar): a minimap and world map with waypoints for Monumenta, replacing Xaero's Minimap (Fair-Play) and World Map. A **CakeUI plugin** (hard dependency, needs CakeUI 0.2.0's `addHudPiece`). Terms are in [`GLOSSARY.md`](GLOSSARY.md): static map, dynamic map, shard, region, reveal, match check, built-in waypoint, temporary waypoint, bookmark, cave mode. Player-facing page: [`README.md`](README.md); keep both current.

Planned standalone repo `HarrytheCake/CakeMap` (remote `cakemap`, MIT). **Not public yet** (2026-11-04): the folder is not in the root `.gitignore` whitelist and not in `push_all` TARGETS until the user says so. Source of truth: this folder in the Monumenta-Cakedition repo. Design and decisions: the local `docs/cakemap-design.md` (started from the user's `CakeMap.txt`).

## Decisions (user, 2026-11-04)

- **Static maps** are pre-rendered from the user's own Xaero data into a **separate resource pack** (`CakeMap-static-monumenta.zip`), made with the dev export. It **does not ship** until Monumenta's permission is recorded. No network code at all (Dynmap is not used).
- **Reveal:** a shard's whole static map shows once the player has entered that dimension (saved per install in `cakemap/<server>/revealed.json`).
- **Fair-play, cave mode allowed:** only the player's own marker (arrow or head); no entity, mob or player radar.
- **CakeUI plugin:** HUD pieces `minimap` and `waypoints` (layouts can hide / move / scale / fade them) and the element `cakemap:minimap`.
- **Quest objectives** (temporary): the quest compass target (spawn-position packet, Monumenta only or dev mode; ignored for 2 s after a world change), `[+]` after coordinates in chat (runs the client-only `/cakemap temp x y z`), and by hand.
- **Keys as Xaero's in the pack:** M world map, U waypoints, **J** new waypoint (the pack ships Xaero's on J because CakeBook has B), Y settings, Z enlarge (hold), numpad + temporary waypoint; cave toggle, zoom in/out, minimap toggle unbound.
- **Default corner top left** (top right is where vanilla puts status effects and toasts). Built-in **traders start hidden** (97 of them; one click in Kinds).

## Decisions (user, 2026-11-04, after the first in-game look)

- **Icons are SVG** (`textures/waypoint/<icon>.svg`, JSVG 2.2.0, MIT, jar-in-jar): rasterised at the exact screen-pixel size and placed on whole pixels (the 16/32 PNGs went jagged when the world map zoomed out). The 16/32 PNGs and their `_bookmarked` twins are gone.
- **Bookmark = tiny gold star** (`star.svg`) at the icon's top left; no outline.
- **Quest givers**: light blue triangle + white "!" (`quest`); **main quest givers** (`quest_main`): light red triangle + three white "!", for the coloured boxes of the user's *Monumenta Quest Flowchart (June 2026)* (story = green, wool = blue, major side = orange; white side quests stay `quest`). The list is `MAIN_QUESTS` in `tools/wiki_waypoints.py`, plus `{{Quest}}` pages with `spec` (specialization trials).
- **Draw order** (`WaypointStore.layer`): ordinary < bookmarked < temporary < compass target < tracked; on the world map the hovered icon is drawn last, and a click picks the top-most icon.
- **Left click on a world-map icon** → two buttons: **Track this** (one tracked waypoint per server, saved in `waypoints.json` `tracked`; always in the world view with its name, pinned to the minimap edge, yellow ring on the world map, even when hidden) and **Nearest town teleport** (nearest `town` waypoint to the clicked icon, not itself; centres the map, moves the cursor there with vanilla's `InputConstants.grabOrReleaseMouse` (Yarn: `InputUtil.setCursorParameters`) so Ixeris is fine, flashes the icon). Track is also in the right-click menu and the waypoint list.
- **Towns** come from each region page's "Cities" section (deepest headings; coordinates there, else the city page). Celsian Isles now has its 8 towns; Steelmeld is in the Isles (it was wrongly in the Ring). Oceangate's page has only x z (y = 64); New Antium and Chantry of Repentance have no coordinates on the wiki (left out).
- **Waypoint colours**: 8 swatches (`WaypointColors.PALETTE`, 0 = default blue); for player waypoints they recolour the pin (`#3a8dff` in `player.svg`), for every kind the name and world-view text. Xaero's 16 colours map to the nearest swatch.
- **Map choice per waypoint** (`maps`: both / static / dynamic; editor button; the import screen asks for imported Xaero waypoints, default both).
- **Old overworld waypoints are split by shard** (user, 2026-11-04: "it imports all shards' waypoints into one"). Before Monumenta named its dimensions, every shard was `minecraft:overworld`, so Xaero's `dim%0` (and its `null` world map) mixes the Valley, the Isles, the Ring. `OverworldSplit` gives each such waypoint the shard (1) whose map around it (9x9 columns, same top block, height ±1) is the old overworld map there (≥ 40 % and twice the next shard), else (2) whose ground is nearest the waypoint's y (≤ 6 and 3 ahead, or ≤ 12 and 10 ahead), else (3) the only shard that knows the spot; otherwise it stays in the overworld. Map first because town waypoints are often typed-in coordinates under roofs. Candidates: `monumenta:*` minus plots, maps from Xaero's world map and CakeMap's own. A re-import moves the copies an earlier import left in the overworld (`WaypointStore.moveImported`: same position and name, source `xaero`; keeps id, colour, flags, tracking). On the user's data: 70 of 92 placed (53 Isles, 14 Valley, 3 Ring, all towns right); 22 stay (spots no current shard map knows, one tie). The old overworld *map* itself is still imported as one mixed map (not asked).

## Decisions (user, 2026-11-07)

- **Icons: hand-drawn viewBox-32 SVGs only** (dark silhouette outline first, then fills). The pixel-art icons from the user's `mapicon.png` (2026-11-07, viewBox 16) were **reverted the same day at the user's request**; `death.svg` is a new hand-drawn gravestone in the old style. The pixel-art SVGs are kept outside the repo (session scratchpad `pixel-icons-2026-11-07/`); their art source/permission was never recorded.
- **Recolouring:** an icon may name its recolour colour with `<!-- cakemap-recolor: #rrggbb -->`; without the comment `#3a8dff` (`WaypointColors.recolorSvg`; `player.svg` uses the default). Colour 0 (default) draws the icon's own colours.
- **Death waypoints** (`WaypointKind.DEATH`, kept, saved with the player's own): one per death, name "Death MM-dd HH:mm", source `death`; the newest **5 per server** stay (`WaypointStore.DEATHS_KEPT`, `addDeath` removes the oldest over every dimension); **never tracked by themselves**; setting `deathWaypoints` (on). Signal: the death packet (`ClientPacketListener.handlePlayerCombatKill` TAIL, also sent with immediate respawn), fallback the player dead on a tick; once per life (`DeathWaypoints.armed`, re-armed only by a new, living player entity: respawn or another world. Fixed 2026-11-07 during the port: re-arming on "health > 0" let the tick after the death packet, which can come before the health packet, record a second death 1–2 ticks later; the Fabric 1.20.4 self-test caught it with 2 deaths on a fresh run). Xaero deathpoints are still not imported.
- **Circle frame** (`MapSettings.frame`: `RING` default / `LINE`): RING = a band inside the minimap box (`Minimap.ringWidth`: radius/14, 3–7 GUI px; 4 at size 110), grey `#5c5c5c` with a `#9a9a9a` highlight and dark edges, **N on the ring** on a dark disc (both north up and turning); the map circle shrinks by the band, waypoints pin to the map's edge. LINE = the old 1.5 px line, N only when turning. Rotation itself was already a setting (*Turns with me* / *North up*).

## Files

Cake multiloader layout (ADR 0009, kit in `../tools/multiloader/`): `core/` is plain Java compiled into every jar; each `<mc>/` folder (1.20.4, 1.21.1, 26.1.2) has `common/` (Minecraft-facing, both loaders, Mojang names), `fabric/` and `neoforge/`. The three `common/` trees are copies that differ only where Minecraft changed (see "Multiloader status").

```
cakemap/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/wrapper/   # the kit (apply_kit.py copies it; don't edit here)
├── gradle/cakemap.gradle   # CakeMap's own build lines, applied by every <mc>/<part>/build.gradle: CakeUI + JSVG (see "Build and test")
├── gradle.properties       # mod_id, mod_name, mod_version 0.1.0, mod_group, cakeui_version=0.3.0, jsvg_version=2.2.0 (+ Gradle memory)
├── .github/workflows/build.yml   # mirror CI (copied from CakeUI's: JDK 17+21+25, all six jars + cakemap-<version>.jar); not public yet
├── tools/make_icons.py     # draws the arrow and the mod icon (PIL) into core/…/textures; waypoint icons are hand-written SVGs
├── tools/wiki_waypoints.py # builds core/…/cakemap/waypoints/monumenta.json from monumenta.wiki.gg (CC BY-SA 4.0);
│                           #   MAIN_QUESTS = the flowchart's coloured quests; run with the test instance's cakemap/monumenta for terrain
├── run/                    # the old (pre-port) dev run folder: worlds, run/cakemap-export/ (the static pack: never delete, never ship)
├── core/src/main/java/io/github/harrythecake/cakemap/
│   ├── core/                # RegionData (.cmr format), RegionShader (colours → pixels, relief, water, quarter, samples), BlockStates
│   │                        #   (sorted state text), WorldKeys (server/dimension folders, Xaero names, personal worlds), Waypoint (+ maps),
│   │                        #   WaypointKind, WaypointColors, WaypointStore (own/built-in/temporary, flags, reach, tracked, layers,
│   │                        #   shown(dim, static), nearest, deaths, built-in JSON), MapSettings (config.json), ChatCoords, MatchCheck, StaticSamples
│   ├── xaero/               # XaeroRegionReader (world-map region.xaero, format 6.x), XaeroWaypoints, XaeroSettings (+ key names)
│   ├── platform/            # Platform (loader, configDir, gameDir) + Services (ServiceLoader; each loader part's META-INF/services)
│   ├── CakeMapCore.java     # MOD_ID, LOG, onRender (the game sets the render-thread executor; tests run tasks at once)
│   ├── MapGeometry.java     # View (centre, zoom, angle, toScreen/toWorld), circle/rect polygons, clip (Sutherland–Hodgman)
│   ├── RegionStore.java     # one dimension's .cmr files: regions near the player in memory, one IO thread, saves every 30 s / when far / on leave
│   ├── OverworldSplit.java  # Monumenta's old "minecraft:overworld" waypoints (Xaero dim%0) → their shard (see Decisions)
│   └── XaeroImport.java     # the file side of the import: Options, Progress, xaeroDir, waypoints (live store or file), maps (merge), counts
├── core/src/main/resources/assets/cakemap/   # lang, textures (SVG icons, arrow), icon.png, cakemap/waypoints/monumenta.json
├── core/src/test/java/…/   # CoreTest, XaeroTest, LangTest (21 tests, 2 of them local-only and skipped without their data)
└── <mc>/
    ├── version.properties   # from the kit
    ├── common/src/main/java/io/github/harrythecake/cakemap/
    │   ├── CakeMap.java         # the loader-independent client: init (settings, KEYS), endClientTick (session per server, scanner, cave,
    │   │                        #   deaths, keys, reach, waypoint saves; then SelfTest), endSession, notices, Xaero import offer (once)
    │   ├── CakeMapCommands.java # the client-only /cakemap tree for any command source (temp, map, waypoints, settings, import, export)
    │   ├── CakeMapPlugin.java   # CakeUI plugin: HUD pieces waypoints + minimap, element cakemap:minimap, values cakemap.*, actions cakemap:*
    │   ├── MapSession, Scanner, GameColors, MapTextures, MapDraw, StaticMaps (reload listener), StaticExport, CaveLayer, Minimap,
    │   │   InWorldWaypoints, QuestCompass, ChatLinks, HudNotice, DeathWaypoints, Icons (JSVG → texture per icon/colour/px),
    │   │   WorldMapScreen, WaypointListScreen (+ KindsScreen), WaypointEditScreen, SettingsScreen, ImportScreen (tinyfd folder picker),
    │   │   XaeroImportRun (the import's game side: thread, settings, keys, the open server's live store), SelfTest
    │   ├── Polys.java           # 26.1.2 only: triangles as a GUI render state (GuiElementRenderState) for MapDraw
    │   └── mixin/               # ClientPlayNetworkHandlerMixin (handleSetSpawn TAIL → QuestCompass; handlePlayerCombatKill TAIL → DeathWaypoints),
    │                            #   SpriteContentsAccessor (originalImage); 26.1.2 also GuiGraphicsExtractorAccessor (guiRenderState) and
    │                            #   GameRendererMixin (renderLevel: camera + projection for InWorldWaypoints); cakemap.mixins.json
    ├── fabric/              # CakeMapFabric (client entrypoint: keys, Fabric API events, reload listener, client command), FabricPlatform,
    │                        #   ModMenuEntry, fabric.mod.json ("cakeui" entrypoint = CakeMapPlugin)
    └── neoforge/            # CakeMapNeoForge (@Mod: keys, events, reload listener, client command, config screen), NeoForgePlatform,
                             #   META-INF/services/…cakeui.api.CakeUIPlugin (CakeMapPlugin), META-INF/mods.toml (1.20.4) or neoforge.mods.toml;
                             #   1.20.4 only: neoforge/mixin/ClientPacketListenerMixin (cakemap.neoforge.mixins.json, see the table)
```

Resources: `assets/cakemap/lang/{en_us,zh_cn,zh_tw}.json` (same keys, LangTest; `key.category.cakemap.main` is the 26.1 key category, `key.categories.cakemap` the older one), `textures/waypoint/<icon>.svg` + `star.svg` (all hand-drawn: viewBox 32, dark silhouette outline drawn first, then fills; `player.svg` keeps `#3a8dff` as its recolour colour), `textures/gui/arrow.png`, `icon.png`, `cakemap/waypoints/monumenta.json` (built-in waypoints, **CC BY-SA 4.0**, generated).

## Formats

- **`.cmr`** (`<game>/cakemap/<server>/<ns>/<path>/<rx>_<rz>.cmr`): int magic `CMR1`, int version 1, int rx, int rz, then deflate: state palette (int n, UTF…; 0 = unknown), biome palette, short[512²] state, byte[512²] biome, short[512²] height, short[512²] overlay state, byte[512²] depth. Index = z * 512 + x.
- **Xaero world map** (format 6.x): see `docs/cakemap-design.md` and `XaeroRegionReader`; heights are 12-bit signed (bits 12–19 low, 25–28 high).
- **Static pack**: `assets/<ns>/cakemap/static/<dim ns>/<dim path>/meta.json` (`server`, `dimension`, `title`), `<rx>_<rz>.png` (512², transparent = unknown), `samples.bin` (StaticSamples). A shard is used only on its `server` (`*` in dev mode).
- **Built-in waypoints**: `assets/<ns>/cakemap/waypoints/<server>.json`: `{ "<dimension>": [ {id, name, kind, x, y, z} ] }`, keys starting with `_` ignored.
- **Player files**: `config/cakemap/config.json` (MapSettings), `<game>/cakemap/<server>/waypoints.json` (own waypoints with `color` and `maps`, built-in bookmarks/hidden, hidden kinds, `tracked` id), `revealed.json`.

## Build and test

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # all six jars → build/libs/ (+ tests)
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=26.1.2   # one version (+ core)
```

`build/libs/cakemap-<fabric|neoforge>-<mc>-0.1.0.jar`, plus `cakemap-0.1.0.jar` = the Fabric 1.20.4 jar (what the pack and the test instance use). JSVG 2.2.0 (MIT) is bundled: `include` on Fabric (`META-INF/jars/`), `jarJar` on NeoForge (`META-INF/jarjar/`; NeoForge 1.20.4/1.21.1 dev runs also get it through `additionalRuntimeClasspath`, which 26.1 no longer has or needs).

**CakeUI jars** (`gradle/cakemap.gradle`): CakeMap compiles (and runs in dev) against CakeUI's **GitHub release jars** `cakeui-<loader>-<mc>-<cakeui_version>.jar` of release `v0.3.0` (API 3), fetched as Ivy artifacts from `github.com/HarrytheCake/CakeUI/releases/download/…` (reproducible: a release never changes, and the mirror CI can fetch them too). `common/` compiles against the NeoForge jar of its version (Mojang names, same API classes; the Fabric jars of 1.20.4/1.21.1 are in intermediary names), `fabric/` against the Fabric jar (through the kit's `modDep`, so Loom remaps it), `neoforge/` against the NeoForge jar (`implementation`: FML loads it as the `cakeui` mod in dev runs, and its NeoForgePlugins finds CakeMapPlugin through `META-INF/services`). For CakeUI changes that aren't released: `-Pcakeui_dir=../cakeui/build/libs` (a multiloader build of `cakeui/`, same file names). The old Loom-1.10 workaround (`-Pcakeui_jar`) is gone: everything builds with the kit's Loom 1.19.

Tests (`core/`, plain JUnit; 21, 2 skipped without local data): CoreTest (regions, keys, chat coordinates, match check, samples, shading, waypoint store incl. tracking / layers / map choice / nearest / colours, death waypoints (newest 5), icon recolour, settings, clipping + view maths in MapGeometry), XaeroTest (hand-made region bytes, versions, waypoints, waypoint import into a live store vs a file, old overworld waypoints split by shard, settings + keys; local only: the `xaero/` sample, and `realOverworldSplit` on `CAKEMAP_XAERO=<instance>/.minecraft/xaero` (`CAKEMAP_VERBOSE=1` prints each placement)), LangTest (same keys in en_us/zh_cn/zh_tw, every SVG icon, the recolour colour). The `common/` parts have no tests.

**Self-test** (dev client, any target; screenshots in `<mc>/<loader>/run/screenshots/cakemap-selftest-*.png`, log lines `CakeMap self-test: …`), from the repo root:

```
python tools/multiloader/smoke.py cakemap <mc> <fabric|neoforge> "--jvm=-Dcakemap.selftest=world" "--done=CakeMap self-test: done" "--args=--width 1280 --height 720"
```

Before a run, clear death waypoints left in `<mc>/<loader>/run/cakemap/sp_cakemap-selftest/waypoints.json` by earlier runs (the death checks expect exactly one), and give `<mc>/<loader>/run/options.txt` `pauseOnLostFocus:false` and `guiScale:3` (smoke.py itself sets `onboardAccessibility:false`). The 2026-11-07 port did both with a scratch script (`prep.py`, session scratchpad `cakemap-port/`).

- `-Dcakemap.selftest=world`: demo world; shots of the square / round rotating minimap, a real death (server `kill`: death screen, one death waypoint, still one after respawn; logged PASS/FAIL), the round north-up ring with a death icon and the thin line, the cave minimap, chat `[+]` + compass target (over a POI at the same place), then the chat-click path of `/cakemap temp` (`sendUnsignedCommand`; 26.1: `sendUnattendedCommand`; it must run on the client and add one waypoint: PASS/FAIL), world map (near, icon popup, nearest-town jump, zoomed out with the tracked ring and a bookmark star), list, editor (red pin, swatches), settings, kinds, in-world markers (tracked one named); then exports the test world, loads it back as a resource pack and checks the static shard (found, revealed, match, shown) + a world-map shot. About 1.5–2 minutes.
- `-Dcakemap.selftest=export -Dcakemap.import=<xaero folder or game folder> -Dcakemap.export=monumenta`: imports Xaero waypoints + maps into `run/cakemap/`, exports `run/cakemap-export/CakeMap-static-monumenta.zip` (pass all three with `--jvm=`; the existing static pack is in the old `cakemap/run/cakemap-export/`).
- `-Dcakemap.selftest=compat` (e.g. through a test instance's JVM args + `OverrideJavaArgs=true`, launched with `prismlauncher.exe --launch "<instance>"`): test world, one waypoint of every kind (one tracked, one bookmarked), shots of the HUD, world map and list, quits. Back up `instance.cfg`, `config/cakemap/config.json` and `options.txt` first and restore them afterwards; delete `saves/cakemap-selftest` and `cakemap/sp_cakemap-selftest`.

## Status

- **2026-11-04, built in one session (all of CakeMap.txt):** dynamic map, static maps (pack + reveal + match check), minimap (square/circle, rotating, cave mode, CakeUI pieces + element), world map, waypoints (11 kinds, icons 16/32 normal/bookmarked, permanent/temporary/built-in, bookmarks, kinds filter, in-world markers), quest compass, chat coordinates, Xaero import (settings, keys, waypoints incl. legacy folder, maps), static export, 514 built-in Monumenta waypoints from the wiki (232 POIs, 12 dungeon entrances, Azacor, 153 quest givers, 9 towns, 101 traders, 6 charm traders; towns and NPCs placed by the user's explored terrain). Self-tested in the dev client (world + export + static round trip) and in the test instance with the full pack (compat run: no mixin problems, 50 static maps loaded).
- **Test instance (2026-11-04):** `cakemap-0.1.0.jar`, new `cakeui-0.2.0.jar` (with addHudPiece; old one in the session scratchpad), Xaero's World Map `.disabled`, the enabled Xaero Minimap duplicate moved out (its identical `.disabled` copy stays), `resourcepacks/CakeMap-static-monumenta.zip` enabled on top. Not tested on Monumenta itself yet (no login with the user's account): quest compass, static match check on real shards, import offer → import in game.
- **2026-11-04, second round (user's first look on Monumenta):** SVG icons, gold-star bookmarks, blue / red quest triangles (flowchart), tracked waypoint + icon popup + nearest town, compass target on top, waypoint colours, map choice, Xaero import fix (the import wrote `waypoints.json`, then the open session saved its old empty list over it at the end; now merged into the live store). Built-in waypoints rebuilt (18 towns, 89 main / 64 other quest givers, 97 traders). Self-tested in the dev client (world) and with the full pack in the test instance (compat).
- **2026-11-04, third round:** old overworld waypoints split by shard on import (`OverworldSplit`). New jar in the test instance (previous one in the session scratchpad); user: "it works good". The import still brings over waypoints of old numbered instance worlds (`minecraft:teal10990`…) and overworld leftovers (user's choice: no import rule, only their test instance cleaned).
- **Test instance waypoints (2026-11-04, user's request):** `cakemap/monumenta/waypoints.json` cleaned once with the import's own code: the 70 old overworld waypoints placed on their shards, then the 21 overworld leftovers and the 27 waypoints of numbered instance worlds deleted (839 → 791; no `minecraft:*` dimension left). Backup: session scratchpad `waypoints-before-cleanup.json`.
- **2026-11-07, multiloader port:** Fabric + NeoForge on 1.20.4, 1.21.1 and 26.1.2 (see "Multiloader status"); death waypoints no longer recorded twice when the health packet comes a tick after the death packet. The new Fabric 1.20.4 jar is in the test instance (`mods/cakemap-0.1.0.jar`; the 15:15 pre-port jar is in the session scratchpad as `cakemap-0.1.0-pre-port.jar`); it runs with the test instance's Loom-1.10 `cakeui-0.2.0.jar` (same API signatures as the v0.2.0 release jar; the Fabric 1.20.4 self-test also passed against that very jar via `-Pcakeui_dir`), no kit-built CakeUI needed.
- **Next:** the user's in-game check on Monumenta (import done and cleaned); Monumenta's permission for the static pack; then pack integration (remove Xaero's mods and their configs from the pack, CakeMap + CakeUI together, `config/cakemap/config.json` not shipped, yosbr key lines), guide-book page, public repo.

## Multiloader status (2026-11-07)

Ported 2026-11-07 from the Yarn 1.20.4 Fabric mod (Loom 1.10 `migrateMappings` + `yarn2moj.py`; plain moves, the folder isn't in git; full backup `../backups/cakemap-pre-multiloader-2026-11-07.zip`). 1.20.4 Fabric behaves as before: same mod id, config and data files, keys, lang keys, Fabric API events, `cakeui` entrypoint, mixin targets (Mojang names now: `handleSetSpawn`, `handlePlayerCombatKill`, `SpriteContents.originalImage`). What moved to `core/`: `core/`, `xaero/`, RegionStore, OverworldSplit, the file side of XaeroImport (the game side is `XaeroImportRun` in common), MapGeometry (MapDraw's View/shapes/clip, so CoreTest's clipping test stays plain), CakeMapCore (log + render-thread hook). The `/cakemap` command tree is built once for any command source (CakeMapCommands) and registered by each loader. One behaviour fix on every target, 1.20.4 Fabric included: death waypoints re-arm only for a new player entity (see Decisions 2026-11-07).

Every target: `./gradlew build` OK (21 core tests, 2 skipped without local data), and the full `-Dcakemap.selftest=world` run through `smoke.py` reached `CakeMap self-test: done` with no error or mixin lines (SMOKE PASS): death waypoints after one death 1 (PASS) and after respawn 1 (PASS), the chat-click `/cakemap temp` ran on the client (PASS), the exported static shard loaded back (4 tiles, revealed, match all samples, shown). Screenshots checked by eye on all six (square/round/rotating minimap with the ring and N, death icons, cave layer, chat `[+]` and the compass target, world map with the icon popup, nearest-town jump, zoomed out with the tracked ring and the bookmark star, the static world map, list, editor swatches, settings, kinds, in-world markers with names and distances).

| Target | Works | Differs / gaps (why) |
|---|---|---|
| 1.20.4 Fabric | everything, as before the port | — |
| 1.20.4 NeoForge | everything; events: ClientTickEvent END, ChunkEvent.Load (client side), LoggingOut, GameShuttingDownEvent, RenderLevelStageEvent AFTER_LEVEL (camera), ClientChatReceivedEvent.System (`[+]`), RegisterClientCommandsEvent, RegisterClientReloadListenersEvent, RegisterKeyMappingsEvent; settings from the mod list's Config; CakeUI plugin via `META-INF/services` (first real NeoForge plugin of CakeUI: works) | NeoForge 20.4 sends click-event commands (`sendUnsignedCommand`) to the server: `neoforge/mixin/ClientPacketListenerMixin` (1.20.4 only) runs `/cakemap …` on the client instead (the chat `[+]`, the import offer); self-tested. Xaero conflicts are `incompatible` dependencies (xaerominimap, xaeroworldmap). |
| 1.21.1 Fabric | everything | BufferBuilder API of 1.21 (`Tesselator.begin`, `addVertex`); camera from `WorldRenderContext.positionMatrix()` (1.20.5+ passes the view rotation as a matrix, not a PoseStack); vanilla 1.21 blurs the world behind the list/editor/settings screens. |
| 1.21.1 NeoForge | everything (as 1.20.4 NeoForge; ClientTickEvent.Post, camera from `getModelViewMatrix()`) | no click-command mixin needed (21.1 runs client commands from click events itself). |
| 26.1.2 Fabric | everything | **GUI render states:** since 1.21.6 the GUI is recorded and drawn at the end of the frame, so the map fills, region tiles, cave layer, rings and outlines are CakeMap's own `GuiElementRenderState` (`Polys`: triangles as quads with the last corner doubled, wound like vanilla's quads so none is culled; submitted through the `GuiGraphicsExtractorAccessor`); they ignore an enclosing scissor (nothing in CakeMap or CakeUI sets one today). No shader colour: icon and map alpha go into the vertex colour. The world-map popup sits on its own GUI stratum (`nextStratum`) instead of z 400. **In-world markers:** no common after-level event with the projection, so the common `GameRendererMixin` (MixinExtras `@Local`) takes the projection (view bobbing included), view rotation and camera position in `GameRenderer.renderLevel`; same picture as before. Block colours: models are `BlockStateModel` parts, tints `BlockTintSource`s (default colour without a world), `NativeImage.getPixel` is ARGB. Key category is a registered id (`key.category.cakemap.main`). Forests look browner than on 1.21.1: that's 26.1's leaf litter between the trees (tinted with dry foliage), not a colour bug. |
| 26.1.2 NeoForge | as 26.1.2 Fabric; reload listener through `AddClientReloadListenersEvent` | as 26.1.2 Fabric. |

Not tested on any target: Monumenta itself (as before the port), the Xaero import and the folder picker on NeoForge (same code as Fabric), the `export`/`compat` self-tests on the new targets (only `world`), keys pressed by a real player (registered on both loaders; the self-test opens screens directly).

- **CakeUI HUD editor (2026-11-07, CakeUI API 3):** the minimap is a CakeUI **movable** (`Minimap.MOVABLE`, registered with `addMovables` in `CakeMapPlugin`): the editor drags it (nearest corner + offsets from the drop point), its corner handle / scrolling sets `size` (48–256), Done saves CakeMap's settings, so `corner`/`offsetX`/`offsetY`/`size` stay the only store. The `minimap` and `waypoints` HUD pieces say `editable() = false` (CakeUI leaves them out of the editor; old personal edits on them are moved once / dropped by CakeUI). With CakeUI 3 the settings' Corner row becomes "Place on HUD: open the HUD editor…" (`CakeMapPlugin.hudEditor`; with CakeUI 0.2 the old corner row stays, `NoSuchMethodError` caught). **Building needs CakeUI 3:** `cakeui_version=0.3.0` (released 2026-11-09). For a smoke run against a local CakeUI, put `cakeui_dir=` into gradle.properties temporarily (the `ORG_GRADLE_PROJECT_` variable didn't reach smoke.py's build). Self-test: "minimap movable listed …, placed bottom-right …" (PASS on Fabric 1.20.4; other targets build + tests only).
