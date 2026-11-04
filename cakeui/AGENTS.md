# CakeUI: agent reference

Client-only library mod (`cakeui`, package `io.github.harrythecake.cakeui`) for **Fabric and NeoForge on Minecraft 1.20.4, 1.21.1 and 26.1.2** (Cake multiloader layout, see `../tools/multiloader/README.md`; the Cakedition pack ships the Fabric 1.20.4 jar). Layout folders, written by pack developers with no Java, redraw the game window, launching screen, main menu, loading screens and HUD. Terms are in [`GLOSSARY.md`](GLOSSARY.md): layout, part, window, subwindow, element, port, declaration, game value, design pixel, skin, dev mode. The user-facing guide is [`README.md`](README.md); keep both files current.

The standalone repo is `HarrytheCake/CakeUI` (remote `cakeui`, a `git subtree` mirror pushed by `tools/push_all`; MIT; public since 2026-11-04). The source of truth is this folder in the Monumenta-Cakedition repo; never commit in the standalone repo. **Not in the pack yet:** the next pack release ships the newest CakeUI (0.2.0, no layout) together with CakeCosmetic (user, 2026-11-04).

## Decisions (grilled with the user 2026-10-30, Q1–Q35)

- **File-driven first** (ADR 0005, local): layouts in JSON5 plus a small expression language. The Java API (game values, element types, button actions) arrives in M5; a Java screen builder comes later.
- **Untouched unless mentioned** (ADR 0006, local): with no layout CakeUI does nothing and costs nothing. A port, button or HUD piece the layout doesn't name stays as vanilla and other mods draw it. Every hook checks this first.
- **Plugins may fill a port** (ADR 0008, local; 0.2.0, API version 2): `CakeUIApi.fillPort(port, owner, PortFiller)` for `main_menu/background` (panorama + overlay) and `loading_screen/background`. The filler draws when the active layout doesn't mention that port, also with no layout; a layout that mentions it wins; the filler returns false to leave vanilla. One filler per port (a second throws); a filler that throws is logged once and vanilla draws. First user: CakeCosmetic.
- **Plugins may add HUD pieces** (0.2.0, API version 2, for CakeMap; 2026-11-04): `CakeUIApi.addHudPiece(name, owner, HudPiece)`. `PluginHud.drawAll` runs at InGameHud.render TAIL before the late elements and the front layer (skipped with F1), each piece through `HudPart.piece`, so a layout that names it as a `hud` port can hide/move/scale/fade it and add elements; with no layout it is drawn as is. The Loader accepts added piece names as `hud` ports; the schema lists `minimap`. One mod per name, never a vanilla port's name; a piece that throws is logged once and switched off.
- **Parts:** window ⊃ game launching screen, main menu, loading screen, HUD ⊃ subwindows ⊃ elements. The pause menu and inventory will be built-in HUD subwindows (`hud/subwindow_pause`, `hud/subwindow_inventory`). Inventory slots never move. Chests and other screens come later.
- **Variables:** a folder sees its own and its parents' declarations, never a sibling's; a child's name shadows the parent's. **A folder writes only its own state variables** (a button's `set`/`toggle` must target the button's own folder).
- **Safety:** expressions have no loops and no file or network access. Button actions are client-side only: **never chat or commands**. Like every Cake mod, it downloads nothing at runtime.
- **Units:** `r` = design pixels (`design: [w, h]`, fit/fill/stretch), `px` = GUI pixels, `%` = parent. A plain number in a length is `r`; in `+`/`-` next to a length it is also `r`.
- **Dev mode** (`devMode` in config, off by default): a file watcher reloads, and the error overlay lists problems with file:line. **A reload with problems keeps the previous layout**; only the first load uses a layout with problems (broken elements skipped).
- **Performance:** zero work without a layout. Expressions are compiled once, and constants folded. Target under 0.5 ms/frame for a typical HUD on the low tier, to be checked with CakeBench in M4.
- **Pack:** stays out of the pack until a Cakedition layout exists (separate grilling). It will then ship on by default, with `layoutPath` set through yosbr.

## Files

```
cakeui/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/   # the Cake multiloader kit (apply_kit.py copies it; don't edit here)
├── gradle.properties       # mod_id, mod_name, mod_version, mod_group (+ Gradle memory settings)
├── GLOSSARY.md, README.md (the public layout guide), schema/*.schema.json, examples/window_example
├── .github/workflows/build.yml   # mirror CI (not yet updated for the multiloader build)
├── core/                   # plain Java, no Minecraft imports; compiled into every jar
│   ├── src/main/java/…/cakeui/
│   │   ├── CakeUI.java          # MOD_ID, LOGGER
│   │   ├── Config.java          # config/cakeui/config.json5 (dirs through platform/Services)
│   │   ├── HudTiming.java       # self-test HUD bench
│   │   ├── platform/            # Platform (loader, configDir, gameDir, isModLoaded) + Services (ServiceLoader)
│   │   ├── json/ expr/ model/   # Json5, expression language, Loader/Layout/Node/…; model/PluginNames = names of plugin actions
│   │   │                        #   and HUD pieces, so the Loader checks layouts without Minecraft classes
│   │   └── render/NodeState.java, render/Cjk.java (hasCjk)
│   ├── src/main/resources/assets/cakeui/   # icon, lang (en_us, zh_cn, zh_tw)
│   └── src/test/java/          # Json5Test, ExprTest, LoaderTest, GeometryTest, SchemaTest, render/CjkTest (26 tests)
└── <mc>/ (1.20.4, 1.21.1, 26.1.2)
    ├── version.properties   # from the kit
    ├── common/              # Minecraft-facing code for both loaders (Mojang names)
    │   ├── src/main/java/…/cakeui/
    │   │   ├── CakeUIClient.java  # the loader-independent client side: keys, start/runPlugin/afterPlugins, and the hooks the loaders
    │   │   │                      #   call (endClientTick, afterHud, afterScreenInit, before/afterScreenRender, key/click/release)
    │   │   ├── Ui, McValues, ScreenPorts, WindowPart, PluginPorts, PluginHud, ConfigScreen, HudPart, LaunchPart, LoadingPart,
    │   │   │   TitleMenu, Overlay, SelfTest   # as before (see "How drawing works")
    │   │   ├── api/               # the public API: same package and class names in every version (see "Plugins")
    │   │   ├── render/            # Renderer, Paint, Textures, Fonts, Actions (+ Layers on 1.20.4/1.21.1: offscreen 3D buffers)
    │   │   └── mixin/             # screen/HUD/overlay mixins and accessors (cakeui.mixins.json); HudPart.pre/post are the
    │   │                          #   layer-style hooks (NeoForge layer events, Fabric 26.1 HudElementRegistry, some 1.21.1 mixins)
    │   └── src/test/java/         # ApiTest, ExampleLayoutTest, PluginHudTest, PluginPortsTest (11 tests; need Minecraft's classes)
    ├── fabric/              # CakeUIFabric (client entrypoint: "cakeui" entrypoints, Fabric API events), FabricPlatform,
    │                        #   ModMenuIntegration, fabric/mixin/InGameHudMixin (+ BossHealthOverlayMixin on 1.21.1) in
    │                        #   cakeui-fabric.mixins.json, FabricHud on 26.1.2 (HudElementRegistry), fabric.mod.json
    └── neoforge/            # CakeUINeoForge (@Mod: keys, client setup, events, config screen), NeoForgePlatform,
                             #   NeoForgePlugins (ServiceLoader), NeoForgeHud (overlay/layer events), META-INF/(neoforge.)mods.toml
```

Tests (`./gradlew build`, 37 in all, same as before the port): core 26, each `<mc>/common` 11 (they run once per Minecraft version).

## Build, run, test

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # all 6 jars → build/libs/
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=26.1.2   # one version (+ core)
```

`build/libs/cakeui-<fabric|neoforge>-<mc>-0.2.0.jar`, plus `cakeui-0.2.0.jar` = the Fabric 1.20.4 jar (what the pack uses).

**Self-test** (dev client): `-Dcakeui.selftest=1` = title screen shots (mouse in three places), the settings screen, a resource reload (launch overlay shots), quit; `=world` also opens `run/saves/cakeui-selftest` (created as a demo world if missing), shoots each loading screen kind, the pause menu, the inventory and the HUD, times the HUD with and without the layout, and logs `CakeUI self-test: done, N problem(s)`. `-Dcakeui.selftest.fill=true` adds test fillers (purple main menu, teal loading screens). Needs `run/config/cakeui/config.json5` with a `layoutPath` (e.g. the example) and `devMode`. With the kit's smoke script:

```
python tools/multiloader/smoke.py cakeui <mc> <fabric|neoforge> "--jvm=-Dcakeui.selftest=world" "--done=self-test: done" "--args=--width 1280 --height 720"
```

Screenshots land in `<mc>/<loader>/run/screenshots/cakeui-selftest-*.png`.

## Plugins (API, for CakeMap, CakeCosmetic and other mods)

A plugin is a class implementing `io.github.harrythecake.cakeui.api.CakeUIPlugin` with a public no-argument constructor. CakeUI runs every plugin once, before the first layout loads.

- **Fabric:** `"entrypoints": { "cakeui": ["com.example.MyCakeUIPlugin"] }` in `fabric.mod.json` (unchanged since 0.1.0). Run in CakeUI's client initializer.
- **NeoForge:** a service file in the plugin's jar, `META-INF/services/io.github.harrythecake.cakeui.api.CakeUIPlugin`, containing the class name (one line). CakeUI reads it with `ServiceLoader.load(CakeUIPlugin.class, CakeUIPlugin.class.getClassLoader())` in `FMLClientSetupEvent.enqueueWork` (NeoForgePlugins); a broken entry is logged and skipped. A plugin mod that only *optionally* uses CakeUI can ship the file anyway: without CakeUI nobody reads it. Mark CakeUI `type = "optional"`, `ordering = "NONE"` in its `neoforge.mods.toml` / `mods.toml` if it wants it listed.
- The API's package and class names (`CakeUIApi`, `CakeUIPlugin`, `ElementType`, `ElementContext`, `ButtonAction`, `PortFiller`, `HudPiece`, `PropKind`) are the same everywhere; Minecraft's types in their signatures follow the version: `GuiGraphics` (1.20.4, 1.21.1) or `GuiGraphicsExtractor` (26.1.2), `Screen`. So a plugin compiles per version against `build/libs/cakeui-<loader>-<mc>-0.2.0.jar` (compileOnly; the API classes are in the common part, identical on both loaders of one version). `CakeUIApi.VERSION` stays 2. The JSON5 reader (`io.github.harrythecake.cakeui.json`) is in every jar too.
- On 26.1.2 a plugin draws into the frame's render state like vanilla (`GuiGraphicsExtractor.pose()` is a 3x2 matrix; no shader colour: apply `ElementContext.opacity()` to your colours).

## How drawing works

This describes 1.20.4 (the shipped target); 1.21.1 works the same way, 26.1.2 differs where the "Multiloader status" section says. Fabric API events are the loader events of CakeUIClient's hooks (NeoForge: ScreenEvent.*, RenderGuiEvent, ClientTickEvent).

- **One renderer per loaded layout** (`Renderer`). `beginFrame` sets the frame number, design scale (`rx`, `ry`), mouse and time. `drawPart`, `drawElements`, `drawSingle` and `drawSkin` walk nodes.
- **Per node:** visible → size (defaults: text = measured, image = texture size in r, background = parent's full size, item 16r, button 200×40r, bar 100×8r, containers = parent) → place (anchor/origin, or the row/column cursor) → transform (+ parallax) → hover by inverting the transform (2D) or by `Geometry.unproject` (3D) → content → children.
- **3D** (rotateX/rotateY/z ≠ 0): the content is drawn flat into a window-sized offscreen buffer (`Layers.push`, box at a margin of 10% of the screen), then the whole buffer is drawn as one quad through `Geometry.projection` (a real perspective matrix, so text, items and nested 3D tilt correctly). Buffer colours are premultiplied, so composite uses ONE, ONE_MINUS_SRC_ALPHA. Vanilla's `Framebuffer.clear()` unbinds, which is why `push` clears first and then binds. Nested 3D works with one buffer per depth.
- **Why buffers:** vanilla's `VertexConsumer.vertex(Matrix4f, …)` drops `w`, so a perspective matrix on the MatrixStack can't work.
- **States:** `Prop.pick(hovered, pressed, disabled)`. Number, length and colour props with states ease through `NodeState.Anim` when `transition.duration > 0`.
- **Clicks:** the topmost hovered enabled `button` of the frame takes the click (`ScreenMouseEvents.allowMouseClick` returns false). The action runs on press.
- **Loading screens:** six vanilla screens (`LoadingPart.kind`). Their background is one hook on `Screen.render`'s `renderBackground` call; per-frame start and the front layer use Fabric's `ScreenEvents.before/afterRender` (ReconfiguringScreen has no render method to mix into).
- **Launch overlay:** `SplashOverlay` draws no screen of its own: no clicks, no cursor. First start: vanilla clears the window (a hidden background clears to black); reloads: a fading fill, whose alpha the background elements use. Minecraft's font only exists after the first load: layout fonts work, vanilla-font text doesn't.
- **Window:** applied on the first client tick (the window exists) and after every reload; game values in title/border are re-read twice a second (`env.frame` is bumped, since nothing is drawn there).
- **HUD:** `HudPart.beginFrame` (InGameHud.render HEAD) reads each mentioned port once into a `Port`; hooks only read it. Back layer at render's first `getCurrentGameMode()` (after the full-screen overlays), front + action bar/title port elements at TAIL; status-bar port elements at renderStatusBars TAIL. Nothing with F1. `Renderer.beginFrame` keeps the time step when the HUD and a screen both start the same frame (< 2 ms apart).
- **Built-in subwindows** (`Loader.BUILTIN_SUBWINDOWS`: `hud/subwindow_pause`, `hud/subwindow_inventory`): loaded as type `part` into `part.builtins`, never `children`, so the HUD won't draw them. Ports per screen in `Loader.PORTS` (`hud/pause`, `hud/inventory`). Drawn like a part (`drawPart`, back/front) while their screen is open. Pause: only `showMenu` (F3+Esc stays vanilla). Inventory: back layer after the dim, panel port elements right after the panel texture via `drawElementsIn` (panel box, follows the recipe book), title port elements + front layer just before the item tooltip.
- **Skins:** `ScreenPorts.applyButtons` (every frame) moves, resizes, hides and relabels buttons and registers skins. `ClickableWidgetMixin` draws the skin instead of `renderWidget`, so the vanilla button still handles input. Inside a skin, every element's hover = the button's hover.
- **Small CJK text** (`readable`, default true): in Minecraft's bitmap font, text containing CJK (`Renderer.hasCjk`) is enlarged to at least 2 screen px per font unit (vanilla's GUI scale 2): its 16×16 glyphs sit in 8 units, so below that nearest sampling drops whole rows ("squeezed" Chinese, reported by the user 2026-11-04 at 852×475). Linear filtering was tried and rejected: soft but still unreadable at ~11 px. Latin text and layout TTFs keep the design size. `-Dcakeui.size=852x475` runs the dev client at that size.
- **Fonts:** a layout TTF is built per sharpness step (oversample 1/2/4/8/16 by on-screen scale). Minecraft's fonts are borrowed as fallbacks (wrapped so closing ours never closes theirs), and everything is rebuilt when Minecraft reloads its fonts. Tested in game with Bahnschrift + Chinese fallback (dev run, 2026-10-30).

## Invariants

- No layout = no work: every mixin returns to vanilla when `TitleMenu.port(…)`/`part()` is null.
- Problems never crash the game: load problems skip the broken element; runtime expression errors give the default and are listed once.
- Never send chat or commands; never download anything.
- Test data uses made-up names; no real player names anywhere.

## Status

- **M0** (repo folder, CI, glossary) and **M1** (config, loading, declarations, expressions, game values, hot reload, error overlay, schema) are done.
- **M3** (in progress, 2026-11-04): **pause menu** and **inventory** done; self-tested in the dev client (`-Dcakeui.selftest=world`) and **passed the user's in-game test 2026-11-04** (F3+Esc stays vanilla too). Small CJK text fixed after the user's report (see `readable`). **Window, loading screen and game launching screen done** and self-tested in the dev client (2026-11-04: title bar dark + colours + icon + title read back from the real window; loading `world`/`terrain`/`progress`; launch at start and on a reload; 0 problems). **Passed the user's in-game test 2026-11-04: M3 is done.** The example's window icon is now the pack icon (user's wish). Also fixed: the main menu `version` port's `color` was accepted but ignored. - **M2** (rendering on the main menu) is **done and self-tested in a dev client 2026-10-30**: screenshots show the example layout, hover with transitions through a 3D tilt, skins, parallax, TTF and zh_cn. **Passed the user's in-game test 2026-11-04** in the test instance: the example layout, hot reload via the watcher, the reload key, the URL/connect actions and Mod Menu's button id.
- **M4** (HUD, 2026-11-04): all ports done; self-tested in the dev client (info box, XP bar replaced, action bar and title moved/scaled; 0 problems). HUD bench in the self-test: **CakeUI adds 0.07–0.09 ms/frame** CPU with the example HUD (InGameHud.render 0.56 → 0.62 ms vs 0.49 → 0.52 without; this laptop, dev client). **Passed the user's in-game test 2026-11-04** (Monumenta). Still to do: the low-tier CakeBench run: `python tools/bench/bench.py batch tools/bench/sets/cakeui-hud.json` (baseline ×3, CakeUI without layout, CakeUI with the example HUD; laptop on AC and idle).
- **M5** (2026-11-04): API v1 (values, element types, actions; plugins via the `cakeui` entrypoint), Mod Menu settings screen, README "For mod developers", glossary "Plugin". Self-tested: ApiTest, settings screen screenshot (layout, dev mode, ids, reload, open folder, status line). **Passed the user's in-game test 2026-11-04.** All five milestones are done.
- **0.2.0, plugins fill ports** (2026-11-04, CakeCosmetic's M1): `fillPort` + PluginPortsTest; dev-client self-test with test fillers: no layout → fillers on the main menu (overlay skipped) and the world/terrain loading screens; with the example layout (mentions both backgrounds) → the layout wins; 0 problems. In the user's test instance since 2026-11-04 with CakeCosmetic (user: "good for now").
- **0.2.0, plugin HUD pieces** (2026-11-04, for CakeMap): `addHudPiece` + PluginHudTest (names checked and unique; a layout naming an added piece loads with 0 problems, and without the piece it is an unknown port). Self-tested through CakeMap's dev-client run (minimap + in-world waypoints drawn as pieces) and in the test instance with the full pack.
- **Then:** CakeUI ships in the next pack release with **no layout** (vanilla) by default, together with CakeCosmetic (user, 2026-11-04); the low-tier CakeBench run is dropped (user). A Cakedition layout (its look is a separate grilling, ADR/Q32) before CakeUI goes into the pack; chests and other screens later (Q20).

## Multiloader status (2026-11-05)

Ported 2026-11-04/05 from the Yarn 1.20.4 Fabric mod (migrateMappings + yarn2moj, files moved with `git mv`). 1.20.4 Fabric behaves as before: same mod id, config file, keys, lang keys, Fabric API events, `cakeui` entrypoint, mixin targets (Mojang names now); `Screens.getButtons` became the `ScreenAccessor` (`renderables`, same order).

Every target: `./gradlew build` OK (core 26 + common 11 tests), smoke PASS on the title screen with `-Dcakeui.selftest=world` (example layout, dev mode: title shots, settings screen, resource reload, loading screens, pause, inventory, HUD, HUD bench, `done, 0 problem(s)`) and PASS with `--world` (quick play into the smoke world). Screenshots checked by eye on all six: the example main menu with its tilted cards, pause menu skins, inventory panel/title/level, HUD (XP bar replaced, info box, action bar and title moved/scaled).

| Target | Works | Differs / gaps (why) |
|---|---|---|
| 1.20.4 Fabric | everything, as before | none. HUD bench 0.19 ms/frame added in the dev client (example HUD). |
| 1.20.4 NeoForge | everything; HUD ports through `RenderGuiOverlayEvent.Pre/Post` per vanilla overlay (NeoForgeHud), plugins via ServiceLoader, settings via the mod list's Config | `game_launch` only on resource reloads: the first start shows NeoForge's own loading screen (NeoForgeLoadingOverlay replaces vanilla's during mod loading). The main menu `version` port does nothing: NeoForge draws the branding lines in lambdas (injector `require = 0`). Self-test fill run (no layout) checked: teal loading filler. |
| 1.21.1 Fabric | everything | HUD is layered since 1.20.5: hooks moved into the layer methods (renderHotbarAndDecorations, renderPlayerHealth's renderHearts/renderArmor/renderFood + air sprites, renderExperienceLevel, renderEffects, BossHealthOverlay, …); the `experience` port also covers the level number (its own layer now). Title screen: panorama + overlay are one call (`renderPanorama`), so the background port wraps that. Container dim = `renderTransparentBackground`. TTF layout fonts load through FreeType (1.21's TrueTypeGlyphProvider), OTF accepted. |
| 1.21.1 NeoForge | everything; HUD via `RenderGuiLayerEvent` (`VanillaGuiLayers`), vignette/spyglass/pumpkin via the common GuiCameraOverlaysMixin (one camera-overlays layer) | same `game_launch` and `version` gaps as 1.20.4 NeoForge (first-start not observed in this run either). |
| 26.1.2 Fabric | everything; HUD via Fabric's `HudElementRegistry.replaceElement` per vanilla element (FabricHud) + Gui.extractRenderState HEAD/TAIL; rects/images are CakeUI's own `GuiElementRenderState` (Paint.Quads) submitted into the frame's `GuiRenderState`; layout fonts = own FontSet + GlyphStitcher | **3D tilt is an affine approximation**: the GUI records 2D render states (3x2 pose) drawn at the end of the frame, so offscreen layers + a perspective composite (Layers on 1.20.4/1.21.1) aren't possible; a tilted element gets the affine map closest to its projection around its centre (turns and shrinks, no foreshortening), and hit tests use the same map. **No shader colour**: a port's `opacity` on a vanilla piece is ignored except where CakeUI passes the colour (loading text, inventory title/panel, action bar/title text, launch logo); the vanilla background of a screen is drawn or not. `item` elements are skipped until items have their data (`Holder.areComponentsBound`, false on the first main menu). CakeUI quads ignore an enclosing scissor. The `experience` port covers the contextual info bar (XP, locator or jump bar). Key category is a registered id (lang `key.category.cakeui.main`). |
| 26.1.2 NeoForge | as 26.1.2 Fabric, HUD via `RenderGuiLayerEvent` | `game_launch` does show at the first start here (vanilla LoadingOverlay, observed). `version` port does nothing (NeoForge branding). Plugins and the first layout load in client setup (a sync worker on 26.1, not the render thread; nothing GL-related happens there). Self-test fill run (no layout) checked: purple main menu filler. |

Not tested on any target: a real NeoForge plugin jar (ServiceLoader discovery is only exercised by CakeMap/CakeCosmetic once they are ported), CakeUI's layout TTF fonts on 1.21.1/26.1.2 (the example uses Minecraft's fonts), the window icon/title bar on NeoForge (same code as Fabric).

