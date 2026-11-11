package io.github.harrythecake.cakewiki.neoforge;

import io.github.harrythecake.cakewiki.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform {
	@Override
	public String loader() {
		return "neoforge";
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get() != null && ModList.get().isLoaded(modId);
	}

	@Override
	public String modVersion(String modId) {
		return ModList.get() == null ? "dev" : ModList.get().getModContainerById(modId)
				.map(m -> m.getModInfo().getVersion().toString()).orElse("dev");
	}
}
