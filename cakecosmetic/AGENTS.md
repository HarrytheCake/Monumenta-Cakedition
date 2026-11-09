# CakeCosmetic: agent reference

Client-only Fabric mod (`cakecosmetic`, package `io.github.harrythecake.cakecosmetic`) for Minecraft **1.20.4**: a gallery of backgrounds for the main menu and loading screens, unlocked by Monumenta advancements. A **CakeUI plugin** (hard dependency, `cakeui` entrypoint). Terms are in [`GLOSSARY.md`](GLOSSARY.md): background, gallery, unlock rule, advancement mode, all mode. Player-facing page: [`README.md`](README.md); keep both current.

Planned standalone repo `HarrytheCake/CakeCosmetic` (remote `cakecosmetic`, MIT). **Not public yet** (user, 2026-11-04): the folder is not in the root `.gitignore` whitelist and not in `push_all` TARGETS until the user says so. Source of truth: this folder in the Monumenta-Cakedition repo.

## Decisions (grilled with the user 2026-11-04, Q1–Q27)

Full list in the local `docs/cakecosmetic-design.md`; the short version:
- **v1 = the gallery only, no network code.** A CakeCloud part comes later (own grilling).
- **Drawing through CakeUI** (local ADR 0008): CakeUI's API lets a plugin fill a port (main menu background, loading screen background) even with no layout; a layout that mentions the port wins. Replaces the Loading Backgrounds mod (its look is the default: shuffle, 5 s, 0.5 s fade).
- **Choices:** main menu and loading screens separately: one background or shuffle over a ticked set; default shuffle over all unlocked. Main menu: one random pick each time it opens; loading screens: slideshow. A "Vanilla panorama" tile.
- **Backgrounds** from any enabled resource pack: `assets/<ns>/cakecosmetic/backgrounds/<id>.png` + optional `<id>.json` (title, artist, translations). Shrunk to the window size on load; at most 2 full textures; thumbnails made once.
- **Unlocks:** advancement mode (default) / all mode, switched only in `config/cakecosmetic/config.json5` (no UI). Unlock rule = list of advancement ids, all required. Saved the moment the game sees them, per install (no UUIDs), permanent, only on Monumenta servers. Toast per unlock; one summary toast for advancements earned before install.
- **Files:** `config.json5` (`mode`, `devMode`), `rules.json5` (pack-owned: rules with each advancement's title, optional `secret`), `unlocked.json` + `choices.json` (per player, never shipped).
- **Dev mode** (`devMode: true`): ids on tiles, rule editor (searchable list of the advancements the game knows now, Save writes `rules.json5` at once), preview as a new player, forget unlocks.
- **Gallery** v1 is a plain Java screen. Later: a CakeUI custom-screen part, then the gallery as a layout.
- **Screens:** every CakeUI loading-screen kind; not Mojang's launch overlay. Gallery button top-right on the main menu (20×20).
- **Never** sends chat or commands; downloads nothing.

## Files

```
cakecosmetic/
├── build.gradle        # Loom 1.19.3 (Gradle 9.8.2, JDK 17 toolchain); CakeUI from ../cakeui/build/libs/cakeui-<cakeui_version>.jar if built, else its GitHub release (Ivy);
│                       #   runClient passes -Dcakecosmetic.selftest, 1280x720
├── gradle.properties   # MC 1.20.4, Yarn build.3, Loader 0.16.9, Fabric API 0.97.3, cakeui_version=0.2.0, mod_version
├── GLOSSARY.md
├── .github/workflows/build.yml   # CI: build + test; release v<mod_version> once per version
└── src/main/java/io/github/harrythecake/cakecosmetic/
    ├── CakeCosmetic.java        # client entrypoint: Unlocks.load, Backgrounds reload listener, gallery key (unbound), main-menu GalleryButton, DISCONNECT
    ├── CakeCosmeticPlugin.java  # "cakeui" entrypoint: fillPort main_menu/background + loading_screen/background; values cakecosmetic.menu/.loading
    ├── CakeCosmeticHooks.java   # public door for mixins
    ├── Backgrounds.java         # reload listener: assets/<ns>/cakecosmetic/backgrounds/<name>.png (+ .json) → id <ns>:<name>; clears textures, MenuBackground.prepare
    ├── BackgroundMeta.java      # <name>.json: title (string or per language), artist
    ├── BackgroundTextures.java  # one decoder thread (MIN_PRIORITY); full = shrunk to cover the framebuffer, LRU of 2 (MAX_FULL), reloaded if the window grows
    │                            #   25%; thumbs 256x144 cover-cropped, kept until reload; drawCover (centred crop by UV, alpha, linear filter)
    ├── Fit.java                 # shrinkToCover, coverRegion (pure)
    ├── MenuBackground.java      # main menu filler: one pick per TitleScreen instance (WeakReference), next pick loaded ahead; soft dark strip at the bottom
    ├── LoadingBackground.java   # loading filler: shuffled slideshow, 5 s + 0.5 s fade; new order after 1.5 s without loading screens; vanilla tile = vanilla
    ├── Picker.java              # pool (ONE / SHUFFLE / fallback all pickable), pickOne (no repeat), order (pure)
    ├── Settings.java            # config.json5: mode advancement|all, devMode (CakeUI's Json5 reader); writes defaults when missing
    ├── Rules.java               # rules.json5: bg id → advancements [{id,title}] + secret; newlyUnlocked; written by dev mode (header comment + JSON)
    ├── UnlockState.java         # unlocked.json: backgrounds + earned rule advancements (per install)
    ├── Choices.java             # choices.json: mainMenu / loading: mode ONE|SHUFFLE, one, shuffle set; VANILLA = "vanilla"
    ├── JsonFiles.java           # Gson read with fallback, atomic write
    ├── Unlocks.java             # state holder: pickable, countsHere (playmonumenta.com; any server in dev mode), onAdvancements → check → toast,
    │                            #   forget, saveRule, knownAdvancements (live progress map + the manager's tree), new-player preview
    ├── UnlockToast.java         # "New background: <title>" / "N new backgrounds" + gallery hint; toast sprite drawn in 3 pieces (width 160–260)
    ├── GalleryScreen.java       # place tabs, mode button, thumbnail grid (≥ 2 rows visible), locked/selected/ticked/focused tiles, tooltips, right-click
    │                            #   preview; dev row: Edit unlock rule (focused tile), New player view, Forget my unlocks (confirm)
    ├── PreviewScreen.java       # one background full screen, title + artist
    ├── RuleEditorScreen.java    # dev: search, list (rule's own first, then known; untitled hidden unless toggled), click toggles, Secret, Clear, Save
    ├── GalleryButton.java       # 20x20 button with the icon; label key cakecosmetic.gallery.title = its CakeUI button id
    ├── ModMenuEntry.java        # Mod Menu's button opens the gallery
    ├── SelfTest.java            # -Dcakecosmetic.selftest=1|world (see Build)
    └── mixin/ClientAdvancementManagerMixin.java   # onAdvancements TAIL → CakeCosmeticHooks.advancements(advancementProgresses)
```

Tests (`./gradlew build`, 9): LogicTest (settings, rules round trip + newlyUnlocked, bad files, state files, pools, picks, image sizes, meta), LangTest (en_us, zh_cn, zh_tw have the same keys).

Build: `JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build` in `cakecosmetic/` (Gradle 9.8.2 + Loom 1.19.3 on JDK 25, like the multiloader kit; compiling, tests and `runClient` use the JDK 17 toolchain, since 1.20.4's Mixin can't run on 25). Loom must be at least the kit's: CakeUI's release jars are built with Loom 1.19.3, and an older Loom refuses them ("Mod was built with a newer version of Loom"; the first mirror CI run failed that way, 2026-11-09). A `../cakeui/build/libs/cakeui-<cakeui_version>.jar`, if present, wins over the release, so a stale local CakeUI jar can hide CI problems. CI in the standalone repo uses the CakeUI release named by `cakeui_version` (0.2.0, API 2 is enough; it runs with CakeUI 0.3.0 too). Not ported to the multiloader layout yet (Fabric 1.20.4 only).

Dev client self-test: `./gradlew runClient -Dcakecosmetic.selftest=world` (about a minute with an existing world; a fresh world can take much longer). Needs in `run/` (ignored): `resourcepacks/cakecosmetic-paperbox-test.zip` (Paperbox 1.1.0's 7 images under `assets/paperbox/cakecosmetic/backgrounds/` with placeholder titles "Paperbox N"), `options.txt` with `onboardAccessibility:false`, `pauseOnLostFocus:false` and that pack in `resourcePacks`, `config/cakecosmetic/config.json5` with `devMode: true`, and `config/cakecosmetic/rules.json5` (image3 ← minecraft:story/root, image5 ← story/mine_stone, image7 ← end/root secret). It shoots title, gallery, preview, rule editor, loading screens, the unlock toast (a crafting table earns story/root) and the gallery/editor in the world, then quits.

## Status

- **M0** (2026-11-04): folder, Gradle, CI, glossary, AGENTS.md, README, icon (generated), LangTest.
- **M1** (2026-11-04, in `cakeui/`): CakeUI 0.2.0 `CakeUIApi.fillPort` (see cakeui/AGENTS.md).
- **M2–M5** (2026-11-04, built in one go at the user's request): backgrounds from resource packs, both fillers, gallery (choices, Vanilla tile, button, key, Mod Menu), advancement mode (unlocks on disk, toasts), dev mode (rule editor, new-player view, forget unlocks). Self-tested in the dev client: Paperbox on the main menu (a new pick on the next menu) and the world loading screen, gallery grid at GUI scale 3 with locked tiles, preview, unlock of image3 by minecraft:story/root with its toast, rule editor listing the world's advancements with "earned". **User's verdict 2026-11-04: "good for now"** after trying it in the test instance (set up 2026-11-04: cakecosmetic-0.1.0 + cakeui-0.2.0 jars, cakeui-0.1.0 and Loading Backgrounds `.disabled`, `cakecosmetic-paperbox-test.zip` enabled after Paperbox 1.1.0, `devMode: true`, no rules file; options.txt backup in that session's scratchpad).
- **Next M6**: the real Paperbox version (user confirms titles/artists), pack changes (remove Loading Backgrounds, `rules.json5` in `PACK_OWNED`, `unlocked.json` + `choices.json` in `YOSBR_EXCLUDE`, `config.json5` not shipped), guide-book page, release with CakeUI 0.2.0.
