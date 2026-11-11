# CakeCosmetic: agent reference

Client-only mod (`cakecosmetic`, package `io.github.harrythecake.cakecosmetic`) for **Fabric 1.20.4** (the Cakedition pack's) and **Fabric + NeoForge 26.1.2** (Cake multiloader layout, see `../tools/multiloader/README.md`; no 1.21.1 and no NeoForge 1.20.4: user's choice, 2026-11-11): a gallery of backgrounds for the main menu and loading screens, unlocked by Monumenta advancements. A **CakeUI plugin** (hard dependency; Fabric: `cakeui` entrypoint, NeoForge: `META-INF/services/io.github.harrythecake.cakeui.api.CakeUIPlugin`). Terms are in [`GLOSSARY.md`](GLOSSARY.md): background, gallery, unlock rule, advancement mode, all mode. Player-facing page: [`README.md`](README.md); keep both current.

Standalone repo `HarrytheCake/CakeCosmetic` (remote `cakecosmetic`, MIT), public since 2026-11-09 (user); release v0.1.0 is one Fabric 1.20.4 jar from before the multiloader port. Source of truth: this folder in the Monumenta-Cakedition repo (never commit in the standalone repo).

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
├── settings.gradle, build.gradle, buildSrc/, gradlew*, gradle/wrapper/   # the Cake multiloader kit (apply_kit.py copies them; don't edit here)
├── gradle.properties   # mod_id/name/version; cakeui_version=0.2.0 (compile: the oldest release with API 2, keeps "cakeui >= 0.2.0" honest);
│                       #   cakeui_run_version=0.3.0 (dev runs: what the pack ships); -Pcakeui_dir=<dir> = a local CakeUI build instead
├── gradle/cakecosmetic.gradle   # mod-specific lines every part applies: CakeUI's GitHub release jars as an Ivy repo (module cakeui-<loader>-<mc>);
│                                #   core + common compile against the NeoForge jar (Mojang names); fabric/neoforge: compileOnly + runtime (run version)
├── .github/workflows/build.yml  # mirror CI: JDK 17 + 25, all jars; release v<mod_version> once per version
├── core/               # plain Java (no Minecraft), compiled into every jar with that version's Java
│   ├── src/main/java/io/github/harrythecake/cakecosmetic/
│   │   ├── CakeCosmeticCore.java  # MOD_ID, LOG
│   │   ├── platform/Platform.java, Services.java   # loader service: loader(), configDir()
│   │   ├── BackgroundMeta.java    # <name>.json: title (string or per language), artist (CakeUI's Json5 reader)
│   │   ├── Fit.java               # shrinkToCover, coverRegion
│   │   ├── Picker.java            # pool (ONE / SHUFFLE / fallback all pickable), pickOne (no repeat), order
│   │   ├── Settings.java          # config.json5: mode advancement|all, devMode; writes defaults when missing
│   │   ├── Rules.java             # rules.json5: bg id → advancements [{id,title}] + secret; newlyUnlocked; written by dev mode
│   │   ├── UnlockState.java       # unlocked.json: backgrounds + earned rule advancements (per install)
│   │   ├── Choices.java           # choices.json: mainMenu / loading: mode ONE|SHUFFLE, one, shuffle set; VANILLA = "vanilla"
│   │   └── JsonFiles.java         # Gson read with fallback, atomic write
│   ├── src/main/resources/assets/cakecosmetic/   # icon.png, lang (en_us, zh_cn, zh_tw)
│   └── src/test/java/…    # LogicTest, LangTest (9 tests; core compiles against CakeUI's NeoForge 1.20.4 jar for the JSON5 reader)
├── 1.20.4/
│   ├── common/src/main/java/io/github/harrythecake/cakecosmetic/
│   │   ├── CakeCosmetic.java        # init (Unlocks.load, SelfTest), gallery key; hooks the loaders call: endClientTick, afterScreenInit
│   │   │                            #   (main-menu GalleryButton), afterScreenRender (self-test only), onDisconnect
│   │   ├── CakeCosmeticPlugin.java  # CakeUI plugin: fillPort main_menu/background + loading_screen/background; values cakecosmetic.menu/.loading
│   │   ├── CakeCosmeticHooks.java   # public door for mixins
│   │   ├── Backgrounds.java         # vanilla ResourceManagerReloadListener (ID cakecosmetic:backgrounds): assets/<ns>/cakecosmetic/backgrounds/<name>.png (+ .json)
│   │   ├── BackgroundTextures.java  # one decoder thread (MIN_PRIORITY); full = shrunk to cover the framebuffer, LRU of 2; thumbs 256x144; drawCover
│   │   ├── MenuBackground.java      # main menu filler: one pick per TitleScreen instance, next pick loaded ahead; soft dark strip at the bottom
│   │   ├── LoadingBackground.java   # loading filler: shuffled slideshow, 5 s + 0.5 s fade; new order after 1.5 s without loading screens
│   │   ├── Unlocks.java             # state holder: pickable, countsHere (playmonumenta.com; any server in dev mode), onAdvancements → toast, …
│   │   ├── UnlockToast.java, GalleryScreen.java, PreviewScreen.java, RuleEditorScreen.java, GalleryButton.java
│   │   ├── SelfTest.java            # -Dcakecosmetic.selftest=1|world (see Build)
│   │   └── mixin/ClientAdvancementManagerMixin.java   # ClientAdvancements.update TAIL → CakeCosmeticHooks.advancements(progress)
│   └── fabric/  # CakeCosmeticFabric (Fabric API events), FabricPlatform, ModMenuEntry (Mod Menu opens the gallery), fabric.mod.json
└── 26.1.2/
    ├── common/    # the same classes ported to 26.1 (GuiGraphicsExtractor, extractRenderState, Identifier, Toast.update/extractRenderState,
    │              #   KeyMapping.Category, MouseButtonEvent); see "Multiloader status"
    ├── fabric/    # as 1.20.4 with Fabric API 26.1 names (KeyMappingHelper, ResourceLoader, Screens.getWidgets, ScreenEvents.afterExtract)
    └── neoforge/  # CakeCosmeticNeoForge (@Mod dist CLIENT: AddClientReloadListenersEvent, RegisterKeyMappingsEvent + category, ClientTickEvent.Post,
                   #   ScreenEvent.Init.Post / Render.Post, ClientPlayerNetworkEvent.LoggingOut, IConfigScreenFactory = gallery), NeoForgePlatform,
                   #   META-INF/neoforge.mods.toml, META-INF/services (Platform, CakeUIPlugin)
```

## Build, test, run

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                        # everything
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=1.20.4 # one version (+ core)
```

Jars in `build/libs/`: `cakecosmetic-fabric-1.20.4-<v>.jar`, `cakecosmetic-fabric-26.1.2-<v>.jar`, `cakecosmetic-neoforge-26.1.2-<v>.jar`, and `cakecosmetic-<v>.jar` = the Fabric 1.20.4 jar under the old name (the pack and `packwiz_sync.py github` use it). Tests: 9 in `core` (LogicTest: settings, rules round trip + newlyUnlocked, bad files, state files, pools, picks, image sizes, meta; LangTest: en_us, zh_cn, zh_tw have the same keys); no version-specific tests.

**Self-test** (dev client, Fabric 1.20.4 only: the user's rule, game runs only there), from the repo root: `python tools/multiloader/smoke.py cakecosmetic 1.20.4 fabric "--jvm=-Dcakecosmetic.selftest=world" "--done=CakeCosmetic self-test: done" --timeout=900` (about two minutes; it creates the demo world `cakecosmetic-selftest` if missing). Needs in `1.20.4/fabric/run/` (ignored): `resourcepacks/cakecosmetic-paperbox-test.zip` (Paperbox 1.1.0's 7 images under `assets/paperbox/cakecosmetic/backgrounds/` with placeholder titles "Paperbox N"), `options.txt` with `onboardAccessibility:false`, `pauseOnLostFocus:false` and that pack in `resourcePacks`, `config/cakecosmetic/config.json5` with `devMode: true`, and `config/cakecosmetic/rules.json5` (image3 ← minecraft:story/root, image5 ← story/mine_stone, image7 ← end/root secret); no `unlocked.json`, or there is no unlock toast. It shoots title, gallery, preview, rule editor, loading screens, the unlock toast (a crafting table earns story/root) and the gallery/editor in the world, then quits. The pre-port `run/` folder in the main tree (ignored) holds those files.

## Status

- **M0** (2026-11-04): folder, Gradle, CI, glossary, AGENTS.md, README, icon (generated), LangTest.
- **M1** (2026-11-04, in `cakeui/`): CakeUI 0.2.0 `CakeUIApi.fillPort` (see cakeui/AGENTS.md).
- **M2–M5** (2026-11-04, built in one go at the user's request): backgrounds from resource packs, both fillers, gallery (choices, Vanilla tile, button, key, Mod Menu), advancement mode (unlocks on disk, toasts), dev mode (rule editor, new-player view, forget unlocks). Self-tested in the dev client: Paperbox on the main menu (a new pick on the next menu) and the world loading screen, gallery grid at GUI scale 3 with locked tiles, preview, unlock of image3 by minecraft:story/root with its toast, rule editor listing the world's advancements with "earned". **User's verdict 2026-11-04: "good for now"** after trying it in the test instance (set up 2026-11-04: cakecosmetic-0.1.0 + cakeui-0.2.0 jars, cakeui-0.1.0 and Loading Backgrounds `.disabled`, `cakecosmetic-paperbox-test.zip` enabled after Paperbox 1.1.0, `devMode: true`, no rules file; options.txt backup in that session's scratchpad).
- **Multiloader port** (2026-11-11, agent, branch `cakecosmetic-port`): see "Multiloader status".
- **Next M6**: the real Paperbox version (user confirms titles/artists), pack changes (remove Loading Backgrounds, `rules.json5` in `PACK_OWNED`, `unlocked.json` + `choices.json` in `YOSBR_EXCLUDE`, `config.json5` not shipped), guide-book page, release with CakeUI 0.2.0.

## Multiloader status

Ported 2026-11-11 (pre-port backup: `docs/multiloader/backup-premultiloader/cakecosmetic-2026-11-11.zip`, local). Targets: Fabric 1.20.4, Fabric 26.1.2, NeoForge 26.1.2 (no 1.21.1, no NeoForge 1.20.4: user's choice; the kit builds only the part folders that exist). Compiles against CakeUI's **v0.2.0** release jars; dev runs use **v0.3.0**.

| Target | Builds | Tested | Differences / gaps |
|---|---|---|---|
| Fabric 1.20.4 | yes | 9 core tests; dev-client self-test (`=world`) **PASS** 2026-11-11 with CakeUI 0.3.0: Paperbox on the main menu (gallery button top right) and on the terrain loading screen, gallery with locked tiles, preview, rule editors, unlock of image3 by story/root with its toast; 0 error lines | Same behaviour as the pre-port mod. Metadata: `"minecraft": "1.20.4"` (was `~1.20.4`; the kit's exact-version rule). |
| Fabric 26.1.2 | yes | build + tests only (not run in game) | **No shader colour**: `drawCover` puts the fade alpha into the quad colour (`blit(..., color)`); the gallery button's icon takes the button's alpha. Linear filtering through the texture's sampler (`LinearTexture`, clamp-to-edge LINEAR) instead of `setFilter`. Key category is a registered id (`cakecosmetic:main`, lang `key.category.cakecosmetic.main`; 1.20.4 keeps `key.categories.cakecosmetic`). Toast: `update` sets the visibility, `extractRenderState` draws. Self-test: loading-screen shots come from `afterExtract`, so they show the frame before (no `flush` in 26.1); an existing self-test world opens with `WorldOpenFlows.openWorld`. |
| NeoForge 26.1.2 | yes | build + tests only (not run in game) | As Fabric 26.1.2. CakeUI finds the plugin through `META-INF/services`; the mod list's Config button opens the gallery (no Mod Menu); the gallery button is added with `ScreenEvent.Init.Post.addListener`; disconnect = `ClientPlayerNetworkEvent.LoggingOut`. |

Never run so far: either 26.1.2 target (ServiceLoader discovery of the plugin on NeoForge, the mixin on `ClientAdvancements.update`, the alpha CakeUI 26.1.2 passes to fillers, the linear sampler).
