# CakeUI

A client-only mod for **Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2** that lets modpacks redraw the game window, Mojang's launch screen, the main menu, the loading screens, the pause menu, the inventory and the in-game HUD from **layout folders**: elements, variables, 3D tilt, mouse parallax and hot reload, no Java needed.

With no layout chosen (the default), CakeUI changes nothing. It also leaves alone any piece of vanilla that the active layout doesn't mention, so other mods' buttons and screens keep working.

**Status:** 0.3.0. The window, the game launching screen, the main menu, the loading screens, the pause menu, the player's inventory and the HUD work, with a settings screen (Mod Menu on Fabric, the mod list's Config button on NeoForge) and an API for other mods (milestones 1–5 of 5) and an in-game HUD and screen editor ([Moving your HUD](#moving-your-hud-hud-editor)). Chests and other screens may come later. What differs between Minecraft versions and loaders is listed under [Versions](#versions).

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

With [Mod Menu](https://modrinth.com/mod/modmenu) on Fabric, or the Config button in NeoForge's mod list, CakeUI opens its settings: pick a layout from `cakeui/layouts/`, turn dev mode and button ids on or off, reload, or open the layouts folder. Every change applies at once.

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

## Moving your HUD (HUD editor)

In a world, open **Mod Menu → CakeUI → Edit HUD…** (or bind the "Edit HUD" key under Controls → CakeUI). The HUD stays live behind the editor:

- **Drag** a piece to move it; it snaps to the screen's edges and centre and to other pieces (hold **Alt** to place it freely). **Arrow keys** nudge the selected piece (Shift: 10 pixels).
- **Scroll** over a piece to make it bigger or smaller.
- **Right-click** a piece: hide or show it, opacity, position mode, reset.
  - *Anchored* (default): it keeps its distance to the nearest edge or corner when the window or GUI scale changes.
  - *Percent*: it keeps its place as a share of the screen.
  - *Fixed*: it stays at exact pixels from the top-left corner.
- **Previews** shows boxes for pieces that aren't on screen right now (boss bars, titles, the tab list …) so you can place them too.
- **Done** saves, **Cancel** (or Esc twice) throws the changes away, **Ctrl+Z / Ctrl+Y** undo and redo.

- **Right-click empty space** to add your own **text, box, bar or image**. Right-click it and choose **Edit…** to change it:
  - Text can show live game values: `{fps} fps`, `{fmt(player.x, 0)} {fmt(player.z, 0)}`. The **Game values** field filters a few quick buttons; **All…** opens every value with its current reading.
  - A bar's value is a number from 0 to 1, e.g. `player.health / player.maxHealth`.
  - Images are PNG files in `config/cakeui/images/` (**Folder…** opens it).
  - **Show only when** (advanced) hides the element unless a condition is true, e.g. `player.health < 10`.
- The layout's own HUD elements appear as "Layout: name" and can be moved like the rest.
- **Style** (right-click health, hunger, armor, air, mount health or XP): show it as a bar, a number or both (XP: a slim bar or just the level). The vanilla piece is replaced by an element you can move and edit; Delete brings vanilla's back.
- **Crosshair** (right-click it → Style): dot, cross, circle or your own image, with size, gap, thickness, colour and "invert" (like vanilla: visible on any background). The attack-cooldown indicator always stays.
- **Item** and **Inventory** elements show your own slots: the held item, your armor, the hotbar as a grid … with counts, durability and cooldown each on or off.
- Images can be **animated GIF or WebP**; they play at the file's own speed. Big animations are scaled down to fit (at most 512 px a side, 8 million pixels for all frames) and load in the background, so they appear a moment later.
- Every added element has a **Scale** field in its panel (the same as scrolling over it).

Your arrangement is saved in `config/cakeui/hud.json5`, separately for each layout ("vanilla" when no layout is active), and drawn on top of the layout. Pieces another mod draws itself are shown greyed out and can't be moved here.

**Other screens:** the same editor works on the **main menu**, the **pause menu**, the **inventory** and **loading screens**: press the Edit HUD key while the screen is open, or use **Main menu… / Pause menu… / Inventory… / Loading…** in CakeUI's settings. Every button can be moved, resized (scroll) and hidden, and so can the main menu's logo, splash and version text, the inventory title and the loading text and map. (The game-launching screen has no cursor, so only a layout can change it.)

**Other mods' pieces:** mods can let the editor move what they place themselves: CakeChat's chat windows (move and resize; CakeChat also adds an "Edit HUD" button to the chat) and CakeMap's minimap (move, and resize with its corner handle). They keep storing the place in their own settings.

**Layout makers:** in dev mode the editor starts on **Saving: layout** and writes the layout's `hud/edits.json5` (loaded after `layout.json5`; players' own edits go on top). Switch to **Saving: my HUD** for your own edits. Added elements saved into a layout take their images from its `assets/` folder.

### API version 3: the HUD editor

- `addMovables(owner, source)`: things your mod places itself (a chat window, a minimap) become movable in the HUD editor. Each `Movable` gives its `box`, takes `place` while the player drags (and resizes, if `resizable`), and `save`s on Done (on Cancel it is put back with `place` and told `cancel`). You keep storing the place.
- `openHudEditor(parent)`: opens the editor (e.g. from a button of yours); false outside a world.
- `HudPiece.editable()`: return false for a piece the editor must not offer (one covering the whole screen, or one you offer as a movable).

## Third-party code

CakeUI bundles the TwelveMonkeys ImageIO WebP reader and the parts it needs (imageio-webp, imageio-core, imageio-metadata, common-image, common-io, common-lang 3.12.0), © Harald Kuhr, BSD 3-Clause; the licence is in the jar at `META-INF/licenses/TwelveMonkeys-LICENSE.txt`.

## For mod developers

Other mods can add game values, element types and button actions, and fill a background the active layout doesn't redraw. Register a `CakeUIPlugin` (a public no-argument constructor); CakeUI calls it before the first layout loads, so load order doesn't matter:

- **Fabric:** a `cakeui` entrypoint in `fabric.mod.json`:

  ```json
  "entrypoints": { "cakeui": ["com.example.MyCakeUIPlugin"] }
  ```
- **NeoForge:** a Java service file in your jar, `META-INF/services/io.github.harrythecake.cakeui.api.CakeUIPlugin`, holding one line: `com.example.MyCakeUIPlugin`.

The API's package and class names are the same in every version; only Minecraft's own types in it follow the version (`GuiGraphics` on 1.20.4/1.21.1, `GuiGraphicsExtractor` on 26.1.2), so build your plugin against the CakeUI jar of the same Minecraft version and loader.

```java
public class MyCakeUIPlugin implements CakeUIPlugin {
    @Override
    public void register(CakeUIApi api) {
        // {mymod.mana} in any expression
        api.registerValue("mymod.mana", () -> MyMod.mana());
        // { "type": "mymod:gauge", "level": "= mymod.mana / 100", "tone": "#40a0ff" }
        api.registerElement("mymod:gauge", new ElementType() {
            public Map<String, PropKind> properties() { return Map.of("level", PropKind.NUMBER, "tone", PropKind.COLOR); }
            public double defaultWidth() { return 120; }   // design pixels
            public double defaultHeight() { return 12; }
            public void draw(ElementContext c) {
                int w = (int) (c.width() * c.number("level", 0));
                c.drawContext().fill(0, 0, w, (int) c.height(), c.color("tone", 0xFFFFFFFF));
            }
        });
        // "action": "mymod:open_map"  or  { "mymod:open_map": "argument" }
        api.registerAction("mymod:open_map", (argument, screen) -> MyMod.openMap(argument));
        // the main menu's background whenever the active layout doesn't redraw it (also with no layout)
        api.fillPort("main_menu/background", "mymod", (context, screen, alpha) -> {
            if (!MyMod.hasPicture()) return false;   // false: vanilla's panorama as usual
            MyMod.drawPicture(context, screen.width, screen.height, alpha);
            return true;
        });
        // a HUD piece of your own: layouts can name it as a port of the "hud" part, like vanilla's pieces
        api.addHudPiece("minimap", "mymod", (context, screenWidth, screenHeight, tickDelta) -> MyMod.drawMinimap(context));
    }
}
```

- Value names use dots (`mymod.mana`); element types and actions need a namespace (`mymod:…`), so they never clash with CakeUI's.
- An element type gets every common property (place, size, transform, states, opacity, …) plus the ones it declares; a property name must keep the kind it has everywhere else (`color` is always a colour). CakeUI places the element and calls `draw` with the matrix at its top-left corner; `ElementContext` gives its size, state and property values (eased between states like built-in ones).
- Suppliers and `draw` run on the render thread, often every frame: keep them cheap. A `draw` that throws is listed once as a layout problem and skipped.
- **Port fillers** (API version 2, CakeUI 0.2.0): `main_menu/background` (the panorama and its dark overlay) and `loading_screen/background` (connecting, loading terrain, preparing a world, …). The filler runs only while the active layout doesn't mention that port: a layout's own background always wins. One mod per port; a second `fillPort` for the same port throws. A filler that throws is logged once and vanilla draws.
- **HUD pieces** (API version 2, CakeUI 0.2.0): `addHudPiece(name, owner, piece)` adds a piece CakeUI draws every HUD frame (not with F1), after vanilla's pieces and before the layout's front layer. A layout's `hud` part can name it under `ports` (`hide`, `x`, `y`, `scale`, `opacity`, `elements`) exactly like vanilla's pieces; `HudPiece.pivotX/Y` is the point `scale` grows from. Names are one word, not a vanilla port's, one mod each; register from the plugin so layouts that name it load cleanly. A piece that throws is logged once and switched off. First user: CakeMap (`minimap`, `waypoints`).
- CakeUI's own actions never send chat or commands; an action a mod registers is that mod's responsibility.
- Make CakeUI an optional dependency: the plugin class is only loaded when CakeUI is installed. Registering later works too (`CakeUIApi.get()`), and is used from the next `reload()`.

## Versions

| | 1.20.4 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| Everything above | Fabric, NeoForge | Fabric, NeoForge | Fabric, NeoForge |
| 3D tilt (`rotateX`, `rotateY`, `z`) | true perspective | true perspective | drawn as the closest flat (affine) shape: it turns and shrinks, without foreshortening |
| `opacity` on a vanilla piece's port | yes | yes | only where CakeUI passes the colour (text lines, the inventory panel, the launch logo); other vanilla pieces are shown or hidden |
| `item` elements on the first main menu | yes | yes | appear once the game's data is loaded (after the first world) |

On NeoForge the game launching screen (`game_launch`) shows on resource reloads (F3+T, resource packs) and, on 26.1.2, at start too; on 1.20.4 and 1.21.1 NeoForge's own loading screen covers the first start. The main menu's `version` port has no effect on NeoForge, which draws its own branding lines.

## Building

One Gradle build makes all six jars (the Cake multiloader layout): `JAVA_HOME=<JDK 25> ./gradlew build` → `build/libs/cakeui-<fabric|neoforge>-<mc>-<version>.jar` (JUnit tests included). One version only: `-Pcake.versions=1.21.1`. Dev client: `./gradlew :<mc>:<fabric|neoforge>:runClient -Pcake.runJvmArgs=-Dcakeui.selftest=1` screenshots the title screen into `<mc>/<loader>/run/screenshots/` and quits; `-Dcakeui.selftest=world` also opens the world `run/saves/cakeui-selftest`, screenshots the loading screens, pause menu, inventory and HUD, and times the HUD with and without the layout.

## License

MIT, see [LICENSE](LICENSE). Made for the [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition) modpack. Most code was written with Claude (AI).
