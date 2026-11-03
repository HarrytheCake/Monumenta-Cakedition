package io.github.harrythecake.cakeui;

/**
 * Development only: how long InGameHud.render takes (CPU, the whole HUD, vanilla included), for
 * the self-test's HUD benchmark. Off unless the self-test turns it on: then two nanoTime calls per
 * frame.
 */
public final class HudTiming {
	static boolean enabled;
	private static long startedAt;
	private static long total;
	private static long frames;

	private HudTiming() {
	}

	public static void start() {
		if (enabled) {
			startedAt = System.nanoTime();
		}
	}

	public static void end() {
		if (enabled) {
			total += System.nanoTime() - startedAt;
			frames++;
		}
	}

	static void reset() {
		total = 0;
		frames = 0;
	}

	/** Average milliseconds per frame since the last reset, and the number of frames. */
	static double[] result() {
		return new double[] {frames == 0 ? 0 : total / 1e6 / frames, frames};
	}
}
