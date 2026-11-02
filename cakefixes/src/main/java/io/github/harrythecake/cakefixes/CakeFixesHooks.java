package io.github.harrythecake.cakefixes;

import java.util.function.IntPredicate;

/** The public calls the mixins make; keeps the helpers they use package-private. */
public final class CakeFixesHooks {
	private CakeFixesHooks() {}

	/** appleSkinBlendQueryFix: glIsEnabled answered from Minecraft's tracked state when possible. */
	public static boolean isEnabled(boolean fixOn, int cap, IntPredicate query) {
		return BlendQuery.isEnabled(fixOn, cap, fixOn ? TrackedBlend.SUPPLIER : null, query);
	}
}
