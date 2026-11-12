package io.github.harrythecake.cakeupdater.fabric;

import io.github.harrythecake.cakeupdater.CakeUpdater;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Fabric's client entrypoint: Fabric API's events call the shared {@link CakeUpdater}. */
public final class CakeUpdaterFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeUpdater.init();
		ClientLifecycleEvents.CLIENT_STARTED.register(CakeUpdater::onClientStarted);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> CakeUpdater.onScreenInit(client, screen));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CakeUpdater.onJoin(client));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(CakeUpdater.<FabricClientCommandSource>command()));
		ClientLifecycleEvents.CLIENT_STOPPING.register(CakeUpdater::onStopping);
		ClientTickEvents.END_CLIENT_TICK.register(CakeUpdater::onClientTick);
	}
}
