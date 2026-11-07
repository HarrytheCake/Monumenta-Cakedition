package io.github.harrythecake.cakechat.fabric;

import io.github.harrythecake.cakechat.CakeChat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Fabric client entrypoint: config, the peek and hide keys, ticks, joins and the {@code /cakechat} command. */
public final class CakeChatFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeChat.init();
		KeyBindingHelper.registerKeyBinding(CakeChat.PEEK);
		KeyBindingHelper.registerKeyBinding(CakeChat.HIDE);
		ClientTickEvents.END_CLIENT_TICK.register(CakeChat::clientTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CakeChat.joined(client));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				CakeChat.<FabricClientCommandSource>command(ClientCommandManager::literal, FabricClientCommandSource::sendFeedback)));
	}
}
