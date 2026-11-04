package io.github.harrythecake.cakefixes.fabric;

import io.github.harrythecake.cakefixes.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

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

	@Override
	public String fieldName(String intermediaryOwner, String intermediaryName, String mojangName, String intermediaryDesc) {
		return mojangName; // 26.1+: Minecraft runs with Mojang's names
	}

	@Override
	public String className(String intermediaryName, String mojangName) {
		return mojangName;
	}
}
