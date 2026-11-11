package io.github.harrythecake.cakebook.fabric;

import io.github.harrythecake.cakebook.CakeBook;
import io.github.harrythecake.cakebook.CakeBookClient;
import io.github.harrythecake.cakebook.MenuButtons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/** Fabric entry: wires the shared client code to Fabric API's events. */
public final class CakeBookFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeBookClient.init();

		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath(CakeBook.MOD_ID, "books"), CakeBookClient.RELOAD_LISTENER);

		KeyMappingHelper.registerKeyMapping(CakeBookClient.createOpenKey(KeyMapping.Category.register(CakeBookClient.KEY_CATEGORY)));
		ClientTickEvents.END_CLIENT_TICK.register(CakeBookClient::tick);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			Runnable beforeRender = MenuButtons.afterInit(screen, Screens.getWidgets(screen)::add);
			if (beforeRender != null) {
				ScreenEvents.beforeExtract(screen).register((s, context, mouseX, mouseY, delta) -> beforeRender.run());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				CakeBookClient.command(FabricClientCommandSource::sendError, FabricClientCommandSource::sendFeedback)));
	}
}
