package io.github.harrythecake.cakecosmetic;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;

/** The public door for mixins (the rest of the mod is package-private). */
public final class CakeCosmeticHooks {
	private CakeCosmeticHooks() {
	}

	public static void advancements(Map<AdvancementHolder, AdvancementProgress> progress) {
		try {
			Unlocks.onAdvancements(progress);
		} catch (RuntimeException e) {
			CakeCosmetic.LOG.error("CakeCosmetic: checking unlocks failed", e);
		}
	}
}
