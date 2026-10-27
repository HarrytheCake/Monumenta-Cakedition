# Modpack

Monumenta Cakedition is a Fabric client modpack for the Monumenta MMORPG, made for English- and Chinese-speaking (simplified and traditional) players as equals.

## Language

**Pack**:
The mods, configs, resource packs and other files that ship to players, as one whole.
_Avoid_: Modpack (as a separate term), bundle

**Pack version**:
The semver number naming one state of the pack: major for a Minecraft version change, minor for mods added or removed or a new feature of ours, patch for mod updates and config tweaks.
_Avoid_: Letter suffixes (2.0.0E)

**Shipped default**:
A config the pack provides, put in place on an install only where the player has none yet.
_Avoid_: Default config (ambiguous with a mod's own defaults), preset

**Player config**:
A config as it is on one install; it belongs to the player, and the pack never replaces it without asking.
_Avoid_: Live config, user config

**Default change**:
A shipped default that differs from the one in the player's previous pack version. It is applied silently to an untouched player config and offered as a choice for a customised one; a changed keybind is always announced.
_Avoid_: Config migration, config update

**Translator**:
A person invited to translate the game's text on the pack's translation project.
_Avoid_: Using it for the mod (that is always "CakeText Translator" in full)

**CakeText Translator**:
Our optional mod that shows translations in game; always named in full in player-facing text.
_Avoid_: The Translator, translator mod

**Client lag**:
Slowness the player's own machine causes: mainly low FPS and stutter (frame-time spikes); slow shard switches and memory pauses only when severe. Ping and server lag are not client lag.
_Avoid_: Lag (alone)

**Low tier**:
The modest machine the pack's shipped defaults must run smoothly on: integrated graphics, 8 GB RAM, 4 GB of it for Minecraft.
_Avoid_: Minimum specs, potato

**Benchmark scene**:
A fixed place and activity on Monumenta where client lag is measured before and after a change: a crowded hub, a shard with many mobs, a dungeon fight full of particles, the Market shard full of barrels and signs.
_Avoid_: Test world, benchmark (alone)

**Render phase**:
One named part of drawing a frame, as the game's own profiler names it (terrain, entities, block entities, GUI, presenting the frame); client lag is broken down by render phase to see where a frame's time goes.
_Avoid_: Render pass, stage

**Timed run**:
A benchmark run that measures FPS and frame times with no measuring tools attached; only timed runs are compared with each other.
_Avoid_: Benchmark (alone)

**Breakdown run**:
A benchmark run with a measuring tool attached (GPU timers per render phase, a CPU profiler), done to explain a timed run's numbers; its own FPS is never compared, since the tool costs some.
_Avoid_: Profiling run (when GPU timers are meant), debug run

**Dev instance**:
The pack author's local launcher instance, where the pack is changed and tried first.
_Avoid_: Instance (alone), workspace

**Test instance**:
A separate copy of the dev instance for trying our own mods and the update flow; nothing is ever published from it.
_Avoid_: Test pack

**Install**:
The pack as it exists on one player's machine.
_Avoid_: Instance (for a player's copy)

**Release**:
A pack version made available to players, as a pack file or through the update channel. A released pack version never changes; any later change is a new pack version.
_Avoid_: Push (when a release is meant), build

**Withdrawn version**:
A released pack version with a critical bug or exploit, taken off the platforms; its archived download stays, marked as withdrawn.
_Avoid_: Deleted version, yanked

**Purge**:
Removing a released pack version, and the history that holds it, from everywhere outside the author's machine because it leaks personal data or enables attacks; the only time history is rewritten.
_Avoid_: Withdraw (a purge goes further), rollback

**Urgent update**:
A release that installs on a withdrawn or purged version must take as soon as possible, announced more strongly than a normal update.
_Avoid_: Hotfix, forced update (nothing is ever forced)

**Changelog**:
What changed in one pack version, written once and shown in game after an update, on its GitHub release and on the platforms; read in the player's game language, else English.
_Avoid_: Patch notes, update log

**Release notes**:
The whole page that comes with a release: install info, credits and that version's changelog.
_Avoid_: Changelog (that is only one part of it)

**Pack file**:
One downloadable file that installs a single pack version in a launcher, in a platform's modpack format (Modrinth `.mrpack`, CurseForge modpack file).
_Avoid_: mrpack (as the general term), export

**Adoption**:
The one-time step where an install made from a pack file starts following the update channel, keeping the player's optional-mod choices and never touching files the pack didn't ship.
_Avoid_: Conversion, migration

**Platform**:
A site that lists the pack and hosts its pack files for players to find and install: Modrinth, and later CurseForge.
_Avoid_: Store, launcher (a launcher only installs)

**Update channel**:
The source existing installs follow to receive new pack versions, without the player downloading a new pack file. There is one channel, reachable at two always-identical addresses (GitHub first, GitLab as fallback).
_Avoid_: Update server, branch (when the channel is meant), second channel

**Mirror**:
A standalone public copy of one of our own mods' source, published from this repository and never changed directly.
_Avoid_: Fork, sub-repo
