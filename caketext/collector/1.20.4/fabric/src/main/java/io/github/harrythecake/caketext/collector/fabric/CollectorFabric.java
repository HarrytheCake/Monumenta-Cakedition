package io.github.harrythecake.caketext.collector.fabric;

import io.github.harrythecake.caketext.collector.CollectorCommands;
import io.github.harrythecake.caketext.collector.CollectorMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Fabric entry: Fabric API events → {@link CollectorMod}. */
public final class CollectorFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CollectorMod.init();
		// ALLOW runs before any mod changes the message, so this sees the server's original text
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			CollectorMod.onGameMessage(message, overlay);
			return true;
		});
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> CollectorMod.onTooltip(lines));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> CollectorMod.onScreenInit(screen));
		ClientTickEvents.END_CLIENT_TICK.register(CollectorMod::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CollectorMod.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CollectorMod.onDisconnect());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CollectorMod.onStopping());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				CollectorCommands.register(dispatcher, FabricClientCommandSource::sendFeedback));
	}
}
