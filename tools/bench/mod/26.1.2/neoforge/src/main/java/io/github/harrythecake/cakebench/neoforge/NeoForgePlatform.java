package io.github.harrythecake.cakebench.neoforge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.harrythecake.cakebench.platform.Platform;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

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
	public Map<String, String> mods() {
		Map<String, String> mods = new TreeMap<>();
		ModList.get().getMods().forEach(m -> {
			if (!m.getModId().equals("minecraft")) {
				mods.put(m.getModId(), m.getVersion().toString());
			}
		});
		return mods;
	}

	@Override
	public String runClientCommand(String command) {
		var dispatcher = ClientCommandHandler.getDispatcher();
		if (dispatcher == null || Minecraft.getInstance().getConnection() == null) {
			return "no client command dispatcher";
		}
		try {
			dispatcher.execute(command, ClientCommandHandler.getSource());
			return null;
		} catch (CommandSyntaxException e) {
			return e.getMessage();
		}
	}
}
