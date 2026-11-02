package io.github.harrythecake.cakefixes;

import java.util.List;

/**
 * Option "bannerDrawDistance" (off by default: it changes what you see). Banners are drawn by a block entity
 * renderer every frame, one model pass per pattern layer: 2.8-3.7% of the render thread in CakeBench "dungeon1" and
 * "dungeon2", the costliest block entity type there. They are decoration, so the pack may trade them for FPS
 * (cosmetic costs may be off by default or optional). Banners farther than the chosen distance aren't drawn.
 * Plain Java so the tests can run it.
 */
public final class BannerDistance {
	/** Vanilla's block entity draw distance (BlockEntityRenderer.getRenderDistance). */
	public static final int VANILLA = 64;
	/** The choices the Mod Menu button cycles through; 0 = off (vanilla). */
	public static final List<Integer> CHOICES = List.of(0, 16, 24, 32, 48);

	private static long hidden;

	private BannerDistance() {}

	/** The distance actually used: the setting, or vanilla's 64 when it's off (0 or less) or not shorter. */
	public static int effective(int setting) {
		return setting <= 0 || setting >= VANILLA ? VANILLA : setting;
	}

	/** Same test as vanilla's isInRenderDistance (squared distance from the block's centre, strictly less). */
	public static boolean visible(int setting, double squaredDistance) {
		int d = effective(setting);
		boolean visible = squaredDistance < (double) d * d;
		if (!visible && d < VANILLA && squaredDistance < (double) VANILLA * VANILLA) {
			hidden++; // render thread only
		}
		return visible;
	}

	/** Banner draws skipped this session that vanilla would have drawn. */
	public static long hidden() {
		return hidden;
	}
}
