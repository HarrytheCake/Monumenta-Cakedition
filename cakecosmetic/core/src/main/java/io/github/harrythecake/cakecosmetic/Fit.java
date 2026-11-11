package io.github.harrythecake.cakecosmetic;

/** Image sizing math (pure). */
final class Fit {
	private Fit() {
	}

	/**
	 * The size to shrink an image to so it still covers a {@code screenW} x {@code screenH} window
	 * (never enlarged): {width, height}.
	 */
	static int[] shrinkToCover(int imageW, int imageH, int screenW, int screenH) {
		double s = Math.max((double) screenW / imageW, (double) screenH / imageH);
		if (s >= 1 || screenW <= 0 || screenH <= 0) {
			return new int[] {imageW, imageH};
		}
		return new int[] {Math.max(1, (int) Math.ceil(imageW * s)), Math.max(1, (int) Math.ceil(imageH * s))};
	}

	/**
	 * The part of an image that covers a box of the given aspect, centred: {u, v, width, height} in
	 * image pixels.
	 */
	static int[] coverRegion(int imageW, int imageH, double boxW, double boxH) {
		double box = boxW / boxH;
		double image = (double) imageW / imageH;
		if (image > box) {
			int w = Math.max(1, (int) Math.round(imageH * box));
			return new int[] {(imageW - w) / 2, 0, w, imageH};
		}
		int h = Math.max(1, (int) Math.round(imageW / box));
		return new int[] {0, (imageH - h) / 2, imageW, h};
	}
}
