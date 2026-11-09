package io.github.harrythecake.cakemap.fabric;

import io.github.harrythecake.cakemap.CakeMap;
import io.github.harrythecake.cakemap.CakeMapCommands;
import io.github.harrythecake.cakemap.ChatLinks;
import io.github.harrythecake.cakemap.InWorldWaypoints;
import io.github.harrythecake.cakemap.Scanner;
import io.github.harrythecake.cakemap.StaticMaps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/** Fabric entry: CakeMap's hooks on Fabric API's events. The CakeUI plugin is the "cakeui" entrypoint. */
public final class CakeMapFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeMap.init();
		CakeMap.KEYS.values().forEach(KeyBindingHelper::registerKeyBinding);
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricStaticMaps());
		ClientTickEvents.END_CLIENT_TICK.register(CakeMap::endClientTick);
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> Scanner.queue(chunk.getPos()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(CakeMap::endSession));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CakeMap.endSession());
		WorldRenderEvents.END.register(ctx -> InWorldWaypoints.capture(ctx.matrixStack().last().pose(), ctx.projectionMatrix(), ctx.camera().getPosition()));
		ClientReceiveMessageEvents.MODIFY_GAME.register(ChatLinks::modify);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				CakeMapCommands.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback, FabricClientCommandSource::sendError)));
	}

	/** Fabric wants an id on its reload listeners. */
	private static final class FabricStaticMaps extends StaticMaps implements IdentifiableResourceReloadListener {
		@Override
		public ResourceLocation getFabricId() {
			return ID;
		}
	}
}
