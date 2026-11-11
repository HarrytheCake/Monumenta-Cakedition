package io.github.harrythecake.caketext.translator.fabric;

import io.github.harrythecake.caketext.translator.TranslatorCommands;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;

/** Fabric entry: Fabric API events → {@link TranslatorMod}. */
public final class TranslatorFabric implements ClientModInitializer {
	/** Tooltips are translated after every other listener (the collector runs in the default phase). */
	private static final ResourceLocation TOOLTIP_PHASE = new ResourceLocation("caketext_translator", "translate");

	@Override
	public void onInitializeClient() {
		TranslatorMod.init();
		// chat lines are handled where they enter the chat box (ChatHudMixin); the action bar is here
		ClientReceiveMessageEvents.MODIFY_GAME.register(TranslatorMod::onGameMessage);
		ItemTooltipCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, TOOLTIP_PHASE);
		ItemTooltipCallback.EVENT.register(TOOLTIP_PHASE, (stack, context, lines) -> TranslatorMod.onTooltip(stack, lines));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> TranslatorMod.onScreenInit(screen));
		ClientTickEvents.END_CLIENT_TICK.register(TranslatorMod::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TranslatorMod.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TranslatorMod.onDisconnect());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TranslatorMod.onDisconnect());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				TranslatorCommands.register(dispatcher, FabricClientCommandSource::sendFeedback));
	}
}
