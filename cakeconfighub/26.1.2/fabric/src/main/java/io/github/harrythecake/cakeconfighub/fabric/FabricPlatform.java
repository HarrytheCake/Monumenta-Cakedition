package io.github.harrythecake.cakeconfighub.fabric;

import io.github.harrythecake.cakeconfighub.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public List<ModAssetFolder> modAssetFolders(String sub) {
		List<ModAssetFolder> out = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			mod.findPath("assets/" + id + "/" + sub).filter(Files::isDirectory).ifPresent(p -> out.add(new ModAssetFolder(id, p)));
		}
		out.sort(Comparator.comparing(ModAssetFolder::modId));
		return out;
	}

	@Override
	public String modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	@Override
	public String modMetadata(String modId, String key) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getCustomValue(key))
				.filter(v -> v.getType() == CustomValue.CvType.STRING)
				.map(CustomValue::getAsString).orElse(null);
	}
}
