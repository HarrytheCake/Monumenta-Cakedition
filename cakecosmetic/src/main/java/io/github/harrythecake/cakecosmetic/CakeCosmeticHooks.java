package io.github.harrythecake.cakecosmetic;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;

import java.util.Map;

/** The public door for mixins (the rest of the mod is package-private). */
public final class CakeCosmeticHooks {
	private CakeCosmeticHooks() {
	}

	public static void advancements(Map<AdvancementEntry, AdvancementProgress> progress) {
		try {
			Unlocks.onAdvancements(progress);
		} catch (RuntimeException e) {
			CakeCosmetic.LOG.error("CakeCosmetic: checking unlocks failed", e);
		}
	}
}
