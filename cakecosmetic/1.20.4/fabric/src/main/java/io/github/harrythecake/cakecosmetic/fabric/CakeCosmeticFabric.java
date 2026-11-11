package io.github.harrythecake.cakecosmetic.fabric;

import io.github.harrythecake.cakecosmetic.Backgrounds;
import io.github.harrythecake.cakecosmetic.CakeCosmetic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Fabric entry: CakeCosmetic's hooks on Fabric API's events. The CakeUI plugin is the "cakeui" entrypoint. */
public final class CakeCosmeticFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeCosmetic.init();
		Backgrounds backgrounds = CakeCosmetic.backgrounds();
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return Backgrounds.ID;
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				backgrounds.onResourceManagerReload(manager);
			}
		});
		KeyBindingHelper.registerKeyBinding(CakeCosmetic.galleryKey());
		ClientTickEvents.END_CLIENT_TICK.register(CakeCosmetic::endClientTick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			CakeCosmetic.afterScreenInit(screen, width, Screens.getButtons(screen)::add);
			if (CakeCosmetic.selfTestRunning()) {
				ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> CakeCosmetic.afterScreenRender(s, ctx));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CakeCosmetic.onDisconnect(client));
	}
}
