package io.github.harrythecake.cakemap.core;

import java.util.List;

/**
 * Turns a region's columns into ARGB pixels: the block's colour (tinted by its biome), relief
 * from the height difference to the north and west, and water by depth. Unknown columns stay
 * transparent. Pure Java: the colours come from a {@link Colors}, which the game fills from block
 * textures.
 */
public final class RegionShader {
	public static final int TINT_NONE = 0;
	public static final int TINT_GRASS = 1;
	public static final int TINT_FOLIAGE = 2;
	public static final int TINT_WATER = 3;

	/** Where colours come from. Called once per palette entry per region, not per pixel. */
	public interface Colors {
		/** RGB of the block's top, before any biome tint. */
		int base(String state);

		/** One of the TINT_ constants. */
		int tint(String state);

		int grass(String biome);

		int foliage(String biome);

		int water(String biome);
	}

	private RegionShader() {
	}

	/** 512 x 512 ARGB pixels, row by row (index = z * 512 + x). */
	public static int[] shade(RegionData r, Colors colors) {
		List<String> states = r.states();
		List<String> biomes = r.biomes();
		int[] base = new int[states.size()];
		int[] tint = new int[states.size()];
		for (int k = 1; k < states.size(); k++) {
			base[k] = colors.base(states.get(k));
			tint[k] = colors.tint(states.get(k));
		}
		int[] grass = new int[biomes.size()];
		int[] foliage = new int[biomes.size()];
		int[] water = new int[biomes.size()];
		for (int k = 0; k < biomes.size(); k++) {
			grass[k] = colors.grass(biomes.get(k));
			foliage[k] = colors.foliage(biomes.get(k));
			water[k] = colors.water(biomes.get(k));
		}
		int[] out = new int[RegionData.AREA];
		for (int z = 0; z < RegionData.SIZE; z++) {
			for (int x = 0; x < RegionData.SIZE; x++) {
				int i = RegionData.index(x, z);
				int s = r.state[i];
				if (s == 0) {
					continue;
				}
				int b = r.biome[i] & 0xFF;
				int c = colour(base[s], tint[s], grass[b], foliage[b], water[b]);
				int h = r.height[i];
				int hn = z > 0 && r.state[i - RegionData.SIZE] != 0 ? r.height[i - RegionData.SIZE] : h;
				int hw = x > 0 && r.state[i - 1] != 0 ? r.height[i - 1] : h;
				c = scale(c, relief(h - hn + h - hw));
				int o = r.overlay[i];
				if (o != 0) {
					int oc = colour(base[o], tint[o], grass[b], foliage[b], water[b]);
					c = water(c, oc, r.depth[i] & 0xFF);
				}
				out[i] = 0xFF000000 | c;
			}
		}
		return out;
	}

	static int colour(int base, int tint, int grass, int foliage, int water) {
		return switch (tint) {
			case TINT_GRASS -> multiply(base, grass);
			case TINT_FOLIAGE -> multiply(base, foliage);
			case TINT_WATER -> multiply(base, water);
			default -> base;
		};
	}

	/** Brightness for a summed height difference to the north and west neighbours. */
	static double relief(int diff) {
		return Math.max(0.62, Math.min(1.3, 1 + diff * 0.07));
	}

	/** The ground shows through shallow water; deep water darkens. */
	static int water(int ground, int water, int depth) {
		double groundShare = Math.max(0, 0.42 - depth * 0.05);
		double dark = 1 - Math.min(depth, 12) * 0.025;
		int w = scale(water, dark);
		return mix(w, ground, groundShare);
	}

	public static int multiply(int a, int b) {
		int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
		int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
		int bl = (a & 0xFF) * (b & 0xFF) / 255;
		return (r << 16) | (g << 8) | bl;
	}

	public static int scale(int c, double f) {
		int r = Math.min(255, (int) (((c >> 16) & 0xFF) * f));
		int g = Math.min(255, (int) (((c >> 8) & 0xFF) * f));
		int b = Math.min(255, (int) ((c & 0xFF) * f));
		return (r << 16) | (g << 8) | b;
	}

	public static int mix(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** A quarter-size copy (each pixel = the average of the known pixels of a 4 x 4 block). */
	public static int[] quarter(int[] full, int size) {
		int q = size / 4;
		int[] out = new int[q * q];
		for (int z = 0; z < q; z++) {
			for (int x = 0; x < q; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int n = 0;
				for (int dz = 0; dz < 4; dz++) {
					for (int dx = 0; dx < 4; dx++) {
						int c = full[(z * 4 + dz) * size + x * 4 + dx];
						if ((c >>> 24) != 0) {
							r += (c >> 16) & 0xFF;
							g += (c >> 8) & 0xFF;
							b += c & 0xFF;
							n++;
						}
					}
				}
				if (n > 0) {
					out[z * q + x] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
				}
			}
		}
		return out;
	}

	/** Heights every 8 blocks (64 x 64), Short.MIN_VALUE where unknown: the static map's match samples. */
	public static short[] samples(RegionData r) {
		short[] out = new short[64 * 64];
		for (int z = 0; z < 64; z++) {
			for (int x = 0; x < 64; x++) {
				int i = RegionData.index(x * 8 + 4, z * 8 + 4);
				out[z * 64 + x] = r.state[i] == 0 ? Short.MIN_VALUE : r.height[i];
			}
		}
		return out;
	}
}
