# Benchmark 2026-10-28-step1-partial

> **Partial and preliminary.** The step-1 batch was stopped early. Only the runs before about 05:20 are here: later
> runs were 2–3× faster in some scenes with the same scenes, GPU and cores, so the laptop's state had changed (likely
> the charger or other load) and they can't be compared. baseline#1 is also left out: the first run of the batch was
> ~10% faster in the heaviest scenes while the laptop was still cool (later batches start with a discarded warm-up run).
> Baseline #2 and #3 agree within about 2%, so 30 s windows are precise enough. Step 1 is to be rerun in full.
>
> Takeaways so far: Entity Culling +14% average FPS in the mobs scene (pillars hide mobs), no change elsewhere;
> FerriteCore, Dynamic FPS and BadOptimizations change nothing measurable. Every scene already meets the average-FPS
> targets on the low tier; the mixed scene's 1% low (about 31–36 FPS) is the closest to the 30 FPS bar.

- Renderer: Intel(R) RaptorLake-S Mobile Graphics Controller (3.2.0 - Build 32.0.101.7026)
- Java 17.0.12, heap 4096 MB, 4 CPUs visible to the game
- Render distance 12, graphics fast, max FPS 260
- Scenes are synthetic (tools/bench/bench.py); medians over repeats; 1% low = average FPS of the slowest 1% of frames.

## Average FPS

| Set | runs | normal | mobs | market | players | mixed |
|---|---|---|---|---|---|---|
| +badoptimizations | 1 | 168.3 (+0%) | 106.5 (+2%) | 168.4 (-0%) | 140.7 (-0%) | 88.1 (-0%) |
| +dynamicfps | 1 | 168.3 (+0%) | 105.9 (+2%) | 167.7 (-0%) | 141.2 (+0%) | 86.5 (-2%) |
| +entityculling | 1 | 169.4 (+1%) | 119.3 (+14%) | 167.7 (-0%) | 142.4 (+1%) | 86.9 (-2%) |
| +ferritecore | 1 | 170.2 (+1%) | 103.6 (-1%) | 167.6 (-1%) | 142 (+1%) | 86.7 (-2%) |
| baseline | 2 | 168.3 | 104.2 | 168.5 | 140.85 | 88.45 |

## 1% low FPS

| Set | runs | normal | mobs | market | players | mixed |
|---|---|---|---|---|---|---|
| +badoptimizations | 1 | 47.9 (-0%) | 40 (+9%) | 50.3 (-0%) | 45.2 (-1%) | 31.4 (-37%) |
| +dynamicfps | 1 | 49 (+2%) | 38.9 (+6%) | 50.1 (-1%) | 45 (-1%) | 30.6 (-39%) |
| +entityculling | 1 | 42.4 (-12%) | 40.2 (+10%) | 48.9 (-3%) | 45.7 (+1%) | 36.6 (-27%) |
| +ferritecore | 1 | 48.1 (+0%) | 36.9 (+1%) | 47.4 (-6%) | 46.1 (+1%) | 35.8 (-29%) |
| baseline | 2 | 48.05 | 36.55 | 50.55 | 45.45 | 50.1 |

## Worst frame (ms)

| Set | runs | normal | mobs | market | players | mixed |
|---|---|---|---|---|---|---|
| +badoptimizations | 1 | 25.1 | 31 | 25.6 | 28.7 | 63.3 |
| +dynamicfps | 1 | 23.5 | 41.6 | 26.4 | 26.6 | 45.2 |
| +entityculling | 1 | 215.4 | 36.9 | 30.9 | 28.7 | 45.2 |
| +ferritecore | 1 | 32.1 | 37.1 | 33.9 | 25.4 | 51.6 |
| baseline | 2 | 28.5 | 156.75 | 23.15 | 26.8 | 30.05 |

## Sets

- **+badoptimizations**: small render and lightmap savings add 2j7ZpIyw
- **+dynamicfps**: no in-game gain expected; must not cost focused FPS add TBg9M5dN
- **+entityculling**: skip mobs and block entities behind walls add 7w0DgYXF
- **+ferritecore**: less memory, fewer GC pauses add pguEMpy9
- **baseline**: the 2.2.1 pack as shipped; 3 runs to measure the noise
