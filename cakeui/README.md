# CakeUI

A client-only Fabric mod for Minecraft 1.20.4 that lets modpacks redraw the game window, Mojang's launch screen, the main menu, the loading screens, the pause menu, the inventory and the in-game HUD from **layout folders**: elements, variables, 3D tilt, mouse parallax and hot reload, no Java needed.

With no layout chosen (the default), CakeUI changes nothing. It also leaves alone any piece of vanilla that the active layout doesn't mention, so other mods' buttons and screens keep working.

**Status:** 0.1.0 (in development). The window, the game launching screen, the main menu, the loading screens, the pause menu, the player's inventory and the HUD work (milestones 1–4 of 5). An API for other mods and a Mod Menu screen come next.

## Using a layout

1. Put a layout folder in `.minecraft/cakeui/layouts/`, e.g. `cakeui/layouts/window_example/` (the example is in [`examples/`](examples/)).
2. In `config/cakeui/config.json5`:
   ```json5
   {
     layoutPath: "cakeui/layouts/window_example",
     devMode: true,   // reload on save + show problems on screen
   }
   ```
3. Start the game. With `devMode` on, saving any file in the layout reloads it at once. Problems are listed in the top-left corner with file and line. A reload with problems keeps the previous version on screen.

Controls → CakeUI has two unbound keys: **Reload layout**, and **Show button ids** (dev mode).

## A layout folder

```
window_<name>/
├── window.json5          # format, design resolution, fonts
├── declaration.json5     # constants and variables for the whole layout
├── assets/               # PNG images, fonts/*.ttf
├── lang/en_us.json5      # layout strings: {t:key}; zh_cn.json5, zh_tw.json5, …
├── game_launch/          # Mojang's loading overlay (start, resource reloads)
├── loading_screen/       # connecting, loading terrain, preparing a world, saving, …
├── main_menu/
│   ├── layout.json5      # ports + elements
│   ├── declaration.json5 # constants and variables for the main menu
│   ├── splashes.txt      # optional splash list
│   └── subwindow_<name>/ # panels: layout.json5 + declaration.json5, nested as deep as you like
└── hud/
    ├── layout.json5          # HUD ports + elements
    ├── subwindow_pause/      # built in: the pause menu (Esc)
    └── subwindow_inventory/  # built in: the player's inventory (E, survival)
```

Files are JSON5: comments, trailing commas and unquoted keys are fine. For autocomplete in VS Code, keep keys quoted, map `*.json5` to `jsonc` and add `"$schema": "…/schema/layout.schema.json"` (see [`schema/`](schema/)).

### window.json5

```json5
{
  "format": 1,
  "design": [1920, 1080],   // r units are pixels of this screen
  "scale": "fit",           // fit | fill | stretch
  "perspective": 1000,      // camera distance for 3D, in design pixels
  "fonts": { "title": { "file": "fonts/MyFont.ttf", "size": 11 } },
  "title": "My pack {pack.version} · {window.title}",   // the window title; {window.title} is vanilla's
  "icon": ["icon_16.png", "icon_32.png"],              // window icon, PNGs in assets/ (Windows, Linux)
  "border": { "dark": true, "color": "#1b1f2a", "text": "#ffffff", "outline": "#ffb000" },
}
```

`border` is the Windows title bar: `dark` needs Windows 10 2004 or newer, the colours Windows 11; other systems ignore it. Anything left out stays as the system draws it, and switching to a layout without it puts the default back. The title can use any value and follows it (for example `{server.name}`).

### Declarations: constants and variables

```json5
{
  "constants": { "accent": "#ffb000", "gap": 12, "double": "= gap * 2" },
  "variables": {
    "open": false,                          // state: this folder's buttons can set it
    "hp": "= player.health / player.maxHealth", // computed every frame
    "label": "HP {player.health}",          // text with {…} is computed too
  },
}
```

A folder sees its own names and its parents' names, never a sibling's. A child's name hides the same name in a parent. A button can only set variables of **its own folder**.

### Values and expressions

| Write | Means |
|---|---|
| `12`, `"12r"` | 12 design pixels (scaled to the screen) |
| `"20px"` | 20 GUI pixels (follows Minecraft's GUI scale) |
| `"50%"` | half of the parent's width (for x/width) or height (for y/height) |
| `"50% - 10"` | lengths mix; a plain number next to a length is in design pixels |
| `"#ffb000"`, `"#ffb00080"` | colours (alpha last) |
| `"mouse.x * 8"` | any expression |
| `"HP {fmt(hp * 100, 0)}%"` | text with expressions; `{t:key}` = layout string |
| `"= open ? 'a' : 'b'"` | `=` makes a whole text value one expression |

Operators: `+ - * /`, `== != < <= > >=`, `&& || !`, `a ? b : c`. Functions: `sin cos tan rad deg abs sign sqrt pow floor ceil round mod min max clamp lerp smoothstep if fmt str num len upper lower contains rgb rgba alpha mix t`. There's no `%` operator, because `%` is the unit; use `mod(a, b)`.

Game values include `time`, `mouse.x`/`mouse.y` (−1…1 from the centre), `screen.width`, `screen.height`, `fps`, `lang`, `mc.version`, `pack.version`, `inWorld`, `server.name`, `server.address`, `world.time`, `world.day`, `player.*` (`health`, `maxHealth`, `food`, `armor`, `level`, `xp`, `x`, `y`, `z`, …), `menu.splash`, `menu.version`, `menu.fade`. Inside an element: `self.hovered`, `self.pressed`, `self.active`, `self.width`, `self.height`, `self.text`, `self.id`.

### Elements

```json5
"elements": {
  "title": { "type": "text", "text": "{t:my.title}", "x": 120, "y": 180, "size": 96, "color": "accent", "shadow": true },
  "bg":    { "type": "image", "src": "bg.png", "width": "100%", "height": "100%", "fit": "cover" },
  "play":  { "type": "button", "action": "singleplayer", "anchor": "center", "width": 400, "height": 80,
             "background": { "type": "image", "src": "panel.png", "slice": 12 },
             "states": { "hover": { "transform": { "scale": 1.05 } } },
             "transition": { "duration": 0.15, "easing": "back_out" },
             "elements": { "label": { "type": "text", "text": "Play", "anchor": "center", "size": 40 } } },
}
```

- **Types:** `text`, `image` (nine-slice `slice`, `fit` stretch/contain/cover/tile, sprite sheets with `frames` + `fps`), `rect` (gradients with `color2`, `outline`), `bar` (`value` 0…1), `item` (`item: "minecraft:diamond"`), `button`, `group`.
- **Place:** `anchor` (9 points), `origin`, `x`, `y`, `width`, `height`. Containers (`group`, `button`, subwindows) can also stack children with `arrange: "row" | "column"`, `gap`, `padding` and `align`.
- **Look:** `opacity`, `visible`, `order`, `layer` (`back` or `front` of the vanilla buttons).
- **Small windows:** Minecraft's Chinese, Japanese and Korean glyphs are 16×16 pixel art and break up when drawn small, so text containing them is never drawn smaller than vanilla's GUI scale 2 (it may then be larger than in your design). For an icon that must stay inside its box, set `readable: false`. Layout TTF fonts are always drawn at the design size.
- **3D and motion:** `transform` (`rotateX`, `rotateY`, `rotateZ`, `scale`, `skewX`, `z`, `pivotX`, …) with `perspective` on a container. `parallax: { move, tilt, smoothing }` follows the mouse. Clicks follow the tilt.
- **States:** `states: { hover, pressed, disabled }` override any property, eased by `transition`. `sounds: { hover, click }` take sound ids.
- **Button actions** (client-side only, never chat or commands): `singleplayer`, `multiplayer`, `realms`, `options`, `language`, `accessibility`, `modmenu`, `quit`, `back`, `{ connect: "address" }`, `{ url: "https://…" }` (vanilla asks first), `{ set: { name: value } }`, `{ toggle: "name" }`.

### Main menu ports

```json5
"ports": {
  "background": { "hide": true, "elements": { … } },   // hide the panorama, draw your own
  "logo": { "hide": true },                             // or move/scale: x, y, scale, opacity
  "splash": { "list": "splashes.txt", "mixVanilla": false, "color": "#ffb000", "x": 120, "y": 350 },
  "version": { "text": "{menu.version} + my pack" },
  "buttons": {
    "menu.singleplayer": { "visible": false },
    "options.language": { "anchor": "bottom_left", "x": 20, "y": -20, "skin": { … elements … } },
    "*": { … },                                         // every button not named
  },
}
```

A button is named by its label's translation key (`menu.multiplayer`), its text, or `#index`. **Show button ids** prints each id next to its button. A button the layout doesn't name stays where vanilla put it.

### Game launching screen

`game_launch/` redraws Mojang's overlay, at start and on every resource reload. Ports: `background` (the red fill; hide it and draw your own), `logo`, `bar` (hide, x, y, scale). `{launch.progress}` is 0…1, `{launch.fade}` follows the overlay's fade, `{launch.reloading}` is true on reloads. **At the very first start Minecraft's own font isn't loaded yet**: use images, rects and bars here, or text in a layout font.

### Loading screens

`loading_screen/` covers every "please wait" screen. `{loading.kind}` says which: `connecting`, `terrain` (loading terrain, also on Monumenta's shard switches), `reconfiguring`, `world` (preparing a singleplayer world), `progress`, `message` (saving, …). `{loading.progress}` is 0…1 where the screen knows it, else −1; `{loading.text}` is vanilla's text.

```json5
"ports": {
  "background": { "hide": true, "elements": { … } },
  "text": { "color": "#ffb000", "y": 60 },   // vanilla's lines: hide, x, y, scale, color, opacity
  "map": { "hide": true },                    // the chunk map while a world is prepared
  "buttons": { "gui.cancel": { … } },         // Cancel while connecting, Disconnect while reconfiguring
}
```

### HUD

`hud/layout.json5` redraws the in-game HUD. Every vanilla piece except chat and F3 is a port: `hotbar`, `crosshair`, `bossBars`, `health`, `armor`, `hunger`, `air`, `mountHealth`, `experience` (bar and level), `heldItemName`, `statusEffects`, `scoreboard`, `tabList`, `actionBar`, `title` (with the subtitle), `vignette`, `spyglass`, `pumpkin`. Each takes `hide`, `x`, `y`, `scale`, `opacity` and `elements`. A port's elements are drawn whenever vanilla would draw the piece, so a hidden piece can be replaced:

```json5
"ports": {
  "hotbar": { "y": "-4px", "opacity": 0.9 },
  "experience": { "hide": true, "elements": { "bar": { "type": "bar", "value": "player.xp", … } } },
  "title": { "scale": 0.75 },
},
"elements": { … },   // the HUD's own elements and subwindows (layer back = under the hotbar, front = on top)
```

Use `px` for HUD sizes: they match vanilla's HUD at every GUI scale. A piece the layout doesn't name stays exactly as vanilla (or another mod) draws it; a mod that replaces a piece entirely (say, the health and hunger bars) also takes it out of CakeUI's hands. Values: `hud.actionbar`, `hud.title`, `hud.subtitle`, `hud.hidden` (F1), and the `player.*` values. Nothing is drawn with F1.

### Pause menu and inventory

`hud/subwindow_pause/` and `hud/subwindow_inventory/` are **built-in subwindows**: each stands for a vanilla screen, covers the whole screen and is drawn only while that screen is open. Like a part, its `layout.json5` has `ports` and `elements` (`layer: "back"` behind the vanilla buttons and slots, `"front"` in front of them but under tooltips).

```json5
// hud/subwindow_pause/layout.json5
"ports": {
  "background": { "hide": true, "elements": { … } },   // vanilla's dim; or "opacity": 0.5
  "buttons": {
    "menu.game": { "visible": false },                  // the "Game Menu" title is a text widget, named like a button
    "*": { "skin": { … } },                              // every other button
  },
}
```

F3+Esc's "Game Paused" screen (no buttons) stays vanilla.

```json5
// hud/subwindow_inventory/layout.json5
"ports": {
  "background": { "hide": true, "elements": { … } },
  "panel": { "elements": { "frame": { "type": "rect", "x": "-3px", "y": "-3px", "width": "100% + 6px", "height": "100% + 6px", … } } },
  "player": { "x": "4px", "scale": 0.9 },              // or "hide": true
  "title": { "text": "Craft", "color": "#8a5a00" },     // "Crafting"; also hide, x, y, scale
  "buttons": { "#0": { … } },                           // the recipe-book button (it has no label)
}
```

**Inventory slots never move.** The `panel` port's elements (and the `title` port's) are drawn in the panel's box, 176×166 GUI pixels, and follow it when the recipe book pushes it aside, so give them sizes in `px` and `%`. With `panel: { "hide": true }` the slot frames go too (they are part of vanilla's texture), so draw your own. The creative inventory is a different screen and stays vanilla for now.

## Building

`JAVA_HOME=<JDK 21> ./gradlew build` (JUnit tests included). `./gradlew runClient -Dcakeui.selftest=1` opens a dev client that screenshots the title screen into `run/screenshots/` and quits; `-Dcakeui.selftest=world` also opens a test world, screenshots the loading screens, pause menu, inventory and HUD, and times the HUD with and without the layout.

## License

MIT, see [LICENSE](LICENSE). Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack. Most code was written with Claude (AI).
