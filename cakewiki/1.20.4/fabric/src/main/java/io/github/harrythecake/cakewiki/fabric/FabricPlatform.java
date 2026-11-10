package io.github.harrythecake.cakewiki.fabric;

import io.github.harrythecake.cakewiki.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricPlatform implements Platform {
	@Override
	public String loader() {
		return "fabric";
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public String modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}
}
