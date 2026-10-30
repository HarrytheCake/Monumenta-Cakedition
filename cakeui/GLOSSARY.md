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

**Dev mode**:
The setting under which CakeUI reloads a layout when its files change and shows the error overlay.
_Avoid_: Debug mode, editor mode
