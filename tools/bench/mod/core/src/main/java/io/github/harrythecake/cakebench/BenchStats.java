package io.github.harrythecake.cakebench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Frame-time statistics and the other numbers a recording keeps (no Minecraft here). */
public final class BenchStats {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final int GPU_TIMER_DEPTH = 4;

	private BenchStats() {}

	/** avg FPS, 1% low, median / max / mean frame time and frames over 100 ms of a recording {@code seconds} long. */
	public static Map<String, Object> stats(float[] f, double seconds) {
		Map<String, Object> r = new LinkedHashMap<>();
		float[] sorted = f.clone();
		Arrays.sort(sorted);
		double sum = 0;
		for (float v : f) {
			sum += v;
		}
		int worst = Math.max(1, sorted.length / 100);
		double worstSum = 0;
		for (int i = sorted.length - worst; i < sorted.length; i++) {
			worstSum += sorted[i];
		}
		int over100 = 0;
		for (float v : f) {
			if (v > 100) {
				over100++;
			}
		}
		r.put("frames", f.length);
		r.put("avgFps", round(f.length / seconds));
		r.put("onePercentLowFps", round(1000 / (worstSum / worst)));
		r.put("medianFrameMs", round(sorted.length == 0 ? 0 : sorted[sorted.length / 2]));
		r.put("maxFrameMs", round(sorted.length == 0 ? 0 : sorted[sorted.length - 1]));
		r.put("framesOver100ms", over100);
		r.put("meanFrameMs", round(f.length == 0 ? 0 : sum / f.length));
		return r;
	}

	/** One frame time per line, header {@code frame_ms}. */
	public static String csv(float[] f) {
		StringBuilder csv = new StringBuilder("frame_ms\n");
		for (float v : f) {
			csv.append(String.format(Locale.ROOT, "%.3f%n", v));
		}
		return csv.toString();
	}

	/** {collections, milliseconds} of every garbage collector so far. */
	public static long[] gc() {
		long count = 0, millis = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, b.getCollectionCount());
			millis += Math.max(0, b.getCollectionTime());
		}
		return new long[] {count, millis};
	}

	public static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}

	public static double round3(double v) {
		return Math.round(v * 1000) / 1000.0;
	}
}
