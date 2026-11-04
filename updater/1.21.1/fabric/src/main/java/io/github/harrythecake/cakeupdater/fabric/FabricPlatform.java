package io.github.harrythecake.cakeupdater.fabric;

import io.github.harrythecake.cakeupdater.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Optional;

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
	public Optional<String> modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString());
	}

	@Override
	public Path modFile(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).orElseThrow().getOrigin().getPaths().get(0);
	}
}
