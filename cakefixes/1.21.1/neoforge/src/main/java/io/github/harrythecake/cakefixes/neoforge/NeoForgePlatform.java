package io.github.harrythecake.cakefixes.neoforge;

import io.github.harrythecake.cakefixes.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge runs with Mojang's names, so the runtime names are Mojang's. */
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
	public boolean isModLoaded(String modId) {
		return ModList.get() != null && ModList.get().isLoaded(modId);
	}

	@Override
	public String fieldName(String intermediaryOwner, String intermediaryName, String mojangName, String intermediaryDesc) {
		return mojangName;
	}

	@Override
	public String className(String intermediaryName, String mojangName) {
		return mojangName;
	}
}
