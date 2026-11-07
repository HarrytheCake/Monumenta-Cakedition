# CakeUI

CakeUI lets a pack developer redraw Minecraft's window, menus, loading screens and HUD from layout folders, with no Java.

## Language

**Layout**:
One complete look, kept as a folder `window_<name>/` with every part below it; exactly one layout is active at a time.
_Avoid_: Theme (in code and files), skin (that is a button's look)

**Part**:
One of the screens a layout can redraw: window, game launching screen, main menu, loading screen, HUD. Each is a folder of the layout.
_Avoid_: Screen (a part can cover several vanilla screens), page

**Window**:
The game's operating-system window (icon, title, title bar) and the root part that holds all others.
_Avoid_: Using it for an in-game panel (that is a subwindow)

**Subwindow**:
A named panel inside a part or another subwindow, kept as a folder `subwindow_<name>/`; the pause menu and the inventory are built-in subwindows of the HUD.
_Avoid_: Panel, widget, container

**Element**:
One drawn thing inside a part or subwindow: text, image, rect, bar, item or button. An element is an entry in a layout file, never a folder.
_Avoid_: Widget (that is vanilla's clickable class), component

**Port**:
A named piece of vanilla's drawing that a layout may hide, move, restyle or replace with elements (the splash text, the hotbar, a button). A port the layout doesn't mention is untouched.
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
Another mod's `cakeui` entrypoint, which adds game values, element types and button actions through the API before the first layout loads, may fill a port the active layout doesn't mention, and may add HUD pieces (ports of the HUD part, like vanilla's).
_Avoid_: Addon, extension

**Dev mode**:
The setting under which CakeUI reloads a layout when its files change and shows the error overlay.
_Avoid_: Debug mode, editor mode

**HUD editor**:
The in-game screen where a player or a layout maker arranges the HUD by dragging: move, scale, hide, fade, style and add elements.
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
