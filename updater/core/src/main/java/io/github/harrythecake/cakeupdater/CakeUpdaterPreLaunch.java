package io.github.harrythecake.cakeupdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Runs before other mods initialize: Fabric's preLaunch entrypoint; on NeoForge a mixin config plugin's onLoad (before
 * mods are constructed and before the game reads options.txt). Applies a config restore or an import (MoveIn) that
 * couldn't run when the game closed (crash, killed process) and copies missing default files
 * (config/cakeupdater/defaults/). No Minecraft classes may load here.
 */
public final class CakeUpdaterPreLaunch {
	private static final Logger LOG = LoggerFactory.getLogger(CakeUpdaterMod.MOD_ID);
	private static boolean ran;

	private CakeUpdaterPreLaunch() {}

	/** Runs once per start; {@code gameDir} is the game folder (.minecraft). */
	public static synchronized void run(Path gameDir) {
		if (ran) {
			return;
		}
		ran = true;
		Backups backups = new Backups(gameDir);
		try {
			int restored = backups.applyPendingConfigRestore(Integer.MAX_VALUE);
			if (restored >= 0) {
				LOG.info("Restored {} config files from a backup", restored);
			}
			int imported = MoveIn.apply(gameDir, backups, Integer.MAX_VALUE);
			if (imported >= 0) {
				LOG.info("Imported {} files from another install", imported);
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
