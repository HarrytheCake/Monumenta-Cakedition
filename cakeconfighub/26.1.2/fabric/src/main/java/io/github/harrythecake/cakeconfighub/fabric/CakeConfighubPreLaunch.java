package io.github.harrythecake.cakeconfighub.fabric;

import io.github.harrythecake.cakeconfighub.CakeConfighub;
import io.github.harrythecake.cakeconfighub.configmap.ConfigHub;
import io.github.harrythecake.cakeconfighub.configmap.HubSelfTestFiles;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Applies the pack defaults (config/cakeconfighub/pack-defaults.json5) to other mods' config files before those mods
 * read them (ADR 0011, 0013). Loads no Minecraft class; does nothing without that file; never throws.
 */
public final class CakeConfighubPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (CakeConfighub.SELF_TEST) {
			try {
				// the self-test's made-up map, file and pack defaults, so it can check this run and the hub screen
				HubSelfTestFiles.write(FabricLoader.getInstance().getConfigDir());
			} catch (Exception e) {
				CakeConfighub.LOGGER.warn("CakeConfighub self-test: hub files not written: {}", e.toString());
			}
		}
		ConfigHub.applyPackDefaults();
	}
}
