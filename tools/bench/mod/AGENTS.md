# CakeBench (tools/bench/mod)

Client-lag benchmark mod used by `tools/bench/bench.py` (see the docstring there and `docs/benchmarks/`).
**Never shipped in the pack.** Mod id `cakebench`, version in `gradle.properties` (`mod_version`).

## What it does

- **Plan runs** (`config/cakebench/plan.json`, written by `bench.py`): joins `plan.server` from the title screen,
  waits `settleSeconds` (and for operator rights, which the driver gives from the server console; a `live` plan
  needs none), then per scene: `/tp` there, turns the camera slowly through warm-up and measurement, records every
  frame's time, writes `<out>/<run>/<scene>.csv` and `summary.json`, and quits. `live` = a real server
  (Monumenta): **sends no chat and no commands**, never moves, declines the server resource pack. No plan → nothing.
- **Play recordings** without a plan: `/cakebench start [name]`, `stop`, `cancel`, `status` (a *client* command:
  never reaches the server). Writes `cakebench-results/play/<date>-<name>/` (`play.csv`, `summary.json`, spark
  profile if spark is installed) and sums it up in local chat. Leaving the server saves a running recording.
- **GPU timers** (`GpuTimer`, plan `gpuTimers` or every play recording): wraps the frame profiler returned by
  `Minecraft.constructProfiler` and puts a GL timestamp query + `System.nanoTime()` at every profiler section
  boundary down to `gpuTimerDepth`; read back a few frames later without stalling.
- **spark** (plan `spark`, or play recordings when spark is installed): `sparkc profiler start/stop
  --save-to-file` through the loader's client command dispatcher (local only, never uploaded).
- **Server side** `/cbbot <name> <pos>` (permission level 2): a fake player with an offline profile and a
  connection that goes nowhere, for crowded bench scenes (the bench server runs this jar too).

## Layout (Cake multiloader kit, see `tools/multiloader/README.md`)

```
core/                      plain Java: Plan (plan.json), BenchStats (stats, CSV, GC counters, Gson), platform/Platform
  src/test/java            BenchStatsTest (stats, CSV, plan parsing)
<mc>/common/               CakeBench (plan state machine), Play (/cakebench), GpuTimer, BenchServer (/cbbot),
                           SelfTest, Compat (the version-specific Minecraft calls), mixin/MinecraftClientMixin
<mc>/fabric/               CakeBenchFabric (main: /cbbot), CakeBenchFabricClient (events, /cakebench), FabricPlatform
<mc>/neoforge/             CakeBenchNeoForge (/cbbot via RegisterCommandsEvent), client events + /cakebench via
                           RegisterClientCommandsEvent (1.20.4: inside CakeBenchNeoForge behind a dist check;
                           1.21+: CakeBenchNeoForgeClient, @Mod(dist = CLIENT)), NeoForgePlatform
```

Version differences are kept in each version's `Compat.java` (plus `BenchServer`'s fake connection and
`GpuTimer`'s 26.1 profiler methods), so `CakeBench`/`Play`/`SelfTest` are the same text in every version folder:
change them in all three.

## Build, test, run

```
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build                          # all 6 jars + core tests
JAVA_HOME="C:/Program Files/Java/jdk-25.0.4" ./gradlew build -Pcake.versions=1.20.4   # one version
```

Jars: `build/libs/cakebench-<fabric|neoforge>-<mc>-<version>.jar`, plus `cakebench-<version>.jar` = the Fabric
1.20.4 jar, which is what `bench.py` copies into the bench server and the "Cakedition Bench" instance
(`bench_jar()` picks the newest `cakebench-<digits>*.jar`).

Self-test (dev client, singleplayer world): `-Dcakebench.selftest=play` → 4 s after the join it runs `/cbbot
CakeBot` on the integrated server, `/cakebench start selftest` through the client command dispatcher, `/cakebench
stop` 6 s later, saves `run/screenshots/cakebench-selftest.png` and logs `CakeBench self-test done: ...` (frames,
GPU-timed frames and phases, whether the fake player was seen) or `CakeBench self-test FAILED: ...`.

```
python tools/multiloader/smoke.py tools/bench/mod <mc> <loader> --world=<smoke world> \
    --jvm=-Dcakebench.selftest=play "--done=CakeBench self-test (done|FAILED)"
```

Plan path check: put a `live` plan in `<mc>/<loader>/run/config/cakebench/plan.json` (e.g. settle 3 / warm-up 2 /
measure 5 s, `gpuTimers: true`, one scene) and smoke with `--world` and `"--done=CakeBench scene '.*': "`: it
records, writes `run/cakebench-results/<run>/` and quits. Delete the plan afterwards (else every dev launch runs it).

Don't run benchmarks against Monumenta from here, and don't touch the "Cakedition Bench" Prism instance by hand.

## Multiloader status (2026-11-11)

Ported 2026-11-04/05 from the Yarn 1.20.4 Fabric mod (files moved with `git mv`; plan/stats logic into `core/`), finished
2026-11-11 (26.1.2 checked, `Compat.later` so `CakeBench`/`Play`/`SelfTest` are identical in all three versions, exact
Fabric `minecraft` dependency from the kit). Same on every target: mod id, plan file and fields, `/cakebench` (client
command), `/cbbot` (permission level 2), result folders, mixin targets (`Minecraft.runTick` TAIL,
`Minecraft.constructProfiler` RETURN; checked with javap in 26.1.2's vanilla jar).

Testing rule (user, 2026-11-07): the game runs only on Fabric 1.20.4; the other targets get build + unit tests.

| Target | Build + tests (5 core) | Dev client | Differs (why) |
|---|---|---|---|
| 1.20.4 Fabric | OK 2026-11-11 | 2026-11-11 PASS: `-Dcakebench.selftest=play` in the smoke world (592 frames, 590 GPU-timed, 63 phases, fake player seen, screenshot checked) and the plan join path (a `live` plan with `server` = an unused local port: title screen → `Compat.later` → "Connecting to …", no errors) | the bench's jar (`cakebench-<version>.jar` is a copy of it) |
| 1.20.4 NeoForge | OK 2026-11-11 | PASS 2026-11-05 (earlier build; not rerun) | client part inside `CakeBenchNeoForge` behind an `FMLEnvironment.dist` check; client command via `RegisterClientCommandsEvent` |
| 1.21.1 Fabric | OK 2026-11-11 | PASS 2026-11-05 (earlier build; not rerun) | `ConnectScreen.startConnecting(…, null)` (transfer state), `CommonListenerCookie(…, false)`, the fake connection has no `setListener` |
| 1.21.1 NeoForge | OK 2026-11-11 | PASS 2026-11-05 (earlier build; not rerun) | as 1.21.1 Fabric; `@Mod(dist = CLIENT)` client class `CakeBenchNeoForgeClient` |
| 26.1.2 Fabric | OK 2026-11-11 | not run (rule above) | `client.schedule` (was `tell`), renderer/GL version from `RenderSystem.getDevice()`, `graphicsPreset()`, `ResourceKey.identifier()`, `Window.handle()`, permissions via `Permissions.COMMANDS_GAMEMASTER` / `Commands.hasPermission(LEVEL_GAMEMASTERS)`, `ClickEvent.OpenFile`, `addClientSystemMessage`, `Screenshot.grab(…, 1, …)`; fake connection: `ChannelFutureListener` + `setupInbound/OutboundProtocol`; `GpuTimer` forwards the new zone methods (`addZoneText/Value`, `setZoneColor`) and turns itself off instead of failing when there is no current GL context; Fabric API `ClientCommands` (was `ClientCommandManager`) |
| 26.1.2 NeoForge | OK 2026-11-11 | not run (rule above) | as 26.1.2 Fabric (NeoForge parts same as 1.21.1) |

Not checked on 1.21.1/26.1.2 in a game since the port: GPU timers against 1.21.2+'s `Profiler.use` loop (the profiler
returned by `constructProfiler` should still be the frame's one), a plan run against a real bench server. Only the Fabric
1.20.4 jar is used by `bench.py`; the bench server runs on JDK 21 (`SERVER_JAVA`).
