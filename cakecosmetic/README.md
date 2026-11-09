# CakeCosmetic

Choose the pictures behind Minecraft's main menu and loading screens from a gallery. On [Monumenta](https://www.playmonumenta.com/), backgrounds unlock as you earn advancements. Part of [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition). Fabric 1.20.4, client only. Needs Fabric API and [CakeUI](https://github.com/HarrytheCake/CakeUI).

**In development** (not released yet).

## What it does

- **Gallery:** every background as a thumbnail. Pick one for the main menu and one for loading screens, or let each place shuffle through the ones you tick. Open it from the button in the main menu's top-right corner, Mod Menu, or a key (unbound).
- **Unlocks:** in advancement mode (the default) some backgrounds are locked; earning the advancements a background needs on Monumenta unlocks it for good.
- **Backgrounds from resource packs:** `assets/<namespace>/cakecosmetic/backgrounds/<name>.png` (id `<namespace>:<name>`), with an optional `<name>.json`: `{ "title": "Dusk" or { "en_us": "Dusk", "zh_cn": "黄昏" }, "artist": "Someone" }`. Pictures are shrunk to the window size when loaded.
- **Loading screens** show a slideshow (5 s per picture, 0.5 s fade); the **main menu** picks one each time it opens. A layout of [CakeUI](https://github.com/HarrytheCake/CakeUI) that redraws these backgrounds itself wins; layouts can read `{cakecosmetic.menu}` and `{cakecosmetic.loading}`.

## Files (`config/cakecosmetic/`)

- `config.json5`: `mode` (`"advancement"` or `"all"`) and `devMode`. There is no button for these on purpose.
- `rules.json5`: unlock rules, `{ "<background id>": { "advancements": [ { "id": "...", "title": "..." } ], "secret": false } }`. A background needs all of its advancements; one without a rule is free. Modpacks ship this file.
- `unlocked.json`, `choices.json`: what this install has unlocked and what you picked.

## Dev mode (for pack makers)

With `devMode: true`, the gallery shows ids, an **Edit unlock rule** button (lists every advancement the game knows on the current server, searchable; Save writes `rules.json5` at once), **New player view** and **Forget my unlocks**. In dev mode, advancements count on any server, so a singleplayer world works for testing.

## License

MIT (see `LICENSE`). Background pictures belong to their artists and come from resource packs, not from this mod.
