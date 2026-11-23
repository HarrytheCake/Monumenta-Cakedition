# CakeConfighub glossary

**Config map**:
A data file (JSON5) describing one other mod's config file: where it is, its format, which options it holds (key path, type, range, label) and which versions of the mod it fits. CakeConfighub reads and writes that mod's options only through its map. Its **map id** is the file name without `.json5`.
_Avoid_: Adapter (no code per mod), schema

**Config hub**:
CakeConfighub's screen, where a player sets the UI/HUD options of every loaded mod that has a config map; Save writes them into each mod's own file and asks the mod to reload.
_Avoid_: Mod config menu, settings screen (that is each mod's own screen)

**Pack default**:
A value the pack wants for one option of another mod's config, listed in `config/cakeconfighub/pack-defaults.json5` with its earlier values (`history`); CakeConfighub writes it at startup only while the option is untouched, never over the player's own choice.
_Avoid_: Shipped default (that is a whole file yosbr puts in place once), forced setting

**Untouched**:
An option whose value in the file is missing or equals what CakeConfighub last wrote (the record), the current pack default, or one in its `history`. Any other value is the player's and is kept.
_Avoid_: Default (ambiguous), unchanged

**Record**:
`config/cakeconfighub/pack-defaults-applied.json5`, per player: the value CakeConfighub last wrote to each option through pack defaults. The hub's Save never writes to it.
_Avoid_: Cache, state file

**Reload hook**:
What a mod gives CakeConfighub so it can re-read its config right after the hub saved it: `CakeConfighubApi.registerConfigReload`, or a class named by the metadata key `cakeconfighub:reload` with `public static void cakeconfighubReloadConfig()`. Never a command.
_Avoid_: Reload command (that is `/<mod> reload`, for players' hand edits)
