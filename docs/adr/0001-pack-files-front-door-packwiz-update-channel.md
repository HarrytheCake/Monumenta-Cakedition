# Pack files are the front door; the packwiz branch is the update channel

New players install from a pack file published on a platform (Modrinth's `.mrpack` now, a CurseForge modpack file later), because that is one click in any launcher and is how players find packs. Existing installs keep updating from the `packwiz` branch, which Cakedition Updater reads for every kind of install, so updates don't wait on platform review and reach players on their next join. The Prism pre-launch command stays documented as an alternative for players who want updates on every launch.

## Consequences

- Every pack file must carry what Cakedition Updater needs to update that install (the bootstrap jar, `pack-version.txt`), and must never overwrite player state on a launcher's update (`servers.dat` goes to the updater's defaults instead).
- A pack file install only learns of an update when the pack version changes, which is one reason a released pack version never changes.
