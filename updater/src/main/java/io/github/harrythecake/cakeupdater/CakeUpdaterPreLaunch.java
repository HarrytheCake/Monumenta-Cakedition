package io.github.harrythecake.cakeupdater;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before other mods initialize. Applies a config restore that couldn't run when the game closed (crash, killed
 * process) and copies missing default files (config/cakeupdater/defaults/). No Minecraft classes may load here.
 */
public final class CakeUpdaterPreLaunch implements PreLaunchEntrypoint {
	private static final Logger LOG = LoggerFactory.getLogger(CakeUpdater.MOD_ID);

	@Override
	public void onPreLaunch() {
		Backups backups = new Backups(FabricLoader.getInstance().getGameDir());
		try {
			int restored = backups.applyPendingConfigRestore(Integer.MAX_VALUE);
			if (restored >= 0) {
				LOG.info("Restored {} config files from a backup", restored);
			}
			int copied = backups.applyDefaults();
			if (copied > 0) {
				LOG.info("Copied {} default files from config/cakeupdater/defaults", copied);
			}
		} catch (Exception e) {
			LOG.error("Backup tasks at start failed", e);
		}
	}
}
