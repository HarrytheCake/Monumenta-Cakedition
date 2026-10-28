---
order: 5
---
# Performance tips

The pack is already tuned for speed. If the game still feels slow:

1. **Render distance** in Video Settings makes the biggest difference. Lower it first.
2. **Laptops:** plug in the charger. On battery, many laptops slow the graphics down a lot.
3. **Close other programs**, especially browsers and video calls.
4. **Shaders** (the optional Iris) cost a lot of FPS. Turn them off in Video Settings → Shader Packs if the game stutters.
5. **Memory:** 4 to 8 GB suits most computers. Set it in your launcher, not in the game.

Video Settings are Sodium's, with more pages from Sodium Extra. Press F3 to see your FPS.

## Busy places

Crowded areas like the Market are hard on the processor rather than the graphics card, so lower settings help less there. That's normal.

## Reporting lag

A spark profile shows where the time goes:

1. Type `/sparkc profiler start` where it lags.
2. Play for a minute or two.
3. Type `/sparkc profiler stop`. spark uploads the result and gives you a link.

Share that link in your report (see [Getting help](help.md)). The upload is your choice; nothing is sent unless you run these commands.
