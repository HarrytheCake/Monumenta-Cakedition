package io.github.harrythecake.cakeupdater.neoforge;

import io.github.harrythecake.cakeupdater.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

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
	public Optional<String> modVersion(String modId) {
		return ModList.get() == null ? Optional.empty()
				: ModList.get().getModContainerById(modId).map(mod -> mod.getModInfo().getVersion().toString());
	}

	@Override
	public Path modFile(String modId) {
		return ModList.get().getModFileById(modId).getFile().getFilePath();
	}
}
