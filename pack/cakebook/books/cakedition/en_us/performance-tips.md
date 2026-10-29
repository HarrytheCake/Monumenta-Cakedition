---
order: 5
---
# Performance tips

The pack is already tuned for speed. If the game still feels slow:

1. **Render distance** in Video Settings makes the biggest difference. Lower it first.
2. **Laptops:** plug in the charger. On battery, many laptops slow the graphics down a lot.
3. **Close other programs**, especially browsers and video calls.
4. **Shaders** (the optional Iris) cost a lot of FPS. Turn them off in Video Settings → Shader Packs if the game stutters.
5. **Java and memory:** use Java 21 and give the game the right amount of memory (see **Java and memory** below).

Video Settings are Sodium's, with more pages from Sodium Extra. Press F3 to see your FPS.

## Busy places

Crowded areas like the Market are hard on the processor rather than the graphics card, so lower settings help less there. That's normal.

## Java and memory

We recommend **Java 21**. The pack also runs on Java 17, but Java 21 is newer and usually runs the game more smoothly.

In Prism Launcher:

1. Install Java 21, for example **Eclipse Temurin 21** from adoptium.net (64-bit).
2. Edit the instance → **Settings** → **Java**. Tick **Java installation** and pick Java 21 (**Auto-detect**, or **Browse** to `javaw.exe` in its `bin` folder).
3. Tick **Skip Java compatibility checks**. Prism only offers Java 17 for Minecraft 1.20.4 and refuses Java 21 otherwise.

**Memory** (Maximum memory allocation, on the same page):

- 8 GB of RAM in your computer: 4 GB for Minecraft
- 16 GB: 6 GB
- 32 GB or more: 8 GB

Don't go above 12 GB: it doesn't help and can make pauses longer. Other launchers have the same settings under Java or memory.

## Reporting lag

A spark profile shows where the time goes:

1. Type `/sparkc profiler start` where it lags.
2. Play for a minute or two.
3. Type `/sparkc profiler stop`. spark uploads the result and gives you a link.

Share that link in your report (see [Getting help](help.md)). The upload is your choice; nothing is sent unless you run these commands.
