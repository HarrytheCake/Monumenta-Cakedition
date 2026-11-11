# Cake multiloader kit

Every Cake mod builds for **Fabric and NeoForge** on **Minecraft 1.20.4, 1.21.1 and 26.1.2** from one
Gradle build per mod folder (each mod folder is also its own GitHub repo through `git subtree`, so the
kit is copied into each mod, not shared at build time). Based on
[jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (branch per version
there; folder per version here).

## Layout of a mod folder

```
<mod>/
├── settings.gradle, build.gradle, buildSrc/, gradlew, gradle/   ← the kit (apply_kit.py copies them; don't edit in the mod)
├── gradle.properties        mod_id, mod_name, mod_version, mod_group (+ cake_fabric_api=false if no Fabric API)
├── core/                    plain Java, NO Minecraft / loader imports: logic, parsers, stores, formats, config
│   ├── src/main/java        compiled into every jar (with that version's Java)
│   ├── src/main/resources   shared assets (assets/<mod>/lang, textures, icon …)
│   └── src/test/java        unit tests (JUnit 5, Gson + SLF4J available)
└── <mc>/                    one folder per Minecraft version: 1.20.4, 1.21.1, 26.1.2
    ├── version.properties   copied from kit/versions/<mc>.properties (Minecraft, Java, loader, API versions)
    ├── common/              Minecraft-facing code for both loaders (Mojang names): screens, mixins, rendering
    │   └── src/main/{java,resources}   (mixin configs live here: <mod>.mixins.json, no "refmap" key)
    ├── fabric/              Fabric-only: entrypoints, Mod Menu, Fabric API events, fabric.mod.json
    └── neoforge/            NeoForge-only: @Mod class, events, config screen, META-INF/(neoforge.)mods.toml
```

- Each part has a `build.gradle` with `plugins { id 'cake-<part>' }`; extra dependencies go below it.
  In `fabric/build.gradle` use `modDep(...)`, `modCompileOnlyDep(...)`, `modRuntimeOnlyDep(...)` (they pick
  `modImplementation` for 1.20.4/1.21.1 and `implementation` for 26.1.2). Bundled plain libraries
  (jsoup, JSVG …): `implementation` + `include` on Fabric, `implementation` + `jarJar` on NeoForge.
- The loaders compile `core` + `common` + their own sources together. The `common` project compiles
  core + common against vanilla Minecraft (ModDevGradle vanilla mode) to prove common has no loader classes.
- Loader-specific things reach common/core through a small service interface in core
  (`<pkg>.platform.Platform`, loaded with `ServiceLoader` via `META-INF/services/<pkg>.platform.Platform`
  in each loader part): config dir, game dir, is-mod-loaded, loader name, … Keep it small.
- `${version}`, `${mod_id}`, `${mod_name}` and every key of `version.properties` (e.g. `${fabric_mc_dep}`,
  `${neoforge_mc_range}`, `${neoforge_range}`, `${neoforge_loader_range}`, `${java_version}`) are expanded in
  `fabric.mod.json`, `META-INF/mods.toml`, `META-INF/neoforge.mods.toml`, `*.mixins.json`, `pack.mcmeta`.
- NeoForge metadata: 1.20.4 = `META-INF/mods.toml`, 1.21.1+ = `META-INF/neoforge.mods.toml`.
  Client-only mods: `@Mod(value = ID, dist = Dist.CLIENT)` (1.21+), `FMLEnvironment.dist` check on 1.20.4;
  dependency `side = "CLIENT"`. Config screen: 1.20.4 `ConfigScreenHandler.ConfigScreenFactory`,
  1.21+ `container.registerExtensionPoint(IConfigScreenFactory.class, …)`.
- `fabric_mc_dep` is the **exact** Minecraft version (`1.21.1`, never `~1.21.1`: Fabric reads the tilde as
  "up to 1.22", so a 1.21.1 jar loaded on 1.21.11 and crashed on its mixin).
- Extra version file `versions/1.21.11.properties` (2026-11-11, CakeWiki only, for Wynncraft; not one of ADR 0009's
  three targets; its `neoforge_*` values are unverified).
- Names: Mojang's everywhere (no Yarn). 1.20.4 and 1.21.1 are obfuscated, Loom remaps the Fabric jar
  (mixin targets are remapped inside the classes; no refmap). NeoForge runs on Mojang's names since 20.2.
  26.1+ ships unobfuscated (no remapping anywhere; Fabric's runtime names are Mojang's too).
- Several mods in one build (CakeText: `cake_mods=core,collector,translator` in the build folder's
  `gradle.properties`): each listed folder is laid out like a one-mod build (`core/` + `<mc>/<part>`, own
  `gradle.properties` with `mod_id` etc.; own `LICENSE`/`NOTICE` if it has one); `cake_ship=false` = a library
  bundled inside the others, no jars of its own. `apply_kit.py <build folder>` puts the version files into each
  listed folder; `smoke.py <build folder>/<mod>` runs Gradle from the build folder. A version folder that still
  exists (even only ignored build output) counts as a version for `apply_kit.py`: delete dropped versions entirely.

## Build, test, run

Gradle 9.8 runs on **JDK 25** (Loom 1.19); each version compiles with its own Java (17 / 21 / 25,
found by Gradle's toolchains).

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # everything
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=1.21.1   # one version (+ core)
```

Jars land in `<mod>/build/libs/`: `<mod_id>-<fabric|neoforge>-<mc>-<mod_version>.jar`, plus
`<mod_id>-<mod_version>.jar` = the Fabric 1.20.4 jar (the name the Cakedition pack and the old release CI use).

**Smoke test** (one dev client; at most two run at once on this machine, the script waits for a slot):

```
python tools/multiloader/smoke.py <mod> <mc> <fabric|neoforge>                  # title screen, log check
python tools/multiloader/smoke.py <mod> <mc> <loader> --world=<world dir>        # also opens a world (quick play)
python tools/multiloader/smoke.py <mod> <mc> <loader> --jvm=-Dcakemap.selftest=world --done="self-test done"
```

It starts `:<mc>:<loader>:runClient`, waits for the title screen (or `--done`), lists log lines with the
mod id and any error / mixin failure lines, then closes the game. `SMOKE PASS` = reached the goal with no
such errors. `winshot.py "<window title>" out.png` captures the game window alone (never the desktop).

## Porting a 1.20.4 Fabric (Yarn) mod into this layout

1. In the old build: `./gradlew migrateMappings --mappings "net.minecraft:mappings:1.20.4"` (Loom remaps the
   Java code to Mojang's names into `remappedSrc/`), copy `remappedSrc` over `src/main/java`.
2. `python tools/multiloader/yarn2moj.py <src dir>`: rewrites the Yarn names Loom leaves in strings (mixin
   `method =`, `target =`, `@Accessor`/`@Invoker`, descriptors). Fix any `UNRESOLVED` by hand.
3. `git mv` the files into `core/` (no Minecraft imports) or `1.20.4/common` / `1.20.4/fabric`; add the
   Platform service, the NeoForge part; `python tools/multiloader/apply_kit.py <mod>`; build; smoke both loaders.
4. Copy `1.20.4/{common,fabric,neoforge}` to `1.21.1/` and `26.1.2/`, fix what changed in Minecraft and the
   loaders, build, smoke.
