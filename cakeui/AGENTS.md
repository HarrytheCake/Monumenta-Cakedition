# CakeUI: agent reference

Client-only Fabric library mod (`cakeui`, package `io.github.harrythecake.cakeui`) for Minecraft **1.20.4**. Layout folders, written by pack developers with no Java, redraw the game window, launching screen, main menu, loading screens and HUD. Terms are in [`GLOSSARY.md`](GLOSSARY.md): layout, part, window, subwindow, element, port, declaration, game value, design pixel, skin, dev mode. The user-facing guide is [`README.md`](README.md); keep both files current.

The standalone repo will be `HarrytheCake/CakeUI` (remote `cakeui`, not created yet; MIT). The source of truth is this folder in the Monumenta-Cakedition repo; never commit in the standalone repo.

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
├── build.gradle            # Loom 1.10.5; Java 17; Mod Menu compileOnly; runClient: -Dcakeui.selftest, 1280×720
├── gradle.properties       # MC 1.20.4, Yarn build.3, Loader 0.16.9, Fabric API 0.97.3, mod_version
├── GLOSSARY.md             # CakeUI's terms (listed in the root's local GLOSSARY-MAP.md)
├── README.md               # the layout guide (public)
├── schema/*.schema.json    # JSON Schemas for layout/declaration/window files; SchemaTest keeps them in step with PropKinds
├── examples/window_example # the example layout (generated art; ExampleLayoutTest loads it with 0 problems)
├── .github/workflows/build.yml   # CI: build + test; release v<mod_version> once per version
└── src/main/java/io/github/harrythecake/cakeui/
    ├── CakeUI.java          # entrypoint: values, keys (reload, button ids; unbound), Ui.reload, screen/HUD/tick events
    ├── Config.java          # config/cakeui/config.json5: layoutPath, devMode, showButtonIds (JSON5 reader)
    ├── Ui.java              # active layout + renderer; reload (keep previous on problems); dev-mode WatchService thread
    ├── McValues.java        # game values: time, mouse.*, screen.*, fps, lang, mc/pack version, server.*, world.*, player.*, menu.*
    ├── TitleMenu.java       # main menu part: ports background/logo/splash/version/buttons, skins, button ids
    ├── Overlay.java         # dev mode: problems in the top-left of every screen and the HUD
    ├── SelfTest.java        # only with -Dcakeui.selftest: screenshots the title screen with the mouse in 3 places, quits
    ├── json/                # Json5 (comments, trailing commas, unquoted keys, lines kept), JValue, Json5Exception
    ├── expr/                # Parser (precedence climbing, names bound at compile time), Ops, Functions, Template ({…}, {t:key}, "="), Values, Px, Env
    ├── model/               # Loader (folders → Nodes, every problem with file:line + "did you mean"), Layout, Node, Prop (base + hover/pressed/disabled),
    │                        #   PropKinds (which keys, which kind, which types), Scope (Constant/State/Computed slots), GameValues, Geometry (anchors,
    │                        #   matrices, projection, unproject for clicks through a tilt), Easing, Problems
    ├── render/              # Renderer (tree walk, sizes, anchors, arrange, states/transitions, parallax, 3D, hit tests, clicks), NodeState,
    │                        #   Paint (float quads), Textures (assets PNGs → dynamic textures, freed on reload), Fonts (TTF via TrueTypeFont, fallback to
    │                        #   Minecraft's fonts), Layers (offscreen buffers + perspective composite), Actions
    └── mixin/               # TitleScreenMixin (WrapOperation on panorama, overlay, logo, splash, version line, super.render; HEAD/TAIL),
                             #   ClickableWidgetMixin (skins: wraps renderWidget in render), accessors for fonts/splash, MouseInvoker (self-test)
```

Tests (`./gradlew build`, 22): Json5Test, ExprTest (units, folding, templates, errors), LoaderTest (whole layout, problems with file:line, scoping, own-folder writes, cycles, fatal window), GeometryTest (anchors, design scale, the hit-test unproject against the projection, the GL projection matrix, easings), ExampleLayoutTest, SchemaTest.

Build: `JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew build` in `cakeui/`. Dev client: `./gradlew runClient -Dcakeui.selftest=1` (needs `run/options.txt` with `onboardAccessibility:false` and `run/config/cakeui/config.json5` pointing at a layout; `run/` is ignored). It opens a window for about 30 s.

## How drawing works

- **One renderer per loaded layout** (`Renderer`). `beginFrame` sets the frame number, design scale (`rx`, `ry`), mouse and time. `drawPart`, `drawElements`, `drawSingle` and `drawSkin` walk nodes.
- **Per node:** visible → size (defaults: text = measured, image = texture size in r, background = parent's full size, item 16r, button 200×40r, bar 100×8r, containers = parent) → place (anchor/origin, or the row/column cursor) → transform (+ parallax) → hover by inverting the transform (2D) or by `Geometry.unproject` (3D) → content → children.
- **3D** (rotateX/rotateY/z ≠ 0): the content is drawn flat into a window-sized offscreen buffer (`Layers.push`, box at a margin of 10% of the screen), then the whole buffer is drawn as one quad through `Geometry.projection` (a real perspective matrix, so text, items and nested 3D tilt correctly). Buffer colours are premultiplied, so composite uses ONE, ONE_MINUS_SRC_ALPHA. Vanilla's `Framebuffer.clear()` unbinds, which is why `push` clears first and then binds. Nested 3D works with one buffer per depth.
- **Why buffers:** vanilla's `VertexConsumer.vertex(Matrix4f, …)` drops `w`, so a perspective matrix on the MatrixStack can't work.
- **States:** `Prop.pick(hovered, pressed, disabled)`. Number, length and colour props with states ease through `NodeState.Anim` when `transition.duration > 0`.
- **Clicks:** the topmost hovered enabled `button` of the frame takes the click (`ScreenMouseEvents.allowMouseClick` returns false). The action runs on press.
- **Skins:** `TitleMenu.applyButtons` (every frame) moves, resizes, hides and relabels buttons and registers skins. `ClickableWidgetMixin` draws the skin instead of `renderWidget`, so the vanilla button still handles input. Inside a skin, every element's hover = the button's hover.
- **Fonts:** a layout TTF is built per sharpness step (oversample 1/2/4/8/16 by on-screen scale). Minecraft's fonts are borrowed as fallbacks (wrapped so closing ours never closes theirs), and everything is rebuilt when Minecraft reloads its fonts. Tested in game with Bahnschrift + Chinese fallback (dev run, 2026-10-30).

## Invariants

- No layout = no work: every mixin returns to vanilla when `TitleMenu.port(…)`/`part()` is null.
- Problems never crash the game: load problems skip the broken element; runtime expression errors give the default and are listed once.
- Never send chat or commands; never download anything.
- Test data uses made-up names; no real player names anywhere.

## Status

- **M0** (repo folder, CI, glossary) and **M1** (config, loading, declarations, expressions, game values, hot reload, error overlay, schema) are done.
- **M2** (rendering on the main menu) is **done and self-tested in a dev client 2026-10-30**: screenshots show the example layout, hover with transitions through a 3D tilt, skins, parallax, TTF and zh_cn. **Waiting for the user's in-game test** in the test instance (jar + `cakeui/layouts/window_example` + `config/cakeui/config.json5` with devMode copied there 2026-10-30).
- Not yet tried in game: hot reload via the watcher, the reload key, the URL/connect actions, Mod Menu's button id (`modmenu.title` is a guess in the example).
- **Next:** M3 (pause and inventory subwindows, loading screen, game launching screen, window icon/title/border), M4 (HUD ports + CakeBench), M5 (API, Mod Menu screen, polish).
