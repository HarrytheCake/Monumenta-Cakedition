package io.github.harrythecake.cakemonu.fabric;

import io.github.harrythecake.cakemonu.platform.Platform;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
	@Override
	public String loader() {
		return "fabric";
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}
}
