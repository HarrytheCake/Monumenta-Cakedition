package io.github.harrythecake.cakeui.neoforge;

import io.github.harrythecake.cakeui.platform.Platform;
import net.neoforged.fml.ModList;
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

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get() != null && ModList.get().isLoaded(modId);
	}

	@Override
	public String modVersion(String modId) {
		if (ModList.get() == null) {
			return null;
		}
		return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse(null);
	}

	@Override
	public String modMetadata(String modId, String key) {
		if (ModList.get() == null) {
			return null;
		}
		return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getModProperties().get(key))
				.map(Object::toString).orElse(null);
	}
}
