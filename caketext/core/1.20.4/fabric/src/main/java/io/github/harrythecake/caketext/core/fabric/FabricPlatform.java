package io.github.harrythecake.caketext.core.fabric;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.harrythecake.caketext.core.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

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
	public <T> List<Provided<T>> entrypoints(String name, Class<T> type) {
		List<Provided<T>> out = new ArrayList<>();
		for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(name, type)) {
			out.add(new Provided<>(container.getProvider().getMetadata().getId(), container::getEntrypoint));
		}
		return out;
	}
}
