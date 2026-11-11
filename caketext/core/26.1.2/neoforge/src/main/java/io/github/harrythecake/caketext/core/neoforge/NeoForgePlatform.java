package io.github.harrythecake.caketext.core.neoforge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.github.harrythecake.caketext.core.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

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

	/** NeoForge has no entrypoints: mods list implementations in META-INF/services/&lt;type&gt;. */
	@Override
	public <T> List<Provided<T>> entrypoints(String name, Class<T> type) {
		List<Provided<T>> out = new ArrayList<>();
		ServiceLoader.load(type, type.getClassLoader()).stream().forEach(p -> {
			Module module = p.type().getModule();
			out.add(new Provided<>(module.isNamed() ? module.getName() : p.type().getName(), p::get));
		});
		return out;
	}
}
