# CakeConfighub

One screen for the UI and HUD options of many mods. CakeConfighub writes each value straight into that mod's own config file, changing only that value (comments, order and every other setting stay as they are), and asks the mod to use it at once where the mod allows it. Modpacks can also ship their own default values, which are applied at startup only while the player hasn't changed them.

Part of [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition). Client only, for Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2. Needs no other mod. MIT.

## For players

Open it from:

- **Mod Menu** (Fabric): CakeConfighub → Configure.
- **The mod list** (NeoForge): CakeConfighub → Config.
- **A key**: "Open CakeConfighub" in Controls (not bound by default).
- **`/cakeconfighub`** in chat (Fabric with Fabric API; NeoForge 1.21.1 and 26.1.2). It runs on your side and is never sent to the server.

The first screen lists every installed mod CakeConfighub knows. A greyed-out mod has a version CakeConfighub doesn't know yet, or hasn't written its config file yet (start the game once, or save that mod's own settings once). On a mod's page:

- Change what you like, then **Save**. CakeConfighub then tells you whether the mod uses the new settings now or after a restart.
- **Reset** sets one option back to the pack's default (a gold ◆ marks options the pack manages) or else to the mod's default. **Reset this mod** drops every change on the page. A yellow * marks unsaved changes.
- Every mod keeps its own settings screen and file. CakeConfighub never creates a missing file and leaves a file it can't read alone.

## For pack makers

**Pack defaults** (Fabric for now): put `config/cakeconfighub/pack-defaults.json5` in the pack. Per config map id, per option key: the value you want, and the values you shipped before in `history`:

```json5
{
  format: 1,
  "zoomify": { "initialZoom": { value: 6, history: [4] } },
  "sodium": { "quality.weather_quality": { value: "FAST", history: ["DEFAULT"] } },
  "minecraft": { "renderClouds": { value: "\"false\"" } },
}
```

At each start, before other mods read their configs, CakeConfighub writes your value where the option is **untouched**: missing from the file, or equal to the value CakeConfighub wrote last time, your current value or one in `history`. Anything else is the player's choice and is kept, silently. What CakeConfighub wrote is remembered per player in `config/cakeconfighub/pack-defaults-applied.json5`: never ship that file. Ship `pack-defaults.json5` as a file the pack replaces when it changes. Problems (unknown option, value out of range) go to the log as `CakeConfighub pack defaults: …`.

**Adding or overriding maps:** put `<map id>.json5` into `config/cakeconfighub/configmaps/`; a map there replaces any map with the same id from a mod's jar. `/cakeconfighub reload` reads every map again and tells you how many it read and how many problems it found (details in the log); pack defaults still apply only at startup.

Maps for these mods come in the Fabric/NeoForge 1.20.4 jars, written for the versions the Cakedition pack ships: Minecraft (options.txt, UI options only, never key bindings), AppleSkin, AsteorBar, the Unofficial Monumenta Mod, Zoomify, Better Ping Display, Item Highlighter, TslatEntityStatus, Shulker Box Tooltip, BetterF3; and, for pack defaults only (not on the screen), Sodium, Sodium Extra, LambDynamicLights, Entity Model Features, Entity Texture Features.

## For mod developers

**Ship a map for your mod** in your own jar, `assets/<your mod id>/configmaps/<your mod id>.json5` (it replaces CakeConfighub's copy, if there is one):

```json5
{
  format: 1,
  mod: "mymod",                   // your mod id (or a list of ids)
  versions: "*",                  // or a range like ">=2.13 <3"; other versions are greyed out and skipped
  file: "config/mymod.json5",     // relative to the game folder
  type: "json5",                  // json | json5 | toml | options (vanilla key:value) | properties
  reload: "auto",                 // auto = your reload hook if you have one; restart = always after a restart
  hub: true,                      // false = pack defaults only, not on the screen
  title: "mymod.config.title",    // a translation key or plain text
  sections: [
    { title: "HUD", options: [
      { key: "hud.enabled", type: "bool", label: "mymod.config.hud.enabled" },
      { key: "hud.scale", type: "float", min: 0.5, max: 2, step: 0.25, label: "mymod.config.hud.scale", default: 1 },
      { key: "hud.corner", type: "enum", values: ["top_left", "top_right", { value: "bottom", label: "mymod.config.bottom" }] },
      { key: "hud.colour", type: "colour", label: "mymod.config.hud.colour" },  // "#RRGGBB"; alpha: true for "#AARRGGBB"; colourAs: "int"
    ] },
  ],
}
```

Keys are paths into the file (`overlay.textScale` for nested JSON objects or TOML tables). Option types: `bool`, `int`, `float` (`min`, `max`, `step`), `enum`, `string`, `colour`. Labels are translation keys with the text itself as fallback.

**Reload hook** (so a saved change works at once), without depending on CakeConfighub: name a class in your metadata, with a `public static void cakeconfighubReloadConfig()` that reads your config file again (it runs on the render thread, right after the file was saved).

- Fabric, `fabric.mod.json`: `"custom": { "cakeconfighub:reload": "com.example.mymod.ConfigReload" }`
- NeoForge, `META-INF/neoforge.mods.toml` (1.20.4: `mods.toml`): `[modproperties.mymod]` then `"cakeconfighub:reload" = "com.example.mymod.ConfigReload"`

Or, with CakeConfighub as an optional dependency: `CakeConfighubApi.registerConfigReload("mymod", () -> MyConfig.load())`. Without a hook the player is told the change takes effect after a restart. CakeConfighub never sends a command for a reload: a client command that isn't registered would go to the server as chat.

## Languages

English, 简体中文, 繁體中文.

## Build

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build
```

Builds Fabric and NeoForge jars for Minecraft 1.20.4, 1.21.1 and 26.1.2 into `build/libs/` (`cakeconfighub-<loader>-<mc>-<version>.jar`; `cakeconfighub-<version>.jar` is the Fabric 1.20.4 one). Gradle runs on JDK 25; each version compiles with its own Java (17, 21, 25).

## License

MIT, see [LICENSE](LICENSE). Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack. Most code was written with Claude (AI).
