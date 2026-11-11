package io.github.harrythecake.cakebook.fabric;

import io.github.harrythecake.cakebook.CakeBook;
import io.github.harrythecake.cakebook.CakeBookClient;
import io.github.harrythecake.cakebook.MenuButtons;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Fabric entry: wires the shared client code to Fabric API's events. */
public final class CakeBookFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeBookClient.init();

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public ResourceLocation getFabricId() {
						return new ResourceLocation(CakeBook.MOD_ID, "books");
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						CakeBookClient.loadBooks(manager);
					}
				});

		KeyBindingHelper.registerKeyBinding(CakeBookClient.OPEN_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(CakeBookClient::tick);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			Runnable beforeRender = MenuButtons.afterInit(screen, Screens.getButtons(screen)::add);
			if (beforeRender != null) {
				ScreenEvents.beforeRender(screen).register((s, context, mouseX, mouseY, delta) -> beforeRender.run());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				CakeBookClient.command(FabricClientCommandSource::sendError, FabricClientCommandSource::sendFeedback)));
	}
}
