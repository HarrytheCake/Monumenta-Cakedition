# CakeFixes: agent reference

Client-only mod (`cakefixes`, package `io.github.harrythecake.cakefixes`) for **Fabric and NeoForge** on Minecraft **1.20.4, 1.21.1 and 26.1.2** (Cake multiloader kit, `tools/multiloader/README.md` in the Cakedition repo; the pack ships the Fabric 1.20.4 jar). It's a home for small fixes to vanilla (or mod) bugs that hurt on Monumenta. Each fix has its own switch, on by default. **Options** trade something you can see for FPS, so they're off by default (client-lag rule: cosmetic costs may be off by default or optional). Started 2026-11-02. License MIT. **Ships in the Cakedition pack since 2.5.0 (2026-11-02), on by default** (`pack/mods/cakefixes.pw.toml` → the GitHub release jar).

## Fixes

| Switch (`config.json5`) | Since | What it fixes |
|---|---|---|
| `teamRemovalFix` | 0.1.0 | Freezes when the server removes a player or entity from a team it isn't on |
| `appleSkinBlendQueryFix` | 0.2.0 | AppleSkin's HUD making the CPU wait for the driver every frame |
| `bannerDrawDistance` (option, int, 0 = off) | 0.3.0 | Banners drawn up to 64 blocks away (the costliest block entity in dungeons) |

### teamRemovalFix

- **Found** 2026-11-02 with CakeBench run `dungeon1` (test instance, `monumenta:fortune`): avg 89 FPS but 1% low 8.3, with a 400–500 ms frame every 5.00 s. In spark, 8.1% of the render thread went to `CrashReport.<init>` → `SystemDetails` → OSHI WMI queries (graphics card, CPU id, physical memory). Spark charged that time to `java-discord-rpc`, which bundles OSHI; CraftPresence/Discord weren't involved. The log held 197 copies of `Main thread packet handler` caused by `IllegalStateException: Player is either on another team or not on any team. Cannot remove from team 'red'` (also `aqua` and `unpushable_white`), in bursts.
- **Cause (vanilla 1.20.4):** `ClientPlayNetworkHandler.onTeam` (intermediary `class_634.method_11099`) calls `Scoreboard.removeScoreHolderFromTeam` (`class_269.method_1157`) for each name in a REMOVE packet. It throws when `getScoreHolderTeam(name) != team`. `NetworkThreadUtils` then builds a full `CrashReport` (the slow hardware queries) and the executor logs it. The rest of that packet's names are never processed. No mod in the pack touches team add/remove (checked the refmaps), so it's the server's packets plus vanilla.
- **Mojang names** (since the multiloader move): `ClientPacketListener.handleSetPlayerTeamPacket` → `Scoreboard.removePlayerFromTeam(String, PlayerTeam)`; the mixin class keeps its old name.
- **Fix:** `ClientPlayNetworkHandlerMixin`, a MixinExtras `@WrapWithCondition` on that one call (MixinExtras ships with Fabric Loader ≥ 0.15; it stacks with other mods' wrappers). `TeamRemoval.shouldRemove` lets the call run when the holder is on that team or the switch is off, and skips it otherwise. Skipping is exactly what vanilla would do without the throw: the holder isn't on that team, so there's nothing to remove. The remaining names in the packet are now processed too.
- **Logging:** the first skip per team name goes to the log at INFO (team name only, never the holder: holders are player names or entity UUIDs). After that it only counts. The count shows in the Mod Menu screen ("Skipped this session: N").

### appleSkinBlendQueryFix

- **Found** 2026-11-02 in CakeBench `dungeon2` (RTX 4060, test instance): 4.8% of the render thread was spent in `GL11.glIsEnabled`, all called from AppleSkin's `HUDOverlayHandler.enableAlpha` (4.3% from `drawExhaustionOverlay`, the rest from the saturation and hunger overlays). AppleSkin 2.5.1 calls `glIsEnabled(GL_BLEND)` every frame to remember whether to turn blending off again. A `glIsEnabled`/`glGet*` call makes the CPU wait for the driver; with NVIDIA's threaded optimisation it waits for the driver thread to catch up.
- **Fix:** `AppleSkinHudOverlayMixin` is `@Pseudo` (skipped without AppleSkin) with `remap = false`. It puts a MixinExtras `@WrapOperation` with `require = 0` on that call: if an AppleSkin update renames `enableAlpha`, the fix just stops applying (the count then stays 0). `BlendQuery.isEnabled` answers `GL_BLEND` from Minecraft's own tracked state, `GlStateManager.BLEND.capState.state`. That's the flag `RenderSystem.enableBlend/disableBlend` already use to decide whether to call `glEnable` at all. Other caps, switch off, or tracker not found → the real query.
- **Finding the tracked state** (`TrackedBlend`): both inner classes are private, so reflection asks the loader's `Platform` for the runtime names: on Fabric 1.20.4/1.21.1 the intermediary names `GlStateManager$class_1017.field_5045` / `$class_1018.field_5051` through Fabric's `MappingResolver`; on NeoForge and on Fabric 26.1.2 Mojang's names `GlStateManager$BlendState.mode` / `$BooleanState.enabled`. Then a bound `MethodHandle`. On failure (e.g. a version where these fields are gone) it logs one WARN and AppleSkin keeps its own query.
- **Risk:** the tracked state is wrong only if some mod turns `GL_BLEND` on or off with raw GL calls instead of through `GlStateManager`, right before the food bar is drawn. The worst case is AppleSkin leaving blend on or turning it off once. No HUD code in the pack does that.
- **Count:** "Driver queries saved this session" in Mod Menu (about 3 per frame with all AppleSkin overlays on).

### bannerDrawDistance (option, off by default)

- **Found** in `dungeon1`/`dungeon2`: the block entity renderers by cost were banners 2.8–3.7% of the render thread, mob spawners 1.3%, signs 1.1% (already EBE), chests 0.3%. Banners are drawn with one model pass per pattern layer every frame. No Modrinth mod optimizes banners on 1.20.4 (searched 2026-11-02).
- **How:** `BannerBlockEntityRendererMixin` implements the raw `BlockEntityRenderer` and overrides its default `isInRenderDistance` in `BannerBlockEntityRenderer` only. The jar remap renames it to `method_33892(class_2586, class_243)` (checked with javap). `BlockEntityRenderDispatcher` asks it before every banner draw. `BannerDistance.visible` uses the same strict `<` on the squared distance from the block centre as vanilla. 0, negative or ≥ 64 = vanilla's 64.
- **Choices** in Mod Menu: Off / 16 / 24 / 32 / 48 blocks (`BannerDistance.CHOICES`). A hand-edited value outside the list is kept as an extra choice.
- **Count:** banner draws skipped that vanilla would have drawn.
- **Not hidden on purpose:** mob spawners (the spinning mob shows what spawns; dungeon spawners matter in play), signs (text is information).

## Files

```
cakefixes/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/   # Cake multiloader kit (copied by tools/multiloader/apply_kit.py; don't edit here)
├── gradle.properties        # mod_id, mod_name, mod_version, mod_group; cake_fabric_api=false (no Fabric API)
├── .github/workflows/build.yml   # CI in the standalone repo (still the pre-multiloader one: see "Release")
├── core/                    # plain Java, no Minecraft imports; compiled into every jar
│   ├── src/main/java/io/github/harrythecake/cakefixes/
│   │   ├── CakeFixes.java     # MOD_ID, LOGGER, lazily loaded config (no entrypoint: mixins read it on first use)
│   │   ├── Config.java        # config/cakefixes/config.json5; parse/write are plain Java
│   │   ├── TeamRemoval.java, BlendQuery.java, BannerDistance.java   # the decisions + counts (tested)
│   │   └── platform/Platform.java, Services.java   # what the loader provides (config dir, runtime names), ServiceLoader
│   ├── src/main/resources/assets/cakefixes/   # lang/{en_us,zh_cn,zh_tw}.json (same keys, tested), icon.png
│   └── src/test/java/…/CakeFixesTest.java
└── <mc>/ = 1.20.4, 1.21.1, 26.1.2   # version.properties from the kit
    ├── common/src/main/java/…/      # ConfigScreen, CakeFixesHooks, TrackedBlend, mixin/{ClientPlayNetworkHandler,
    │   │                            #   AppleSkinHudOverlay,BannerBlockEntityRenderer}Mixin
    │   └── src/main/resources/cakefixes.mixins.json   # no refmap (Loom remaps inside the classes)
    ├── fabric/   …/fabric/FabricPlatform.java, ModMenuEntry.java; fabric.mod.json; META-INF/services/…Platform
    └── neoforge/ …/neoforge/CakeFixesNeoForge.java (@Mod, config screen extension point), NeoForgePlatform.java;
                  META-INF/mods.toml (1.20.4) or neoforge.mods.toml (1.21.1+); META-INF/services/…Platform
```

Version differences in the code: 26.1.2's `ConfigScreen` uses `GuiGraphicsExtractor`/`extractRenderState`/`centeredText`, ARGB text colours and `CycleButton.builder(fn, initial)`; NeoForge's entry uses `ConfigScreenHandler` on 1.20.4 and `IConfigScreenFactory` on 1.21+; Fabric 26.1.2's `FabricPlatform` returns Mojang names (no intermediary at runtime).

## Multiloader status

| Target (2026-11-04) | Build + tests | Smoke (dev client, test world by quick play) | Notes |
|---|---|---|---|
| 1.20.4 Fabric | ✓ | ✓ joined, no errors | the pack's jar (`build/libs/cakefixes-<version>.jar` is a copy of it) |
| 1.20.4 NeoForge | ✓ | ✓ joined, no errors | |
| 1.21.1 Fabric | ✓ | ✓ joined, no errors | |
| 1.21.1 NeoForge | ✓ | ✓ joined, no errors | |
| 26.1.2 Fabric | ✓ | ✓ joined, no errors | |
| 26.1.2 NeoForge | ✓ | ✓ joined, no errors | |

Not checked on 1.21.1/26.1.2 yet: whether Monumenta-style team removals still throw there (vanilla may have changed `removePlayerFromTeam`), whether AppleSkin for those versions still calls `glIsEnabled` (the mixin has `require = 0`, so it just doesn't apply), and whether `GlStateManager`'s blend tracker still exists (else `TrackedBlend` logs one WARN). The fixes are harmless where the bug is gone.

## Adding a fix

1. Find it with evidence (CakeBench / spark / log), and record the evidence under **Fixes** above.
2. Add a `public boolean <name>Fix = true;` switch to `Config` (also in `copy()` and the `write()` template, with a comment).
3. Put the decision in a plain-Java class when possible, so it can be tested. Keep mixins to one narrow injection that asks it.
4. Add a `Fix` row to `ConfigScreen.FIXES`, and add `cakefixes.fix.<switch>`, `.tooltip` and `.count` (or drop `.count` from the test if the fix counts nothing) to **all three** lang files.
5. An option instead of a fix (changes what you see): a `public int` field, 0 = off, with `.value` lang text too and its own button in `ConfigScreen` (like `bannerDistanceButton`). The tests find int fields as options.
6. Bump `mod_version`. `CakeFixesTest` fails if a switch is missing from the template or a lang file.

## i18n

- Every string the player sees is in `lang/en_us.json`, `zh_cn.json` and `zh_tw.json`, with the same keys (`CakeFixesTest` checks this, and checks that each switch has its label, tooltip and count).
- Mod Menu shows the translated name and description through `modmenu.nameTranslation.cakefixes` / `modmenu.descriptionTranslation.cakefixes`. Keep the description in step with `fabric.mod.json`'s English one.
- Wording: say what the player notices (a freeze), not internals. zh_cn uses mainland terms (崩溃报告, 地下城), zh_tw Taiwanese ones (當機報告, 地城, 略過, 用戶端).
- New languages: copy en_us, translate every value, and add the code to `LANGUAGES` in the test.

## Invariants

- Client-only. It sends nothing to the server, downloads nothing, and never changes what the server sees.
- A fix only removes a vanilla failure path. It never hides gameplay information.
- Every fix can be switched off, and off means exact vanilla behaviour.
- No player names in logs, tests or docs.

## Config upgrades

`Config.load` rewrites the file when it lacks any public field's name (`needsRewrite`). It keeps the values read, adds new fixes as on and new options as off, and refreshes the comments. Found 2026-11-02: a 0.1.0 file never showed `bannerDrawDistance`.

## Tests

`JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build` (all versions and loaders; `-Pcake.versions=1.20.4` for one) runs 9 JUnit tests (`CakeFixesTest`, in `core`): the team-removal, blend-query and banner-distance decisions, config defaults/parse/write/copy for every switch and option (found by reflection), upgrading an older config file, and lang key parity.

**In game (2026-11-02, test instance, same spot in `monumenta:fortune`):** CakeBench `dungeon2` with the fix vs `dungeon1` without: avg 89.2 → 109.8 FPS, 1% low 8.3 → 34.7, worst frame 517 → 166 ms, frames over 100 ms 29 → 1. The log has no `Main thread packet handler` errors and one `Skipped a removal from team 'red'` line; crash-report building fell from 8.1% of the render thread to 0.

**bannerDrawDistance (0.3.0):** not tested in game yet (it was off during `dungeon3`: the 0.1.0 config file had no key for it, which led to the config upgrade below). Check: off = banners as before; at 24, banners pop out past 24 blocks and back in when closer, and the count goes up; no other block entity is affected.

**appleSkinBlendQueryFix (0.2.0):** verified in CakeBench `dungeon3` (2026-11-02, with 0.3.0 and Particle Core): AppleSkin's `glIsEnabled` went from 0.44 ms per frame to 0, and AppleSkin no longer shows in the render-thread list. Check: the Mod Menu count goes up while the HUD shows; the food bar, saturation and exhaustion overlays look the same (including the flashing held-food preview); no `appleSkinBlendQueryFix` WARN in the log; `glIsEnabled` is gone from the render thread in the next CakeBench profile.

## Release

Same as the other Cake mods: bump `mod_version`, commit `cakefixes/` only, push (only when the user asks), and CI publishes `v<mod_version>` with the jar. **The CI file is still the pre-multiloader one** (JDK 21, `build/libs/cakefixes-<version>.jar`): the build now needs JDK 25 to run Gradle; the root `build` still writes `build/libs/cakefixes-<version>.jar` (= Fabric 1.20.4), but the other five jars aren't released until the CI is updated (user, 2026-11-04: not now). Then `packwiz_sync.py github mods HarrytheCake/CakeFixes v<version>`. Standalone repo `HarrytheCake/CakeFixes` (remote `cakefixes`, in `push_all` TARGETS, first pushed 2026-11-02; no GitLab copy yet).
