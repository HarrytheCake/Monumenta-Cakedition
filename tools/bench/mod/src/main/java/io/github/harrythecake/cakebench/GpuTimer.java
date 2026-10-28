package io.github.harrythecake.cakebench;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.SampleType;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GPU and CPU time per render phase. Wraps the client's frame profiler (the vanilla push/swap/pop sections:
 * {@code gameRenderer/level/entities}, {@code gameRenderer/gui}, {@code updateDisplay}, ...) and at every
 * section boundary down to {@code depth} records a GL timestamp query and {@link System#nanoTime()}. The client tick
 * counts as one phase: it pushes a section per entity type, CPU work that spark breaks down better. Queries are read
 * back a few frames later without waiting; a frame whose results aren't ready when its slot comes round again is
 * dropped. Opt-in ({@code gpuTimers} in the plan), so timed runs don't pay for the queries.
 *
 * <p>Approximate by nature: a GPU interval is the time between the GPU reaching two markers, so when the GPU waits on
 * the CPU the wait lands in the phase as well. Compare the GPU and CPU columns: GPU well above CPU means that phase
 * is GPU-bound; the two about equal means the GPU was keeping up.
 */
public final class GpuTimer implements Profiler {
	private static final Logger LOG = LoggerFactory.getLogger("cakebench");
	private static final int RING = 8;
	private static final int MAX_MARKS = 512;
	private static final String ROOT = "(outside sections)";
	private static final String TICK = "tick";

	private static GpuTimer instance;
	private static boolean checked, supported;

	private final int maxDepth;
	private Profiler delegate;
	private boolean recording;
	private boolean inFrame;

	// the section stack, kept only down to maxDepth; deeper sections are counted, not named
	private final String[] paths;
	private int depth;
	private int limit; // maxDepth, or 1 inside the client tick
	private final Map<String, Map<String, String>> children = new HashMap<>();

	// one slot of queries per frame in flight
	private final int[][] queries = new int[RING][MAX_MARKS];
	private final long[][] cpu = new long[RING][MAX_MARKS];
	private final String[][] labels = new String[RING][MAX_MARKS];
	private final int[] marks = new int[RING];
	private final boolean[] pending = new boolean[RING];
	private final boolean[] overflowed = new boolean[RING];
	private int slot;
	private int oldest; // the next slot to read back

	private final Map<String, long[]> totals = new HashMap<>(); // label -> {gpu ns, cpu ns}
	private int framesRead, framesLate, framesOverflowed, maxMarks;

	private GpuTimer(int maxDepth) {
		this.maxDepth = maxDepth;
		this.paths = new String[maxDepth + 1];
		this.paths[0] = ROOT;
		this.limit = maxDepth;
	}

	/**
	 * Called from the plan, or by {@code /cakebench start}; only the first call counts. Does nothing until
	 * {@link #wrap} finds timer queries on the render thread (the next frame).
	 */
	static void enable(int depth) {
		if (instance == null) {
			instance = new GpuTimer(Math.max(1, depth));
		}
	}

	/** Called by the mixin with the profiler the client is about to use for the next frame. */
	public static Profiler wrap(Profiler profiler) {
		if (instance == null || !RenderSystem.isOnRenderThread()) {
			return profiler;
		}
		if (!checked) {
			checked = true;
			GLCapabilities caps = GL.getCapabilities();
			supported = caps.glQueryCounter != 0 && caps.glGetQueryObjecti64v != 0
					&& GL33C.glGetQueryi(GL33C.GL_TIMESTAMP, GL33C.GL_QUERY_COUNTER_BITS) > 0;
			if (supported) {
				for (int[] q : instance.queries) {
					GL33C.glGenQueries(q);
				}
				LOG.info("CakeBench GPU timers on, sections down to depth {}", instance.maxDepth);
			} else {
				LOG.warn("CakeBench GPU timers: this driver has no timestamp queries; off");
			}
		}
		if (!supported) {
			return profiler;
		}
		instance.delegate = profiler;
		return instance;
	}

	/**
	 * Starts counting with the next frame. Also before {@link #wrap} has checked the driver (the first recording of a
	 * session): counting then starts once the check passes, and nothing happens if it fails.
	 */
	static void start() {
		if (instance != null && (supported || !checked)) {
			instance.totals.clear();
			instance.framesRead = 0;
			instance.framesLate = 0;
			instance.framesOverflowed = 0;
			instance.maxMarks = 0;
			instance.recording = true;
		}
	}

	/**
	 * Stops counting and returns per-frame averages: label -> {gpuMs, cpuMs}, largest GPU time first, plus the frame
	 * counts. Waits for the frames still in flight (this runs between scenes); the frame in progress is discarded.
	 */
	static Map<String, Object> stop() {
		if (instance == null || !supported) {
			return null;
		}
		GpuTimer t = instance;
		t.recording = false;
		t.inFrame = false;
		for (int k = 0; k < RING; k++) {
			int s = (t.oldest + k) % RING;
			if (t.pending[s]) {
				t.read(s); // QUERY_RESULT waits for the GPU
			}
		}
		t.oldest = t.slot;
		List<Map.Entry<String, long[]>> rows = new ArrayList<>(t.totals.entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		Map<String, Object> phases = new LinkedHashMap<>();
		int n = Math.max(1, t.framesRead);
		for (Map.Entry<String, long[]> e : rows) {
			Map<String, Object> v = new LinkedHashMap<>();
			v.put("gpuMs", round3(e.getValue()[0] / 1e6 / n));
			v.put("cpuMs", round3(e.getValue()[1] / 1e6 / n));
			phases.put(e.getKey(), v);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("framesRead", t.framesRead);
		out.put("framesDropped", t.framesLate + t.framesOverflowed);
		out.put("framesLate", t.framesLate); // results not back before the slot came round again
		out.put("framesOverflowed", t.framesOverflowed); // more than MAX_MARKS boundaries in one frame
		out.put("maxMarksPerFrame", t.maxMarks);
		out.put("depth", t.maxDepth);
		out.put("phases", phases);
		return out;
	}

	// --- frame bookkeeping ---------------------------------------------------------------------------------------

	private void beginFrame() {
		depth = 0;
		limit = maxDepth;
		if (!recording) {
			return;
		}
		if (pending[slot]) { // its results never arrived in time: don't stall the frame waiting for them
			if (available(slot)) {
				read(slot);
			} else {
				pending[slot] = false;
				framesLate++;
			}
			oldest = (slot + 1) % RING;
		}
		marks[slot] = 0;
		overflowed[slot] = false;
		inFrame = true;
		mark(null);
	}

	private void endFrame() {
		if (!inFrame) {
			return;
		}
		mark(current());
		inFrame = false;
		pending[slot] = true;
		slot = (slot + 1) % RING;
		// read back whatever has finished, oldest first (slots from oldest up to slot are the frames in flight)
		while (pending[oldest] && available(oldest)) {
			read(oldest);
			oldest = (oldest + 1) % RING;
		}
	}

	private void mark(String endedLabel) {
		if (!inFrame) {
			return;
		}
		int i = marks[slot];
		if (i == MAX_MARKS) {
			overflowed[slot] = true;
			return;
		}
		GL33C.glQueryCounter(queries[slot][i], GL33C.GL_TIMESTAMP);
		cpu[slot][i] = System.nanoTime();
		labels[slot][i] = endedLabel;
		marks[slot] = i + 1;
	}

	private boolean available(int s) {
		return marks[s] == 0 || GL33C.glGetQueryObjecti(queries[s][marks[s] - 1], GL33C.GL_QUERY_RESULT_AVAILABLE) != 0;
	}

	private void read(int s) {
		pending[s] = false;
		maxMarks = Math.max(maxMarks, overflowed[s] ? MAX_MARKS + 1 : marks[s]);
		if (overflowed[s] || marks[s] < 2) {
			framesOverflowed++;
			return;
		}
		long prev = GL33C.glGetQueryObjecti64(queries[s][0], GL33C.GL_QUERY_RESULT);
		for (int i = 1; i < marks[s]; i++) {
			long t = GL33C.glGetQueryObjecti64(queries[s][i], GL33C.GL_QUERY_RESULT);
			long[] sum = totals.computeIfAbsent(labels[s][i], k -> new long[2]);
			sum[0] += t - prev;
			sum[1] += cpu[s][i] - cpu[s][i - 1];
			prev = t;
		}
		framesRead++;
	}

	// --- section stack -------------------------------------------------------------------------------------------

	private String current() {
		return paths[Math.min(depth, limit)];
	}

	private String child(String parent, String name) {
		return children.computeIfAbsent(parent, k -> new HashMap<>())
				.computeIfAbsent(name, k -> parent == ROOT ? k : parent + "/" + k);
	}

	private void enter(String name) {
		String before = current();
		depth++;
		if (depth <= limit) {
			name(name);
		}
		boundary(before);
	}

	private void leave() {
		String before = current();
		if (depth > 0) {
			depth--;
		}
		boundary(before);
	}

	private void replace(String name) {
		String before = current();
		if (depth > 0 && depth <= limit) {
			name(name);
		}
		boundary(before);
	}

	private void name(String name) {
		paths[depth] = child(paths[depth - 1], name);
		if (depth == 1) {
			limit = TICK.equals(name) ? 1 : maxDepth;
		}
	}

	private void boundary(String before) {
		if (current() != before) { // paths are interned through child(), so identity is enough
			mark(before);
		}
	}

	private boolean named(int atDepth) {
		return inFrame && atDepth <= limit;
	}

	private static double round3(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

	// --- Profiler ------------------------------------------------------------------------------------------------

	@Override
	public void startTick() {
		delegate.startTick();
		beginFrame();
	}

	@Override
	public void endTick() {
		delegate.endTick();
		endFrame();
	}

	@Override
	public void push(String location) {
		delegate.push(location);
		if (inFrame) {
			enter(location);
		}
	}

	@Override
	public void push(Supplier<String> locationGetter) {
		delegate.push(locationGetter);
		if (inFrame) {
			enter(named(depth + 1) ? locationGetter.get() : null);
		}
	}

	@Override
	public void pop() {
		delegate.pop();
		if (inFrame) {
			leave();
		}
	}

	@Override
	public void swap(String location) {
		delegate.swap(location);
		if (inFrame) {
			replace(location);
		}
	}

	@Override
	public void swap(Supplier<String> locationGetter) {
		delegate.swap(locationGetter);
		if (inFrame) {
			replace(named(depth) ? locationGetter.get() : null);
		}
	}

	@Override
	public void markSampleType(SampleType type) {
		delegate.markSampleType(type);
	}

	@Override
	public void visit(String marker, int num) {
		delegate.visit(marker, num);
	}

	@Override
	public void visit(Supplier<String> markerGetter, int num) {
		delegate.visit(markerGetter, num);
	}
}
