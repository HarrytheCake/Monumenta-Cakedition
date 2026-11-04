package io.github.harrythecake.cakefixes.neoforge;

import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge entry: only the config screen (Mods list → Config); the mixins read the config on first use. */
@Mod(value = CakeFixes.MOD_ID, dist = Dist.CLIENT)
public final class CakeFixesNeoForge {
	public CakeFixesNeoForge(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new ConfigScreen(parent));
	}
}
