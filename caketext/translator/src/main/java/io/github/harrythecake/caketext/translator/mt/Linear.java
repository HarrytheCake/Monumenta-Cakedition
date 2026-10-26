package io.github.harrythecake.caketext.translator.mt;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.stream.IntStream;

/**
 * A weight matrix stored row per output ({@code out × in}), either int8 with one scale per row or float32,
 * plus an optional bias. {@code y = W·x + b} for a batch of vectors.
 */
final class Linear {
	/** Below this many multiply-adds a call runs on the calling thread; splitting costs more than it saves. */
	private static final long PARALLEL_MIN = 1L << 17;

	/** Worker threads for the model: at most half the cores (max 4), low priority, so the game keeps its CPU. */
	static final ForkJoinPool POOL = new ForkJoinPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
			pool -> {
				ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
				t.setName("CakeText-MT-" + t.getPoolIndex());
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				return t;
			}, null, false);

	final int out;
	final int in;
	final byte[] q;
	final float[] scale;
	final float[] w;
	final float[] bias;

	private Linear(int out, int in, byte[] q, float[] scale, float[] w, float[] bias) {
		this.out = out;
		this.in = in;
		this.q = q;
		this.scale = scale;
		this.w = w;
		this.bias = bias;
	}

	static Linear int8(int out, int in, byte[] q, float[] scale, float[] bias) {
		return new Linear(out, in, q, scale, null, bias);
	}

	static Linear f32(int out, int in, float[] w, float[] bias) {
		return new Linear(out, in, null, null, w, bias);
	}

	/**
	 * Int8 weights also quantize each input vector to int8 (one scale per vector): the JIT vectorizes integer
	 * dot products but not float ones, so this is 3-4x faster, but translations drift (e.g. "Back" became
	 * "后退( E)" in tests). Used by {@link OpusMt.Quality#FAST} only.
	 */
	static volatile boolean quantizeInputs = false;

	/** Keep float inputs for this matrix even when {@link #quantizeInputs} is on (accuracy-sensitive layers). */
	boolean precise;

	/** {@code x}: n vectors of {@code in}; {@code y}: n vectors of {@code out}. */
	void apply(float[] x, int n, float[] y) {
		byte[] xq = null;
		float[] xs = null;
		if (q != null && quantizeInputs && !precise) {
			xq = new byte[n * in];
			xs = new float[n];
			for (int v = 0; v < n; v++) {
				float max = 0;
				for (int i = 0; i < in; i++) {
					max = Math.max(max, Math.abs(x[v * in + i]));
				}
				float s = max == 0 ? 1f : max / 127f;
				xs[v] = s;
				float inv = 1f / s;
				for (int i = 0; i < in; i++) {
					xq[v * in + i] = (byte) Math.round(x[v * in + i] * inv);
				}
			}
		}
		byte[] fxq = xq;
		float[] fxs = xs;
		if ((long) out * in * n < PARALLEL_MIN || POOL.getParallelism() == 1) {
			range(x, fxq, fxs, n, y, 0, out);
			return;
		}
		int chunks = Math.min(POOL.getParallelism() * 4, Math.max(1, out / 32));
		int per = (out + chunks - 1) / chunks;
		POOL.submit(() -> IntStream.range(0, chunks).parallel()
				.forEach(c -> range(x, fxq, fxs, n, y, c * per, Math.min(out, (c + 1) * per)))).join();
	}

	private void range(float[] x, byte[] xq, float[] xs, int n, float[] y, int from, int to) {
		for (int r = from; r < to; r++) {
			float b = bias == null ? 0f : bias[r];
			int row = r * in;
			if (xq != null) {
				float s = scale[r];
				for (int v = 0; v < n; v++) {
					y[v * out + r] = dot(q, row, xq, v * in, in) * (s * xs[v]) + b;
				}
			} else if (q != null) {
				float s = scale[r];
				for (int v = 0; v < n; v++) {
					y[v * out + r] = dot(q, row, x, v * in, in) * s + b;
				}
			} else {
				for (int v = 0; v < n; v++) {
					y[v * out + r] = dot(w, row, x, v * in, in) + b;
				}
			}
		}
	}

	/** Row {@code r} as floats (used for the embedding lookup). */
	void row(int r, float[] dst, int off, float mul) {
		int row = r * in;
		if (q != null) {
			float s = scale[r] * mul;
			for (int i = 0; i < in; i++) {
				dst[off + i] = q[row + i] * s;
			}
		} else {
			for (int i = 0; i < in; i++) {
				dst[off + i] = w[row + i] * mul;
			}
		}
	}

	/** Plain loop on purpose: this shape is what the JIT turns into vector instructions. */
	static int dot(byte[] a, int ao, byte[] b, int bo, int n) {
		int s = 0;
		for (int i = 0; i < n; i++) {
			s += a[ao + i] * b[bo + i];
		}
		return s;
	}

	static float dot(byte[] a, int ao, float[] b, int bo, int n) {
		float s0 = 0;
		float s1 = 0;
		float s2 = 0;
		float s3 = 0;
		int i = 0;
		for (; i + 3 < n; i += 4) {
			s0 += a[ao + i] * b[bo + i];
			s1 += a[ao + i + 1] * b[bo + i + 1];
			s2 += a[ao + i + 2] * b[bo + i + 2];
			s3 += a[ao + i + 3] * b[bo + i + 3];
		}
		for (; i < n; i++) {
			s0 += a[ao + i] * b[bo + i];
		}
		return (s0 + s1) + (s2 + s3);
	}

	static float dot(float[] a, int ao, float[] b, int bo, int n) {
		float s0 = 0;
		float s1 = 0;
		float s2 = 0;
		float s3 = 0;
		int i = 0;
		for (; i + 3 < n; i += 4) {
			s0 += a[ao + i] * b[bo + i];
			s1 += a[ao + i + 1] * b[bo + i + 1];
			s2 += a[ao + i + 2] * b[bo + i + 2];
			s3 += a[ao + i + 3] * b[bo + i + 3];
		}
		for (; i < n; i++) {
			s0 += a[ao + i] * b[bo + i];
		}
		return (s0 + s1) + (s2 + s3);
	}

	long bytes() {
		long b = q != null ? q.length + 4L * scale.length : 4L * w.length;
		return b + (bias == null ? 0 : 4L * bias.length);
	}
}
