package io.github.harrythecake.cakefixes.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Only the /cakefixes reload command, and only when Fabric API's client commands are installed (not required). */
public final class CakeFixesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (FabricLoader.getInstance().isModLoaded("fabric-command-api-v2")) {
			ReloadCommand.register();
		}
	}
}
