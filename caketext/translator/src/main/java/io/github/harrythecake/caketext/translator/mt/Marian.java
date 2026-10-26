package io.github.harrythecake.caketext.translator.mt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * MarianMT (the opus-mt architecture) in plain Java: post-norm transformer encoder-decoder, swish feed-forward,
 * sinusoidal positions, scaled and tied embeddings. Mirrors Hugging Face {@code MarianMTModel} and its greedy and
 * beam search, so results match the reference (exactly with float32 weights, nearly with int8).
 */
final class Marian {
	private static final float LN_EPS = 1e-5f;

	private record Norm(float[] g, float[] b) {
	}

	private record Attn(Linear q, Linear k, Linear v, Linear o) {
	}

	private record EncLayer(Attn self, Norm ln1, Linear fc1, Linear fc2, Norm ln2) {
	}

	private record DecLayer(Attn self, Norm ln1, Attn cross, Norm ln2, Linear fc1, Linear fc2, Norm ln3) {
	}

	final int d;
	final int heads;
	final int vocabSize;
	final int pad;
	final int eos;
	final int start;
	final int defaultBeams;
	private final int maxPositions;
	private final float embedScale;
	private final Linear embed;
	private final float[] finalBias;
	private final EncLayer[] enc;
	private final DecLayer[] dec;
	private final float[] positions;
	private final long bytes;

	/** Which matrices keep float inputs in FAST mode (see {@link Linear#precise}); tests showed no gain, so off. */
	static boolean preciseLogits = false;
	static boolean preciseFc2 = false;

	Marian(ModelFile file) {
		JsonObject c = file.config;
		d = c.get("d_model").getAsInt();
		heads = c.get("heads").getAsInt();
		vocabSize = c.get("vocab_size").getAsInt();
		pad = c.get("pad").getAsInt();
		eos = c.get("eos").getAsInt();
		start = c.get("decoder_start").getAsInt();
		defaultBeams = c.get("num_beams").getAsInt();
		maxPositions = c.get("max_positions").getAsInt();
		embedScale = c.get("scale_embedding").getAsBoolean() ? (float) Math.sqrt(d) : 1f;
		var t = file.tensors;
		embed = linear(t.get("embed"), null);
		finalBias = t.get("final_logits_bias").f();
		enc = new EncLayer[c.get("encoder_layers").getAsInt()];
		for (int i = 0; i < enc.length; i++) {
			String p = "enc." + i + ".";
			enc[i] = new EncLayer(attn(file, p), norm(file, p + "ln1"), dense(file, p + "fc1"), dense(file, p + "fc2"),
					norm(file, p + "ln2"));
		}
		dec = new DecLayer[c.get("decoder_layers").getAsInt()];
		for (int i = 0; i < dec.length; i++) {
			String p = "dec." + i + ".";
			dec[i] = new DecLayer(attn(file, p), norm(file, p + "ln1"), attn(file, p + "x"), norm(file, p + "ln2"),
					dense(file, p + "fc1"), dense(file, p + "fc2"), norm(file, p + "ln3"));
		}
		embed.precise = preciseLogits;
		for (EncLayer l : enc) {
			l.fc2.precise = preciseFc2;
		}
		for (DecLayer l : dec) {
			l.fc2.precise = preciseFc2;
		}
		positions = sinusoids(maxPositions, d);
		long b = embed.bytes() + 4L * finalBias.length + 4L * positions.length;
		for (EncLayer l : enc) {
			b += l.self.q.bytes() * 4 + l.fc1.bytes() + l.fc2.bytes();
		}
		for (DecLayer l : dec) {
			b += l.self.q.bytes() * 8 + l.fc1.bytes() + l.fc2.bytes();
		}
		bytes = b;
	}

	/** Approximate weight memory. */
	long bytes() {
		return bytes;
	}

	private static Linear linear(ModelFile.Tensor w, float[] bias) {
		return w.q() != null ? Linear.int8(w.rows(), w.cols(), w.q(), w.scale(), bias)
				: Linear.f32(w.rows(), w.cols(), w.f(), bias);
	}

	private static Linear dense(ModelFile file, String name) {
		return linear(file.tensors.get(name), file.tensors.get(name + ".b").f());
	}

	private static Attn attn(ModelFile file, String p) {
		return new Attn(dense(file, p + "q"), dense(file, p + "k"), dense(file, p + "v"), dense(file, p + "o"));
	}

	private static Norm norm(ModelFile file, String name) {
		return new Norm(file.tensors.get(name + ".g").f(), file.tensors.get(name + ".b").f());
	}

	/** Hugging Face MarianSinusoidalPositionalEmbedding: sin in the first half, cos in the second. */
	private static float[] sinusoids(int n, int dim) {
		float[] out = new float[n * dim];
		int half = dim / 2;
		for (int pos = 0; pos < n; pos++) {
			for (int j = 0; j < half; j++) {
				double angle = pos / Math.pow(10000, 2.0 * j / dim);
				out[pos * dim + j] = (float) Math.sin(angle);
				out[pos * dim + half + j] = (float) Math.cos(angle);
			}
		}
		return out;
	}

	// ---- encoder ----

	/** Encoder output, {@code ids.length × d}. */
	float[] encode(int[] ids) {
		int n = Math.min(ids.length, maxPositions);
		float[] x = new float[n * d];
		for (int t = 0; t < n; t++) {
			embed.row(ids[t], x, t * d, embedScale);
			for (int i = 0; i < d; i++) {
				x[t * d + i] += positions[t * d + i];
			}
		}
		float[] q = new float[n * d];
		float[] k = new float[n * d];
		float[] v = new float[n * d];
		float[] a = new float[n * d];
		float[] o = new float[n * d];
		for (EncLayer l : enc) {
			l.self.q.apply(x, n, q);
			l.self.k.apply(x, n, k);
			l.self.v.apply(x, n, v);
			scale(q, 1f / (float) Math.sqrt((double) d / heads));
			for (int t = 0; t < n; t++) {
				attend(q, t * d, k, v, n, a, t * d);
			}
			l.self.o.apply(a, n, o);
			addNorm(x, o, n, l.ln1);
			feedForward(x, n, l.fc1, l.fc2, o);
			addNorm(x, o, n, l.ln2);
		}
		return x;
	}

	// ---- decoder ----

	/** Per-beam self-attention cache plus the shared cross-attention keys and values. */
	final class State {
		final int srcLen;
		final int maxLen;
		final float[][] crossK = new float[dec.length][];
		final float[][] crossV = new float[dec.length][];
		float[][][] selfK;
		float[][][] selfV;
		int beams;
		int len;

		State(float[] encoded, int srcLen, int beams, int maxLen) {
			this.srcLen = srcLen;
			this.maxLen = Math.min(maxLen, maxPositions);
			this.beams = beams;
			for (int i = 0; i < dec.length; i++) {
				crossK[i] = new float[srcLen * d];
				crossV[i] = new float[srcLen * d];
				dec[i].cross.k.apply(encoded, srcLen, crossK[i]);
				dec[i].cross.v.apply(encoded, srcLen, crossV[i]);
			}
			selfK = new float[dec.length][beams][this.maxLen * d];
			selfV = new float[dec.length][beams][this.maxLen * d];
		}

		/** Beam {@code b} continues from old beam {@code parents[b]}. */
		void reorder(int[] parents) {
			int n = len * d;
			for (int l = 0; l < dec.length; l++) {
				float[][] k = new float[parents.length][];
				float[][] v = new float[parents.length][];
				for (int b = 0; b < parents.length; b++) {
					k[b] = new float[maxLen * d];
					v[b] = new float[maxLen * d];
					System.arraycopy(selfK[l][parents[b]], 0, k[b], 0, n);
					System.arraycopy(selfV[l][parents[b]], 0, v[b], 0, n);
				}
				selfK[l] = k;
				selfV[l] = v;
			}
			beams = parents.length;
		}
	}

	State start(float[] encoded, int srcLen, int beams, int maxLen) {
		return new State(encoded, srcLen, beams, maxLen + 1);
	}

	/** One decoder step for every beam: feeds {@code tokens[b]}, returns logits ({@code beams × vocab}). */
	float[] step(State s, int[] tokens) {
		int n = s.beams;
		int pos = s.len;
		float[] y = new float[n * d];
		for (int b = 0; b < n; b++) {
			embed.row(tokens[b], y, b * d, embedScale);
			for (int i = 0; i < d; i++) {
				y[b * d + i] += positions[pos * d + i];
			}
		}
		float[] q = new float[n * d];
		float[] k = new float[n * d];
		float[] v = new float[n * d];
		float[] a = new float[n * d];
		float[] o = new float[n * d];
		float qScale = 1f / (float) Math.sqrt((double) d / heads);
		for (int li = 0; li < dec.length; li++) {
			DecLayer l = dec[li];
			l.self.q.apply(y, n, q);
			l.self.k.apply(y, n, k);
			l.self.v.apply(y, n, v);
			scale(q, qScale);
			for (int b = 0; b < n; b++) {
				System.arraycopy(k, b * d, s.selfK[li][b], pos * d, d);
				System.arraycopy(v, b * d, s.selfV[li][b], pos * d, d);
				attend(q, b * d, s.selfK[li][b], s.selfV[li][b], pos + 1, a, b * d);
			}
			l.self.o.apply(a, n, o);
			addNorm(y, o, n, l.ln1);

			l.cross.q.apply(y, n, q);
			scale(q, qScale);
			for (int b = 0; b < n; b++) {
				attend(q, b * d, s.crossK[li], s.crossV[li], s.srcLen, a, b * d);
			}
			l.cross.o.apply(a, n, o);
			addNorm(y, o, n, l.ln2);

			feedForward(y, n, l.fc1, l.fc2, o);
			addNorm(y, o, n, l.ln3);
		}
		s.len++;
		float[] logits = new float[n * vocabSize];
		embed.apply(y, n, logits);
		for (int b = 0; b < n; b++) {
			for (int i = 0; i < vocabSize; i++) {
				logits[b * vocabSize + i] += finalBias[i];
			}
		}
		return logits;
	}

	// ---- search ----

	/** Greedy decoding; result excludes the start token and eos. */
	int[] greedy(int[] src, int maxNew) {
		float[] encoded = encode(src);
		State s = start(encoded, Math.min(src.length, maxPositions), 1, maxNew);
		List<Integer> out = new ArrayList<>();
		int token = start;
		for (int i = 0; i < maxNew && s.len < s.maxLen; i++) {
			float[] logits = step(s, new int[] {token});
			logits[pad] = Float.NEGATIVE_INFINITY;
			token = argmax(logits, 0, vocabSize);
			if (token == eos) {
				break;
			}
			out.add(token);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private record Hyp(float score, int[] tokens) {
	}

	/**
	 * Beam search as in Hugging Face {@code generate(num_beams=N)} with length penalty 1, early_stopping false,
	 * no bad words (pad) and renormalized log-probabilities.
	 */
	int[] beam(int[] src, int numBeams, int maxNew) {
		if (numBeams <= 1) {
			return greedy(src, maxNew);
		}
		float[] encoded = encode(src);
		State s = start(encoded, Math.min(src.length, maxPositions), 1, maxNew);
		List<int[]> seqs = new ArrayList<>(List.of(new int[] {start}));
		float[] beamScores = {0f};
		List<Hyp> done = new ArrayList<>();
		float worst = 1e9f;
		boolean finished = false;
		int keep = 2 * numBeams;
		while (!finished) {
			int curLen = seqs.get(0).length;
			int[] last = new int[seqs.size()];
			for (int b = 0; b < last.length; b++) {
				int[] seq = seqs.get(b);
				last[b] = seq[seq.length - 1];
			}
			float[] logits = step(s, last);
			int n = last.length;
			// log_softmax, pad -> -inf, log_softmax again (renormalize), plus the beam's score
			float[] scores = new float[n * vocabSize];
			for (int b = 0; b < n; b++) {
				int off = b * vocabSize;
				logSoftmax(logits, off, vocabSize, scores);
				scores[off + pad] = Float.NEGATIVE_INFINITY;
				logSoftmax(scores, off, vocabSize, scores);
				for (int i = 0; i < vocabSize; i++) {
					scores[off + i] += beamScores[b];
				}
			}
			int[] top = topK(scores, keep);
			// HF numbers generated_len from the sequence length including the token being added
			int processLen = curLen + 1;
			List<int[]> nextSeqs = new ArrayList<>();
			List<Float> nextScores = new ArrayList<>();
			List<Integer> parents = new ArrayList<>();
			for (int rank = 0; rank < top.length; rank++) {
				int idx = top[rank];
				int beamIdx = idx / vocabSize;
				int token = idx % vocabSize;
				float score = scores[idx];
				if (token == eos) {
					if (rank >= numBeams) {
						continue;
					}
					float norm = score / (processLen - 1);
					if (done.size() < numBeams || norm > worst) {
						done.add(new Hyp(norm, seqs.get(beamIdx)));
						if (done.size() > numBeams) {
							int worstIdx = 0;
							for (int i = 1; i < done.size(); i++) {
								if (done.get(i).score < done.get(worstIdx).score) {
									worstIdx = i;
								}
							}
							done.remove(worstIdx);
							worst = Float.MAX_VALUE;
							for (Hyp h : done) {
								worst = Math.min(worst, h.score);
							}
						} else {
							worst = Math.min(worst, norm);
						}
					}
				} else {
					int[] seq = Arrays.copyOf(seqs.get(beamIdx), curLen + 1);
					seq[curLen] = token;
					nextSeqs.add(seq);
					nextScores.add(score);
					parents.add(beamIdx);
				}
				if (nextSeqs.size() == numBeams) {
					break;
				}
			}
			float best = scores[top[0]];
			if (done.size() >= numBeams && worst >= best / (processLen - 1)) {
				finished = true;
			}
			seqs = nextSeqs;
			beamScores = new float[nextScores.size()];
			for (int i = 0; i < beamScores.length; i++) {
				beamScores[i] = nextScores.get(i);
			}
			s.reorder(parents.stream().mapToInt(Integer::intValue).toArray());
			if (curLen + 1 >= maxNew + 1 || s.len >= s.maxLen) {
				break;
			}
		}
		if (!finished) {
			for (int b = 0; b < seqs.size(); b++) {
				int[] seq = seqs.get(b);
				float norm = beamScores[b] / (seq.length - 1);
				if (done.size() < numBeams || norm > worst) {
					done.add(new Hyp(norm, seq));
					if (done.size() > numBeams) {
						int worstIdx = 0;
						for (int i = 1; i < done.size(); i++) {
							if (done.get(i).score < done.get(worstIdx).score) {
								worstIdx = i;
							}
						}
						done.remove(worstIdx);
						worst = Float.MAX_VALUE;
						for (Hyp h : done) {
							worst = Math.min(worst, h.score);
						}
					} else {
						worst = Math.min(worst, norm);
					}
				}
			}
		}
		Hyp best = null;
		for (Hyp h : done) {
			// like Python's sorted(...).pop(): on a tie the later one wins
			if (best == null || h.score >= best.score) {
				best = h;
			}
		}
		int[] seq = best == null ? new int[] {start} : best.tokens;
		return Arrays.copyOfRange(seq, 1, seq.length);
	}

	// ---- math ----

	/** Attention of one query row (all heads) over {@code kvLen} key/value rows. */
	private void attend(float[] q, int qOff, float[] k, float[] v, int kvLen, float[] out, int outOff) {
		int hd = d / heads;
		float[] w = new float[kvLen];
		for (int h = 0; h < heads; h++) {
			int ho = h * hd;
			float max = Float.NEGATIVE_INFINITY;
			for (int j = 0; j < kvLen; j++) {
				float sc = Linear.dot(q, qOff + ho, k, j * d + ho, hd);
				w[j] = sc;
				max = Math.max(max, sc);
			}
			float sum = 0;
			for (int j = 0; j < kvLen; j++) {
				w[j] = (float) Math.exp(w[j] - max);
				sum += w[j];
			}
			for (int i = 0; i < hd; i++) {
				out[outOff + ho + i] = 0;
			}
			for (int j = 0; j < kvLen; j++) {
				float p = w[j] / sum;
				int vo = j * d + ho;
				for (int i = 0; i < hd; i++) {
					out[outOff + ho + i] += p * v[vo + i];
				}
			}
		}
	}

	/** x = LayerNorm(x + o), row by row. */
	private void addNorm(float[] x, float[] o, int n, Norm norm) {
		for (int r = 0; r < n; r++) {
			int off = r * d;
			float mean = 0;
			for (int i = 0; i < d; i++) {
				x[off + i] += o[off + i];
				mean += x[off + i];
			}
			mean /= d;
			float var = 0;
			for (int i = 0; i < d; i++) {
				float c = x[off + i] - mean;
				var += c * c;
			}
			float inv = (float) (1.0 / Math.sqrt(var / d + LN_EPS));
			for (int i = 0; i < d; i++) {
				x[off + i] = (x[off + i] - mean) * inv * norm.g[i] + norm.b[i];
			}
		}
	}

	/** out = fc2(swish(fc1(x))) */
	private static void feedForward(float[] x, int n, Linear fc1, Linear fc2, float[] out) {
		float[] h = new float[n * fc1.out];
		fc1.apply(x, n, h);
		for (int i = 0; i < h.length; i++) {
			h[i] = h[i] / (1f + (float) Math.exp(-h[i]));
		}
		fc2.apply(h, n, out);
	}

	private static void scale(float[] x, float s) {
		for (int i = 0; i < x.length; i++) {
			x[i] *= s;
		}
	}

	private static void logSoftmax(float[] in, int off, int n, float[] out) {
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			max = Math.max(max, in[off + i]);
		}
		double sum = 0;
		for (int i = 0; i < n; i++) {
			sum += Math.exp(in[off + i] - max);
		}
		float lse = max + (float) Math.log(sum);
		for (int i = 0; i < n; i++) {
			out[off + i] = in[off + i] - lse;
		}
	}

	private static int argmax(float[] x, int off, int n) {
		int best = 0;
		for (int i = 1; i < n; i++) {
			if (x[off + i] > x[off + best]) {
				best = i;
			}
		}
		return best;
	}

	/** Indices of the k largest values, largest first. */
	private static int[] topK(float[] x, int k) {
		int[] idx = new int[k];
		float[] val = new float[k];
		Arrays.fill(val, Float.NEGATIVE_INFINITY);
		Arrays.fill(idx, -1);
		for (int i = 0; i < x.length; i++) {
			float v = x[i];
			if (v <= val[k - 1]) {
				continue;
			}
			int j = k - 1;
			while (j > 0 && val[j - 1] < v) {
				val[j] = val[j - 1];
				idx[j] = idx[j - 1];
				j--;
			}
			val[j] = v;
			idx[j] = i;
		}
		return idx;
	}
}
