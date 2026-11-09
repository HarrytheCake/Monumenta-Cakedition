package io.github.harrythecake.cakemap.core;

/**
 * Does the static map of a shard match the world the player is in? Columns the player has seen
 * are compared with the static map's height samples. Until enough samples are in, the static map
 * is trusted; then it is shown only while most samples agree (an instanced copy of a shard at
 * other coordinates, or a changed build, fails). Pure Java.
 */
public final class MatchCheck {
	/** Samples needed before the result counts. */
	public static final int MIN_SAMPLES = 48;
	/** Share of samples that must agree. */
	public static final double MIN_SHARE = 0.6;
	/** Heights this close agree (blocks). */
	public static final int TOLERANCE = 2;

	private int agree;
	private int total;

	public void reset() {
		agree = 0;
		total = 0;
	}

	/** One seen column against the static map's height there (Integer.MIN_VALUE = the static map has no sample). */
	public void sample(int seenHeight, int staticHeight) {
		if (staticHeight == Integer.MIN_VALUE) {
			return;
		}
		total++;
		if (Math.abs(seenHeight - staticHeight) <= TOLERANCE) {
			agree++;
		}
	}

	public boolean decided() {
		return total >= MIN_SAMPLES;
	}

	/** True until decided; then whether enough samples agree. */
	public boolean matches() {
		return !decided() || agree >= MIN_SHARE * total;
	}

	public int agree() {
		return agree;
	}

	public int total() {
		return total;
	}
}
