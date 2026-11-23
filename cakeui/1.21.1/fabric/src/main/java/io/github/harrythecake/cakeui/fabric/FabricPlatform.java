package io.github.harrythecake.cakeui.fabric;

import io.github.harrythecake.cakeui.platform.Platform;
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
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public java.util.List<ModLayoutFolder> modLayoutFolders() {
		java.util.List<ModLayoutFolder> out = new java.util.ArrayList<>();
		for (net.fabricmc.loader.api.ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			mod.findPath("assets/" + id + "/cakeui").filter(java.nio.file.Files::isDirectory).ifPresent(p -> out.add(new ModLayoutFolder(id, p)));
		}
		out.sort(java.util.Comparator.comparing(ModLayoutFolder::modId));
		return out;
	}

	@Override
	public java.util.List<ModLayoutFolder> modAssetFolders(String sub) {
		java.util.List<ModLayoutFolder> out = new java.util.ArrayList<>();
		for (net.fabricmc.loader.api.ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			mod.findPath("assets/" + id + "/" + sub).filter(java.nio.file.Files::isDirectory).ifPresent(p -> out.add(new ModLayoutFolder(id, p)));
		}
		out.sort(java.util.Comparator.comparing(ModLayoutFolder::modId));
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
				.filter(v -> v.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING)
				.map(net.fabricmc.loader.api.metadata.CustomValue::getAsString).orElse(null);
	}
}
