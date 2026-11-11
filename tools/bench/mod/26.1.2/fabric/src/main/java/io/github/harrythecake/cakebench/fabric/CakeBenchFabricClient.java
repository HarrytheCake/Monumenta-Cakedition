package io.github.harrythecake.cakebench.fabric;

import io.github.harrythecake.cakebench.CakeBench;
import io.github.harrythecake.cakebench.Play;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Fabric client entry: wires Fabric API's events to {@link CakeBench} and registers {@code /cakebench}. */
public final class CakeBenchFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Play.register(dispatcher));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CakeBench.onDisconnect(client));
		CakeBench.init();
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> CakeBench.onScreenInit(client, screen));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CakeBench.onJoin());
	}
}
