package io.github.harrythecake.cakefixes.neoforge;

import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge entry: the config screen (Mods list → Config) and /cakefixes reload; the mixins read the config on first use. */
@Mod(value = CakeFixes.MOD_ID, dist = Dist.CLIENT)
public final class CakeFixesNeoForge {
	public CakeFixesNeoForge(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new ConfigScreen(parent));
		// /cakefixes reload: reads the config file again (client-side, never sent to the server)
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class,
				event -> event.getDispatcher().register(net.minecraft.commands.Commands.literal("cakefixes")
						.then(net.minecraft.commands.Commands.literal("reload").executes(ctx -> {
							io.github.harrythecake.cakefixes.ConfigReload.cakeuiReloadConfig();
							ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("cakefixes.command.reloaded"), false);
							return 1;
						}))));
	}
}
