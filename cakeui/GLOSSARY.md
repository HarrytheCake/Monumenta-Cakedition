# CakeUI

CakeUI lets a pack developer redraw Minecraft's window, menus, loading screens, container screens and HUD from layout folders, with no Java; mods can ship the look of their own container screens.

## Language

**Layout**:
One complete look, kept as a folder `window_<name>/` with every part below it; exactly one layout is active at a time.
_Avoid_: Theme (in code and files), skin (that is a button's look)

**Part**:
One of the screens a layout can redraw: window, game launching screen, main menu, loading screen, HUD, container screens. Each is a folder of the layout.
_Avoid_: Screen (a part can cover several vanilla screens), page

**Window**:
The game's operating-system window (icon, title, title bar) and the root part that holds all others.
_Avoid_: Using it for an in-game panel (that is a subwindow)

**Subwindow**:
A named panel inside a part or another subwindow, kept as a folder `subwindow_<name>/`; the pause menu and the inventory are built-in subwindows of the HUD.
_Avoid_: Panel, widget, container (that is a container screen)

**Container screen**:
A vanilla or modded screen with slots (a furnace, a chest, a mod's machine), laid out in the `container` part by its menu type id: `container/<namespace>/<path>/` for `<namespace>:<path>`. The screen stays the real screen; a layout restyles its background, panel, title lines, slots and buttons and adds elements. The player's own inventory is not one (it is the HUD's built-in subwindow).
_Avoid_: GUI, chest screen (chests are only one kind), handled screen (Yarn's name)

**Menu type id**:
The registry id of a container screen's menu (`minecraft:furnace`, `pigtech:treadmill`); it names the screen's folder in `container/`.
_Avoid_: Screen id, GUI id, container id (that is the number of an open menu)

**Mod layout**:
A mod's default look for its own container screens, a folder `assets/<modid>/cakeui/` in its jar laid out like a layout but holding only `container/`. CakeUI uses it for every menu the active layout doesn't mention, also with no layout chosen.
_Avoid_: Default theme, fallback layout (the fallback is the mod's own screen without CakeUI)

**Element**:
One drawn thing inside a part or subwindow: text, image, rect, bar, item or button. An element is an entry in a layout file, never a folder.
_Avoid_: Widget (that is vanilla's clickable class), component

**Port**:
A named piece of vanilla's drawing that a layout may hide, move, restyle or replace with elements (the splash text, the hotbar, a button, a container screen's slots). A port the layout doesn't mention is untouched.
_Avoid_: Slot (that is an inventory slot), hook

**Declaration**:
A folder's `declaration.json5`: the constants and variables visible in that folder and every folder below it, never in sibling folders.
_Avoid_: Variables file, config

**Game value**:
A read-only value CakeUI or another mod provides to every expression, such as `player.health` or `mouse.x`.
_Avoid_: Built-in variable, placeholder

**Design pixel**:
The unit `r`: a pixel of the layout's design resolution, scaled to the real screen.
_Avoid_: Reference pixel, virtual pixel

**Skin**:
Elements a layout draws in place of a vanilla button's look, while the vanilla button keeps handling clicks.
_Avoid_: Style (that is any restyling), theme

**Plugin**:
Another mod's `cakeui` entrypoint, which adds game values (also ones read from its menus), element types and button actions through the API before the first layout loads, may fill a port the active layout doesn't mention, and may add HUD pieces (ports of the HUD part, like vanilla's). A mod's layout needs no plugin.
_Avoid_: Addon, extension

**Dev mode**:
The setting under which CakeUI reloads a layout when its files change and shows the error overlay.
_Avoid_: Debug mode, editor mode

**HUD editor**:
The in-game screen where a player or a layout maker arranges the HUD by dragging: move, scale, hide, fade, style and add elements; on the main menu, pause menu, inventory and loading screens the same editor moves, scales and hides buttons and vanilla pieces.
_Avoid_: Editor mode (dev mode is not an editor), HUD designer

**Personal edits**:
A player's own HUD changes, saved in `config/cakeui/hud.json5` per layout (plus `vanilla`) and applied on top of the active layout.
_Avoid_: User layout, overrides

**Layout edits**:
The HUD editor's changes to a layout itself, saved in dev mode to the layout's `hud/edits.json5` and loaded after its hand-written files.
_Avoid_: Patch, generated layout

**Position mode**:
How a HUD piece keeps its place when the window or GUI scale changes: anchored (to the nearest of 9 screen anchors), percent of the screen, or fixed pixels.
_Avoid_: Alignment, docking

**Style preset**:
A ready-made replacement look for a HUD port (for example health as a bar), made of ordinary elements the player can then edit.
_Avoid_: Skin (that is a button's look), theme

**Movable**:
Something another mod places on the HUD itself (a chat window, a minimap) that the HUD editor moves and maybe resizes through that mod, which keeps storing its place.
_Avoid_: Widget, draggable

Config map, config hub, pack default: moved to CakeConfighub on 2026-11-24 (ADR 0013), see `../cakeconfighub/GLOSSARY.md`.
