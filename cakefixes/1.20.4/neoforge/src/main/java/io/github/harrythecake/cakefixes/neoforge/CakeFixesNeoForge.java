package io.github.harrythecake.cakefixes.neoforge;

import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;

/** NeoForge entry: only the config screen (Mods list → Config); the mixins read the config on first use. */
@Mod(CakeFixes.MOD_ID)
public final class CakeFixesNeoForge {
	public CakeFixesNeoForge() {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
		}
	}
}
