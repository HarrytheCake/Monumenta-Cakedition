package io.github.harrythecake.cakechat.fabric;

import io.github.harrythecake.cakechat.platform.Platform;
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
}
