# Roadmap

What gets done in which order, and why. It follows the design tree from the grilling sessions of 2026-10-27/28 (Q1–Q63: the whole modpack; Q64–Q87: client lag). Terms are the glossary's ([GLOSSARY.md](GLOSSARY.md)); decisions with lasting reasons are in [adr/](adr/). Update the status marks as items finish.

✅ done · ⏳ open · ⏸ waiting for the author's go · ⛔ blocked by another item · 💤 noted for later

## The tree

```
Make Monumenta Cakedition
├── 1 Publish platforms
│   ├── ✅ Q15 first Modrinth upload by hand; auto-publish from the version after
│   ├── ⏳ Upload the v2.2.2 pack file (one page, English + 简体中文, AI-content on, old versions kept; Modrinth's newest is a 2025 "2.2.0", not our GitHub v2.2.0)
│   ├── ⏳ Q17 Modrinth projects for Cakedition Updater + CakeText → pack metafiles point at Modrinth
│   └── ⏳ Q16 CurseForge audit → CurseForge pack file + projects for our mods
├── 2 Distribution
│   ├── ✅ Q56 adoption (Cakedition Updater 1.4.1); Q22 pack files never carry player state
│   ├── ✅ Q24 install guide on the GitHub wiki (Install-Guide / 安装指南 / 安裝指南, 2026-10-28)
│   └── ⛔ Q40–42 GitLab fallback ← our mods on Modrinth + packwiz-installer.jar in the pack file
├── 3 GitHub management
│   ├── ✅ Q29 GitHub rulesets: no force-push / no deletion on packwiz + both mirrors' main (2026-10-28); GitLab protected branches ⏳
│   ├── ✅ Q28 push_all mirrors caketext/ and pushes the packwiz branch to GitLab too (ADR 0003)
│   ├── ✅ Q30 issue templates (bug report, suggestion; English + 简体中文) on main, 2026-10-28
│   └── ✅ Q31 README on main (English + 简体中文, 2026-10-28)
├── 4 Pack contents
│   ├── ✅ 2.2.2: RRLS, Translations for Sodium, Paperbox 1.1.0
│   ├── ⏸ Client-lag phase 1: step-1 rerun + FO set (Q83), spark pass + GPU-timer batch (Q87)
│   ├── ⛔ Client-lag phase 2: changes to existing shipped defaults ← Default changes
│   └── 💤 Q84 GUI benchmark scene
├── 5 Our own mods
│   ├── ✅ 1.4.0 backups checked in game (2026-10-28)
│   ├── ⏳ Q45 urgent-update flag + Q59–62 in-game changelog + Q69 heap warning: Cakedition Updater 1.5.0 built, 12 tests, in-game test passed 2026-10-28 (title-screen toast not confirmed); not released yet
│   ├── ⏳ Q35–39, Q44 Default changes (per file; options.txt per key; changed keybinds always announced)
│   ├── ⛔ Q47 multi-loader / multi-version ← own Modrinth project + a request from outside the pack
│   └── ⏳ Q48 CakeText's own grilling session; its test steps 10–11
└── 6 Community / legal
    ├── ⏳ Q49 Monumenta's written OK for three uses → Crowdin (invite-only, Q50), public repo, shipping strings
    ├── ⛔ Q51 translator credits in three places ← strings ship
    ├── ⏳ Q52 the author's Discord link on the pages and README
    └── ⏳ Monumenta's mod-rules OK for every client-lag phase-1 addition
```

## Order

### Stage A: protect what is out there, then reach "2.2.x is done" (Q4)

1. ✅ **Branch protection** (Q29). GitHub: one ruleset per repo blocking force pushes and deletion (checked through the API). ⏳ GitLab: protect `packwiz` / `main` with force push off (can't be checked without a login).
2. ✅ **push_all mirrors caketext/ and pushes every repo to GitHub and its GitLab copy** (Q28, ADR 0003). A dry-run push to every remote must pass before anything goes out; all six remotes are set up.
3. ✅ **1.4.0 backup checks in game.** No bugs, so no 1.4.2.
4. ✅ **README on main** (Q31). The README is the one file written to `main` from here (in a separate worktree, pushed `HEAD:main`); the Friday bot owns the rest. Once the Discord link (Q52) and the install guide (Q24) exist, link them from it.
5. ⏳ **Upload the v2.2.2 pack file to Modrinth** (by hand). Page text, project settings and the version's changelog are ready in [platforms/modrinth.md](platforms/modrinth.md); the install guide is on the wiki in all three languages.
6. ✅ **Issue templates** (Q30) on `main`, the same exception as the README, plus contact links (wiki, Monumenta Discord, QQ group).

### Stage B: the first release with an in-game changelog

7. **Cakedition Updater 1.5.0:** urgent-update flag, changelog, heap warning together, since all touch the "Modpack updated" message. Changelog text ships in the pack, one entry per version, also appended to the GitHub release by CI.
8. **Client-lag phase 1**, in parallel (⏸ waiting for a ~70 min idle slot on the laptop): baseline moved to 2.2.2, step-1 rerun on AC power with the FO set, then the Q87 breakdown runs; winners go to Monumenta for a mod-rules OK.
9. **Pack 2.3.0:** the phase-1 mod changes + Cakedition Updater 1.5.0. Its changelog is the first one players see in game.

### Stage C: independence and reach

10. **Modrinth projects for our mods** (Q17), metafiles switched to them, auto-publishing (mc-publish) for pack and mods.
11. **Default changes** (Cakedition Updater 1.6.0), which unlocks client-lag phase 2.
12. **GitLab fallback:** `packwiz-installer.jar` in the pack file, then the fallback in Cakedition Updater (Q41: GitHub first, GitLab on failure or timeout, config key to prefer GitLab).
13. **CurseForge audit**, then CurseForge.

### Any time (waiting on other people)

- Monumenta's written reply (Q49) → CakeText grilling session → Crowdin → credits (Q51).
- Monumenta's mod-rules answers gate stage B's release.
- Multi-loader (Q47) and a beta channel (Q21) only on outside demand.
