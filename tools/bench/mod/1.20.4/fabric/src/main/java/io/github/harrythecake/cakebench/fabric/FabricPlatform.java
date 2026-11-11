package io.github.harrythecake.cakebench.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.harrythecake.cakebench.platform.Platform;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

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
	public Map<String, String> mods() {
		Map<String, String> mods = new TreeMap<>();
		FabricLoader.getInstance().getAllMods().forEach(m -> {
			String id = m.getMetadata().getId();
			if (!id.startsWith("fabric-") && !id.equals("java") && !id.equals("minecraft")) {
				mods.put(id, m.getMetadata().getVersion().getFriendlyString());
			}
		});
		return mods;
	}

	@Override
	public String runClientCommand(String command) {
		var dispatcher = ClientCommandManager.getActiveDispatcher();
		Minecraft client = Minecraft.getInstance();
		if (dispatcher == null || client.getConnection() == null) {
			return "no client command dispatcher";
		}
		try {
			dispatcher.execute(command, (FabricClientCommandSource) client.getConnection().getSuggestionsProvider());
			return null;
		} catch (CommandSyntaxException e) {
			return e.getMessage();
		}
	}
}
