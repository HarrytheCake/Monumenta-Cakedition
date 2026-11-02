---
order: 1
---
# Cakedition Updater

Keeps the pack up to date and your settings safe. It never restarts the game for you and never downloads anything but the pack's own files.

## Pack updates

When you join a world or server and a new version of the pack is out, chat offers **Update when I close the game myself** or **Update now (closes the game)**. After the game has closed, a window installs the update and tells you when to start Minecraft again.

After an update, chat says which version you came from, lists what changed and offers **[Changelog]**. If the version you have was withdrawn because of a serious problem, a red message asks you to update on every join until you do.

## Resource pack updates

When a newer Monumenta resource pack is out, a separate chat message offers to update it. One click downloads it and reloads, without a restart.

## Backups

Every time you close the game, your server list and settings are saved. After a pack update, chat offers **[Restore server list]** and **[Restore configs]**. All backups: Mod Menu → Cakedition Updater → the settings button, or `/cakeupdate backups`. More in [Updates and backups](../updates.md).

## Moving in from another pack

Played Monumenta with another pack or launcher before? On the first join of each start, until you import or click **[Don't show again]**, chat offers to bring your settings over. You can also start it any time with `/cakeupdate import`.

1. Click **[Import from my old .minecraft]** and choose your old `.minecraft` folder, or the instance folder around it. The folder window can open behind the game.
2. Chat lists what it found:
   - options and keybinds: all of `options.txt`, so also your video settings and resource packs
   - UMM settings and locked slots, Monumenta Addons settings
   - Xaero's waypoints and map
   - servers your list doesn't have yet
   - ChatPlus windows and tabs, for CakeChat (they replace CakeChat's tabs)
3. Click **[Import these]**. New servers are added at once; the rest is copied when you close Minecraft, after every mod has saved its own files.

Your old pack is only read. Everything the import replaces is saved in a backup first, so you can put it back from the backups screen.

## Memory warning

If Minecraft has less than 3 GB or more than 12 GB of memory (your launcher's setting), chat warns you once, with a link to how to change it. See [Java and memory](../performance-tips.md#java-and-memory).

## Commands

- `/cakeupdate`: the update choices again
- `/cakeupdate backups`: the backups screen
- `/cakeupdate changelog`: what the last update changed
- `/cakeupdate import`: bring your settings over from another pack
