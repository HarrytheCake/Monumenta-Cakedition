# CakeMap

A minimap and world map with waypoints, made for [Monumenta](https://www.playmonumenta.com/). Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2, client only. Needs [CakeUI](https://github.com/HarrytheCake/CakeUI) 0.2.0 or newer (and Fabric API on Fabric).

## What it does

- **Minimap** in a corner of the screen: square or round (a thick ring with **N** on it, or a thin line), north up or turning with you, zoom, opacity, your coordinates. Hold **Z** to enlarge it.
- **World map** (**M**): drag to move, scroll to zoom, right-click to add waypoints. **Click an icon** for two buttons: **Track this** (follow it: it shows in the world view with its name and distance, stays at the minimap's edge and is drawn above every other icon) and **Nearest town teleport** (the map jumps to the closest town and puts your cursor on it). Pick other dimensions you have a map of.
- **Two map modes**:
  - *Static*: a pre-rendered map of each shard, from a resource pack. A shard's map appears once you have entered that shard. If what you see doesn't match the static map (an instance at other coordinates, a changed build), CakeMap quietly shows your own map instead.
  - *Dynamic*: only what you have seen, drawn from the chunks your game loads. Saved per server.
- **Cave mode**: underground, the minimap shows a cross-section at your height. Automatic, on or off.
- **Waypoints**: permanent (saved) and temporary (gone when you reach them or leave). Kinds with their own icons: dungeon entrance, point of interest, town teleport, teleport, quest giver, main quest giver (red triangle with three "!": story, wool and major side quests), charm trader, trader, your own, world boss, death, quest objective. Icons are vector graphics, sharp at any zoom. **Bookmark** a waypoint (a gold star) to keep it at the minimap's edge and in the world view. Give your own waypoints one of 8 colours, and choose whether a waypoint shows on the static map, the dynamic map or both. Hide whole kinds in *Waypoints → Kinds*.
- **Built-in Monumenta waypoints**: points of interest, dungeon entrances, towns, quest givers and traders from the [Monumenta Wiki](https://monumenta.wiki.gg/) (traders start hidden). Main quests follow the Monumenta Quest Flowchart.
- **Quest compass**: where Monumenta's quest compass points becomes a temporary waypoint.
- **Coordinates in chat** get a green **[+]**: click it for a temporary waypoint there.
- **Death waypoints**: when you die, a death waypoint marks the spot (with the date and time). The newest 5 per server are kept; older ones go by themselves. Turn it off in *Settings*.
- **Import from Xaero's Minimap and World Map**: settings, key bindings, waypoints and explored maps, from this game folder or another install (*Settings → Import*, or `/cakemap import`). Choose whether the imported waypoints show on the static map, the dynamic map or both. Xaero's files are only read.
- Fair play: only your own marker. No mob, player or entity radar.

## Keys

| Key | Action |
|---|---|
| M | World map |
| U | Waypoints |
| J | New waypoint where you stand |
| Y | Settings |
| Z (hold) | Enlarge the minimap |
| Numpad + | Temporary waypoint where you stand |
| (unbound) | Cave mode on/off, minimap zoom in/out, show/hide minimap |

## With CakeUI layouts

CakeMap adds two HUD pieces, `minimap` and `waypoints` (the markers in the world view). A layout's `hud` part can name them as ports like vanilla's pieces:

```json5
ports: {
  minimap: { x: -10, scale: 0.8 },   // or hide: true
  waypoints: { opacity: 0.7 },
}
```

and place a minimap of its own with the element type `cakemap:minimap` (`shape`: square/circle, `zoom`, `rotate`, `coordinates`, `waypoints`). Values: `cakemap.dimension`, `cakemap.mode`, `cakemap.cave`, `cakemap.minimap`. Button actions: `cakemap:open_map`, `cakemap:waypoints`, `cakemap:settings`.

## Static map packs

A static map pack holds, per shard, `assets/<namespace>/cakemap/static/<dimension namespace>/<dimension path>/`:
`meta.json` (`server`: the server key, e.g. `monumenta`; `title`), `<rx>_<rz>.png` (512 x 512, one pixel per block, transparent where unknown) and `samples.bin` (heights for the match check). Pack makers can export one from their own explored map with `devMode: true` in `config/cakemap/config.json` and `/cakemap export`.

## Files

- `config/cakemap/config.json`: settings.
- `cakemap/<server>/`: your waypoints (`waypoints.json`, with bookmarks, hidden kinds and the tracked waypoint), revealed shards and the dynamic map (`<namespace>/<path>/<rx>_<rz>.cmr`).

## Versions

Everything above works on all six jars (Fabric and NeoForge, Minecraft 1.20.4, 1.21.1 and 26.1.2). On NeoForge the settings open from the mod list's **Config** button (Mod Menu on Fabric).

## Building

One Gradle build makes all six jars (the Cake multiloader layout): `JAVA_HOME=<JDK 25> ./gradlew build` → `build/libs/cakemap-<fabric|neoforge>-<mc>-<version>.jar` (JUnit tests included). One version only: `-Pcake.versions=1.21.1`. It compiles against CakeUI's GitHub release jars (`cakeui_version` in `gradle.properties`); `-Pcakeui_dir=<folder>` uses a local CakeUI build instead. Dev client: `./gradlew :<mc>:<fabric|neoforge>:runClient -Pcake.runJvmArgs=-Dcakemap.selftest=world` opens a demo world, screenshots the minimap, world map and screens into `<mc>/<loader>/run/screenshots/`, and quits.

## License

MIT, except `core/src/main/resources/assets/cakemap/cakemap/waypoints/monumenta.json`, which is built from the Monumenta Wiki and is [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Bundles [JSVG](https://github.com/weisJ/jsvg) (MIT) to draw the icons. CakeMap downloads nothing.
