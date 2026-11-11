package io.github.harrythecake.cakebench;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchStatsTest {
	@Test
	void statsOfSteadyFramesWithOneHitch() {
		float[] f = new float[200];
		java.util.Arrays.fill(f, 10f);
		f[50] = 150f;
		f[51] = 30f;
		Map<String, Object> r = BenchStats.stats(f, 2.0);
		assertEquals(200, r.get("frames"));
		assertEquals(100.0, r.get("avgFps"));
		// the worst 1% of 200 frames = 2 frames: 150 and 30 ms → 90 ms mean → 11.1 FPS
		assertEquals(11.1, r.get("onePercentLowFps"));
		assertEquals(10.0, r.get("medianFrameMs"));
		assertEquals(150.0, r.get("maxFrameMs"));
		assertEquals(1, r.get("framesOver100ms"));
		assertEquals(10.8, r.get("meanFrameMs"));
	}

	@Test
	void statsOfASingleFrame() {
		Map<String, Object> r = BenchStats.stats(new float[] {25f}, 0.025);
		assertEquals(1, r.get("frames"));
		assertEquals(40.0, r.get("avgFps"));
		assertEquals(40.0, r.get("onePercentLowFps"));
		assertEquals(25.0, r.get("maxFrameMs"));
	}

	@Test
	void csvHasAHeaderAndOneLinePerFrame() {
		String csv = BenchStats.csv(new float[] {16.6667f, 8f});
		String[] lines = csv.split("\\R");
		assertEquals("frame_ms", lines[0]);
		assertEquals("16.667", lines[1]);
		assertEquals("8.000", lines[2]);
	}

	@Test
	void planDefaultsAndScenesFromJson() {
		Plan p = BenchStats.GSON.fromJson("""
				{"run": "step1", "live": true, "measureSeconds": 30,
				 "scenes": [{"name": "town", "x": 1.5, "y": 64, "z": -3, "yaw": 90, "pitch": 10}]}
				""", Plan.class);
		assertEquals("step1", p.run);
		assertEquals("cakebench-results", p.out);
		assertTrue(p.live);
		assertFalse(p.gpuTimers);
		assertEquals(15, p.settleSeconds);
		assertEquals(30, p.measureSeconds);
		assertEquals(BenchStats.GPU_TIMER_DEPTH, p.gpuTimerDepth);
		assertEquals(new Plan.Scene("town", 1.5, 64, -3, 90, 10), p.scenes.get(0));
	}

	@Test
	void gcCountersNeverGoNegative() {
		long[] gc = BenchStats.gc();
		assertTrue(gc[0] >= 0 && gc[1] >= 0);
	}
}
