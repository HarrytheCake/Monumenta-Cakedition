# CakeUI

A client-only Fabric mod for Minecraft 1.20.4 that lets modpacks redraw the main menu, and later the loading screens, HUD and game window, from **layout folders**: elements, variables, 3D tilt, mouse parallax and hot reload, no Java needed.

With no layout chosen (the default), CakeUI changes nothing. It also leaves alone any piece of vanilla that the active layout doesn't mention, so other mods' buttons and screens keep working.

**Status:** 0.1.0 (in development). The main menu works (milestone 2 of 5). The game launching screen, loading screens, pause menu, inventory, HUD and window come next.

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
└── main_menu/
    ├── layout.json5      # ports + elements
    ├── declaration.json5 # constants and variables for the main menu
    ├── splashes.txt      # optional splash list
    └── subwindow_<name>/ # panels: layout.json5 + declaration.json5, nested as deep as you like
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
}
```

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

## Building

`JAVA_HOME=<JDK 21> ./gradlew build` (JUnit tests included). `./gradlew runClient -Dcakeui.selftest=1` opens a dev client that screenshots the title screen into `run/screenshots/` and quits.

## License

MIT, see [LICENSE](LICENSE). Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack. Most code was written with Claude (AI).
