package io.github.harrythecake.cakefixes;

import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/**
 * Fix "appleSkinBlendQueryFix". AppleSkin's HUD overlays call GL11.glIsEnabled(GL_BLEND) every frame to remember
 * whether blending was already on. A glIsEnabled/glGet* call makes the CPU wait for the driver (with NVIDIA's
 * threaded optimisation it waits for the driver thread to catch up): 4.8% of the render thread in CakeBench
 * "dungeon2". Minecraft already tracks that state (GlStateManager's blend CapabilityTracker, which decides whether
 * RenderSystem.enableBlend() calls glEnable at all), so the answer is read from there instead.
 * Plain Java: the GL query and the tracked state come in as functions, so the tests can run it.
 */
public final class BlendQuery {
	public static final int GL_BLEND = 0x0BE2;

	private static long answered;

	private BlendQuery() {}

	/**
	 * @param tracked the tracked blend state, or null when it couldn't be found (then the real query runs)
	 * @param query   the real glIsEnabled
	 */
	public static boolean isEnabled(boolean fixOn, int cap, BooleanSupplier tracked, IntPredicate query) {
		if (!fixOn || cap != GL_BLEND || tracked == null) {
			return query.test(cap);
		}
		answered++;
		return tracked.getAsBoolean();
	}

	/** How many glIsEnabled calls were answered from the tracked state this session (render thread only). */
	public static long answered() {
		return answered;
	}
}
