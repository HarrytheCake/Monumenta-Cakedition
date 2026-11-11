package io.github.harrythecake.cakecosmetic.neoforge;

import io.github.harrythecake.cakecosmetic.platform.Platform;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform {
	@Override
	public String loader() {
		return "neoforge";
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}
}
