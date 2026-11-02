# CakeFixes

Small client-side fixes for vanilla Minecraft 1.20.4 and mod bugs that hurt on [Monumenta](https://www.playmonumenta.com/). Part of [Monumenta Cakedition](https://github.com/HarrytheCake/Monumenta-Cakedition). Fabric, client only. Needs Fabric Loader 0.15 or newer; no Fabric API.

Each fix has its own switch and is on by default. Turning a fix off gives exact vanilla behaviour. **Options** change what you see, so they're off by default.

## Fixes

- **Team removal freeze fix** (`teamRemovalFix`): sometimes the server removes a player from a scoreboard team they aren't on. Vanilla then builds a full crash report every time, which freezes the game for 100–500 ms, often every few seconds in Monumenta dungeons. CakeFixes skips that one removal instead, which is what vanilla would do if it didn't throw.
- **AppleSkin HUD slowdown fix** (`appleSkinBlendQueryFix`): every frame, AppleSkin asks the graphics driver whether blending is on, which makes the game wait for the driver (about 5% of frame time on NVIDIA). CakeFixes answers from Minecraft's own record instead. Nothing on screen changes. Does nothing without AppleSkin.

## Options

- **Banner draw distance** (`bannerDrawDistance`, off by default): banners are decoration but cost 3–4% of frame time in banner-heavy dungeons. Choose 16, 24, 32 or 48 blocks, and banners farther away aren't drawn. Off = vanilla (64 blocks).

## Results

Measured with CakeBench in a Monumenta dungeon (RTX 4060 laptop, same spot): the team removal fix took the 1% low from 8.3 to 34.7 FPS (29 → 1 frames over 100 ms). The AppleSkin fix saved 0.44 ms per frame.

## Settings

In Mod Menu (CakeFixes → configure), or in `config/cakefixes/config.json5`:

```json5
{
  "teamRemovalFix": true,
  "appleSkinBlendQueryFix": true,
  "bannerDrawDistance": 0   // 0 = off; 16, 24, 32 or 48 blocks
}
```

The Mod Menu screen also shows what each fix did this session. A config file from an older version gets the new settings added on the next start; your values are kept.

## Languages

English, 简体中文, 繁體中文.

## Build

```
./gradlew build
```

Needs JDK 21 to run Gradle; the mod targets Java 17. A push to `main` with a new `mod_version` publishes GitHub release `v<mod_version>` with the jar (see `.github/workflows/build.yml`).

## License

MIT
