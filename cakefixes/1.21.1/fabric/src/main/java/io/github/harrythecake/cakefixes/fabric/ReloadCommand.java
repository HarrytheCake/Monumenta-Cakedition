package io.github.harrythecake.cakefixes.fabric;

import io.github.harrythecake.cakefixes.ConfigReload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** /cakefixes reload: reads the config file again. Client-side, never sent to the server. Loaded only with Fabric API. */
final class ReloadCommand {
	private ReloadCommand() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				LiteralArgumentBuilder.<FabricClientCommandSource>literal("cakefixes").then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload").executes(ctx -> {
					ConfigReload.cakeconfighubReloadConfig();
					ctx.getSource().sendFeedback(Component.translatable("cakefixes.command.reloaded"));
					return 1;
				}))));
	}
}
