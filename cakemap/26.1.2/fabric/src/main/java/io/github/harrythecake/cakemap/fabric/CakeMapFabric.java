package io.github.harrythecake.cakemap.fabric;

import io.github.harrythecake.cakemap.CakeMap;
import io.github.harrythecake.cakemap.CakeMapCommands;
import io.github.harrythecake.cakemap.ChatLinks;
import io.github.harrythecake.cakemap.Scanner;
import io.github.harrythecake.cakemap.StaticMaps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

/**
 * Fabric entry: CakeMap's hooks on Fabric API's events. The CakeUI plugin is the "cakeui" entrypoint.
 * The camera for the in-world markers comes from the common GameRendererMixin on 26.1.
 */
public final class CakeMapFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeMap.init();
		CakeMap.KEYS.values().forEach(KeyMappingHelper::registerKeyMapping);
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(StaticMaps.ID, new StaticMaps());
		ClientTickEvents.END_CLIENT_TICK.register(CakeMap::endClientTick);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> Scanner.queue(chunk.getPos()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(CakeMap::endSession));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CakeMap.endSession());
		ClientReceiveMessageEvents.MODIFY_GAME.register(ChatLinks::modify);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				CakeMapCommands.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback, FabricClientCommandSource::sendError)));
	}
}
