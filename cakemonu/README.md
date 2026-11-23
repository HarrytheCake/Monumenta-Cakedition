# CakeMonu

Monumenta helpers that bridge other mods, for [Monumenta](https://www.playmonumenta.com/). Part of [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition). Client only, Fabric, Minecraft 1.20.4. Needs Fabric API.

## Ability icons on their own (Unofficial Monumenta Mod)

The [Unofficial Monumenta Mod](https://modrinth.com/mod/unofficial-monumenta-mod) (UMM) shows your class's abilities as one row or column of icons. With CakeMonu, any of those icons can have **its own place and size** on the HUD, for example your most important cooldown big and next to the crosshair, the rest in UMM's bar.

- Open CakeMonu's settings (Mod Menu → CakeMonu, or `/cakemonu`) and switch **Own place** on for an ability. Its icon leaves UMM's bar and sits next to it.
- Press **Edit HUD…** to open [CakeUI](https://github.com/HarrytheCake/CakeUI)'s HUD editor (CakeUI 0.3.0 or newer): drag the icon to move it, drag its corner or scroll to resize it, then Done.
- UMM still draws every icon, so cooldowns, charges, the off-cooldown flash, duration bars and silence look exactly as in UMM's bar, and UMM's own settings (icon size, text, show only on cooldown, …) still apply.
- Places are saved per ability, so each class has its own layout. Abilities of another class keep their place for when you switch back.
- **All back to the bar** puts every icon back.

Without CakeUI, icons with their own place stay where CakeMonu put them (next to UMM's bar); you can still change them in the file below.

CakeMonu changes how UMM's ability bar is drawn, so it only does that for UMM versions it was checked against (now: **UMM 1.10**). With any other UMM version it leaves UMM's bar as it is and says so in its settings.

## Files

`config/cakemonu/ability-icons.json` (your own layout):

```json
{
  "format": 1,
  "icons": {
    "mage/magma shield": { "anchorX": 0.5, "anchorY": 1.0, "x": -60, "y": -70, "size": 0 }
  }
}
```

The key is UMM's ability id (class/ability, lower case). `anchorX`/`anchorY` (0, 0.5 or 1) pick the screen corner, edge or centre the icon keeps its distance to; `x`/`y` are that distance in GUI pixels; `size` 0 = UMM's icon size. `/cakemonu reload` reads the file again.

## License

GPL-3.0-or-later (see `LICENSE`), because CakeMonu hooks into the Unofficial Monumenta Mod's code, which is GPL-3.0. UMM is by Njol and contributors; CakeMonu contains none of its code and doesn't ship it.
