package io.github.harrythecake.caketext.translator;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

/**
 * Starts Minecraft's registries for the tests. Since 1.21.2 items get their data components only when a world's
 * registries load (an {@code ItemStack} before that throws "Components not bound yet"), so the tests bind them from
 * the vanilla registries.
 */
final class TestGame {
	private TestGame() {
	}

	static synchronized void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
			BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
					.forEach(DataComponentInitializers.PendingComponents::apply);
		}
	}
}
