package io.github.harrythecake.cakewiki.fabric;

import io.github.harrythecake.cakewiki.CakeWiki;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Fabric's client entrypoint: Fabric API's key, tick and client-command hooks call the shared {@link CakeWiki}. */
public final class CakeWikiFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeWiki.init();
		KeyMappingHelper.registerKeyMapping(CakeWiki.createKey(KeyMapping.Category.register(CakeWiki.KEY_CATEGORY)));
		ClientTickEvents.END_CLIENT_TICK.register(CakeWiki::onClientTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(CakeWiki.<FabricClientCommandSource>command()));
	}
}
