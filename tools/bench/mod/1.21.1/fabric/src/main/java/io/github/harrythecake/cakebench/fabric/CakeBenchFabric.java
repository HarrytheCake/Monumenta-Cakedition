package io.github.harrythecake.cakebench.fabric;

import io.github.harrythecake.cakebench.BenchServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Fabric main entry (client and dedicated server): the bench server's {@code /cbbot}. */
public final class CakeBenchFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> BenchServer.register(dispatcher));
	}
}
