---
order: 6
---
# CakeUI

Lets you arrange your HUD the way you like it. On its own it changes nothing: until you edit something, the game looks as it always did.

## HUD editor

Open it while you're in a world: Mod Menu → CakeUI → **Edit HUD…**, or the **Edit HUD** button at the right end of CakeChat's tabs. You can also set a key for it in Controls ({key:key.cakeui.editHud}).

- **Drag** a piece to move it, **scroll** over it to make it bigger or smaller. Arrow keys nudge it; hold Alt to stop it snapping.
- **Right-click** a piece to hide it, change its opacity or its style (health as a bar or a number, for example), or reset it.
- **Right-click empty space** to add your own text, box, bar, image, item or inventory. Images go in `config/cakeui/images/`.
- CakeChat's windows and UMM's ability bar can be moved here too.
- Ctrl+Z / Ctrl+Y undo and redo, H hides the editor's panel, **Reset all** puts everything back. **Done** keeps your changes; Esc twice throws them away.

Your edits are only for you: they're saved in `config/cakeui/hud.json5` on your PC, and pack updates never change them.
