package io.github.harrythecake.cakeconfighub.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric client entry. The key binding and /cakeconfighub need Fabric API's modules, which CakeConfighub doesn't
 * require: without them it still applies pack defaults, and Mod Menu's Configure opens the hub.
 */
public final class CakeConfighubFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("fabric-lifecycle-events-v1")) {
			FabricApiHooks.tick();
			if (loader.isModLoaded(FabricApiHooks.KEY_MODULE)) {
				FabricApiHooks.key();
			}
			if (loader.isModLoaded("fabric-command-api-v2")) {
				FabricApiHooks.command();
			}
		}
	}
}
