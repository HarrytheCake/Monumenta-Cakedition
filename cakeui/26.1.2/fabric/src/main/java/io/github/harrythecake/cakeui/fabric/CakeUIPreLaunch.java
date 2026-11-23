package io.github.harrythecake.cakeui.fabric;

import io.github.harrythecake.cakeui.configmap.ConfigHub;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Applies the pack defaults (config/cakeui/pack-defaults.json5) to other mods' config files before any mod reads them
 * (ADR 0011). Loads no Minecraft class; does nothing without that file.
 */
public final class CakeUIPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (!System.getProperty("cakeui.selftest", "").isEmpty()) {
			try {
				// the self-test's made-up map, file and pack defaults, so it can check this run and the hub screen
				io.github.harrythecake.cakeui.configmap.HubSelfTestFiles.write(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
			} catch (java.io.IOException e) {
				io.github.harrythecake.cakeui.CakeUI.LOGGER.warn("CakeUI self-test: hub files not written: {}", e.toString());
			}
		}
		ConfigHub.applyPackDefaults();
	}
}
