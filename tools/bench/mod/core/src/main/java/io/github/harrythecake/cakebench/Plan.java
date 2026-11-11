package io.github.harrythecake.cakebench;

import java.util.List;
import java.util.Map;

/** {@code config/cakebench/plan.json}, written by bench.py: what a benchmark run does. Read with Gson. */
public final class Plan {
	public record Scene(String name, double x, double y, double z, float yaw, float pitch) {}

	public String run = "run";
	public String out = "cakebench-results";
	public String server;
	public boolean live;
	public int settleSeconds = 15;
	public int warmupSeconds = 20;
	public int measureSeconds = 60;
	public float turnDegreesPerSecond = 6;
	public boolean gpuTimers; // per-phase GPU/CPU time (GpuTimer); off for timed runs, the queries cost a little
	public boolean spark; // a spark CPU profile per scene; also a breakdown-run tool
	public int gpuTimerDepth = BenchStats.GPU_TIMER_DEPTH;
	public List<Scene> scenes = List.of();
	public Map<String, Object> notes = Map.of();
}
