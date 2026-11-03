# CakeUI: agent reference

Client-only Fabric library mod (`cakeui`, package `io.github.harrythecake.cakeui`) for Minecraft **1.20.4**. Layout folders, written by pack developers with no Java, redraw the game window, launching screen, main menu, loading screens and HUD. Terms are in [`GLOSSARY.md`](GLOSSARY.md): layout, part, window, subwindow, element, port, declaration, game value, design pixel, skin, dev mode. The user-facing guide is [`README.md`](README.md); keep both files current.

The standalone repo is `HarrytheCake/CakeUI` (remote `cakeui`, a `git subtree` mirror pushed by `tools/push_all`; MIT; public since 2026-11-04). The source of truth is this folder in the Monumenta-Cakedition repo; never commit in the standalone repo. **Not in the pack** (user, 2026-11-04) until a Cakedition layout exists.

## Decisions (grilled with the user 2026-10-30, Q1–Q35)

- **File-driven first** (ADR 0005, local): layouts in JSON5 plus a small expression language. The Java API (game values, element types, button actions) arrives in M5; a Java screen builder comes later.
- **Untouched unless mentioned** (ADR 0006, local): with no layout CakeUI does nothing and costs nothing. A port, button or HUD piece the layout doesn't name stays as vanilla and other mods draw it. Every hook checks this first.
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
├── build.gradle            # Loom 1.10.5; Java 17; Mod Menu compileOnly; runClient: -Dcakeui.selftest(=world), 1280×720
├── gradle.properties       # MC 1.20.4, Yarn build.3, Loader 0.16.9, Fabric API 0.97.3, mod_version
├── GLOSSARY.md             # CakeUI's terms (listed in the root's local GLOSSARY-MAP.md)
├── README.md               # the layout guide (public)
├── schema/*.schema.json    # JSON Schemas for layout/declaration/window files; SchemaTest keeps them in step with PropKinds
├── examples/window_example # the example layout: main menu, hud/subwindow_pause, hud/subwindow_inventory (generated art, except `window_icon_*.png` = the Monumenta Cakedition pack icon from `pack/icon.png`; ExampleLayoutTest loads it with 0 problems)
├── .github/workflows/build.yml   # CI: build + test; release v<mod_version> once per version
└── src/main/java/io/github/harrythecake/cakeui/
    ├── CakeUI.java          # entrypoint: values, keys (reload, button ids; unbound), Ui.reload, screen/HUD/tick events
    ├── Config.java          # config/cakeui/config.json5: layoutPath, devMode, showButtonIds (JSON5 reader)
    ├── Ui.java              # active layout + renderer; reload (keep previous on problems); dev-mode WatchService thread
    ├── McValues.java        # game values: time, mouse.*, screen.*, fps, lang, mc/pack version, server.*, world.*, player.*, menu.*
    ├── ScreenPorts.java     # one per redrawable vanilla screen (TitleMenu.PORTS, PAUSE, INVENTORY; `of(screen)`): beginFrame, port helpers (vanilla piece,
    │                        #   offset, text/colour, elements in a box), background + back layer, buttons port (move/resize/hide/relabel/skins), clicks, button ids
    ├── WindowPart.java      # window: title (getWindowTitle mixin, refreshed twice a second), icon (GLFW, Windows/X11; vanilla's back when unset),
    │                        #   title bar via dwmapi DwmSetWindowAttribute (LWJGL JNI.callPPI; dark 20, caption 35, text 36, border 34; reset what was set)
    ├── LaunchPart.java      # game_launch: Mojang's SplashOverlay (background/logo/bar ports, launch.* values)
    ├── LoadingPart.java     # loading_screen: which vanilla screens count (loading.kind), loading.progress/text
    ├── TitleMenu.java       # main menu only: splash pick/draw, version text, fade (buttons etc. through TitleMenu.PORTS)
    ├── Overlay.java         # dev mode: problems in the top-left of every screen and the HUD
    ├── SelfTest.java        # only with -Dcakeui.selftest: title screen shots; =world: opens run/saves/cakeui-selftest, gives a few items, shots of pause + inventory; quits
    ├── json/                # Json5 (comments, trailing commas, unquoted keys, lines kept), JValue, Json5Exception
    ├── expr/                # Parser (precedence climbing, names bound at compile time), Ops, Functions, Template ({…}, {t:key}, "="), Values, Px, Env
    ├── model/               # Loader (folders → Nodes, every problem with file:line + "did you mean"), Layout, Node, Prop (base + hover/pressed/disabled),
    │                        #   PropKinds (which keys, which kind, which types), Scope (Constant/State/Computed slots), GameValues, Geometry (anchors,
    │                        #   matrices, projection, unproject for clicks through a tilt), Easing, Problems
    ├── render/              # Renderer (tree walk, sizes, anchors, arrange, states/transitions, parallax, 3D, hit tests, clicks), NodeState,
    │                        #   Paint (float quads), Textures (assets PNGs → dynamic textures, freed on reload), Fonts (TTF via TrueTypeFont, fallback to
    │                        #   Minecraft's fonts), Layers (offscreen buffers + perspective composite), Actions
    └── mixin/               # TitleScreenMixin (WrapOperation on panorama, overlay, logo, splash, version line, super.render; HEAD/TAIL),
                             #   GameMenuScreenMixin (pause: background, back/front), HandledScreenMixin (container dim, only via ScreenPorts.of),
                             #   InventoryScreenMixin (panel texture, drawEntity, title drawText, front before drawMouseoverTooltip),
                             #   ScreenMixin (Screen.render's renderBackground call, loading screens only), LoadingScreensMixin (5 screens' text lines),
                             #   LevelLoadingScreenMixin (chunk map) + accessors, SplashOverlayMixin (clear/fill/logo/bar), MinecraftClientMixin (title),
                             #   ClickableWidgetMixin (skins: wraps renderWidget in render), accessors for fonts/splash, MouseInvoker (self-test)
```

Tests (`./gradlew build`, 26: also CjkTest, window settings): Json5Test, ExprTest (units, folding, templates, errors), LoaderTest (whole layout, problems with file:line, scoping, own-folder writes, cycles, fatal window, built-in pause subwindow), GeometryTest (anchors, design scale, the hit-test unproject against the projection, the GL projection matrix, easings), ExampleLayoutTest (with the real McValues list), SchemaTest (props, groups, every port in `Loader.PORTS`).

Build: `JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` in `cakeui/`. Dev client: `./gradlew runClient -Dcakeui.selftest=1` (or `=world` for pause + inventory, about a minute; needs `run/options.txt` with `onboardAccessibility:false` and `run/config/cakeui/config.json5` pointing at a layout; `run/` is ignored). It opens a window for about 30 s.

## How drawing works

- **One renderer per loaded layout** (`Renderer`). `beginFrame` sets the frame number, design scale (`rx`, `ry`), mouse and time. `drawPart`, `drawElements`, `drawSingle` and `drawSkin` walk nodes.
- **Per node:** visible → size (defaults: text = measured, image = texture size in r, background = parent's full size, item 16r, button 200×40r, bar 100×8r, containers = parent) → place (anchor/origin, or the row/column cursor) → transform (+ parallax) → hover by inverting the transform (2D) or by `Geometry.unproject` (3D) → content → children.
- **3D** (rotateX/rotateY/z ≠ 0): the content is drawn flat into a window-sized offscreen buffer (`Layers.push`, box at a margin of 10% of the screen), then the whole buffer is drawn as one quad through `Geometry.projection` (a real perspective matrix, so text, items and nested 3D tilt correctly). Buffer colours are premultiplied, so composite uses ONE, ONE_MINUS_SRC_ALPHA. Vanilla's `Framebuffer.clear()` unbinds, which is why `push` clears first and then binds. Nested 3D works with one buffer per depth.
- **Why buffers:** vanilla's `VertexConsumer.vertex(Matrix4f, …)` drops `w`, so a perspective matrix on the MatrixStack can't work.
- **States:** `Prop.pick(hovered, pressed, disabled)`. Number, length and colour props with states ease through `NodeState.Anim` when `transition.duration > 0`.
- **Clicks:** the topmost hovered enabled `button` of the frame takes the click (`ScreenMouseEvents.allowMouseClick` returns false). The action runs on press.
- **Loading screens:** six vanilla screens (`LoadingPart.kind`). Their background is one hook on `Screen.render`'s `renderBackground` call; per-frame start and the front layer use Fabric's `ScreenEvents.before/afterRender` (ReconfiguringScreen has no render method to mix into).
- **Launch overlay:** `SplashOverlay` draws no screen of its own: no clicks, no cursor. First start: vanilla clears the window (a hidden background clears to black); reloads: a fading fill, whose alpha the background elements use. Minecraft's font only exists after the first load: layout fonts work, vanilla-font text doesn't.
- **Window:** applied on the first client tick (the window exists) and after every reload; game values in title/border are re-read twice a second (`env.frame` is bumped, since nothing is drawn there).
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
- **Next:** M4 (HUD ports + CakeBench), M5 (API, Mod Menu screen, polish).
