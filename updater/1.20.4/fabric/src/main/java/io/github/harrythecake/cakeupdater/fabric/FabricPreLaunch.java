package io.github.harrythecake.cakeupdater.fabric;

import io.github.harrythecake.cakeupdater.CakeUpdaterPreLaunch;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Fabric's preLaunch entrypoint: before other mods initialize. No Minecraft classes may load here. */
public final class FabricPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		CakeUpdaterPreLaunch.run(FabricLoader.getInstance().getGameDir());
	}
}
