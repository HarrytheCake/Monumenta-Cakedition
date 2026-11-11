package io.github.harrythecake.cakecosmetic.fabric;

import io.github.harrythecake.cakecosmetic.Backgrounds;
import io.github.harrythecake.cakecosmetic.CakeCosmetic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.PackType;

/** Fabric entry: CakeCosmetic's hooks on Fabric API's events. The CakeUI plugin is the "cakeui" entrypoint. */
public final class CakeCosmeticFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeCosmetic.init();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Backgrounds.ID, CakeCosmetic.backgrounds());
		KeyMappingHelper.registerKeyMapping(CakeCosmetic.createKey(KeyMapping.Category.register(CakeCosmetic.KEY_CATEGORY)));
		ClientTickEvents.END_CLIENT_TICK.register(CakeCosmetic::endClientTick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			CakeCosmetic.afterScreenInit(screen, width, Screens.getWidgets(screen)::add);
			if (CakeCosmetic.selfTestRunning()) {
				ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> CakeCosmetic.afterScreenRender(s, ctx));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CakeCosmetic.onDisconnect(client));
	}
}
