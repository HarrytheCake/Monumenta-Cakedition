package io.github.harrythecake.cakechat.fabric;

import io.github.harrythecake.cakechat.CakeChat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;

/** Fabric client entrypoint: config, the peek and hide keys, ticks, joins and the {@code /cakechat} command. */
public final class CakeChatFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeChat.init();
		CakeChat.createKeys(KeyMapping.Category.register(CakeChat.KEY_CATEGORY));
		KeyMappingHelper.registerKeyMapping(CakeChat.PEEK);
		KeyMappingHelper.registerKeyMapping(CakeChat.HIDE);
		ClientTickEvents.END_CLIENT_TICK.register(CakeChat::clientTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CakeChat.joined(client));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				CakeChat.<FabricClientCommandSource>command(ClientCommands::literal, FabricClientCommandSource::sendFeedback)));
	}
}
