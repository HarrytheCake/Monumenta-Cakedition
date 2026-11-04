package io.github.harrythecake.cakeupdater;

import io.github.harrythecake.cakeupdater.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * The mod id, logger and folders, for code without Minecraft classes (pre-launch, the shutdown hook, tests).
 * {@link CakeUpdater} (Minecraft-facing) keeps the same names.
 */
public final class CakeUpdaterMod {
	public static final String MOD_ID = "cakeupdater";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The Cakedition pack is Fabric on this Minecraft version only (pack/pack.toml [versions]). */
	static final String CAKEDITION_LOADER = "fabric";
	static final String CAKEDITION_MINECRAFT = "1.20.4";

	private CakeUpdaterMod() {}

	public static Path gameDir() {
		return Services.PLATFORM.gameDir();
	}

	/** The running game's version, or null when unknown (tests, no loader). */
	static String minecraftVersion() {
		try {
			return Services.PLATFORM.modVersion("minecraft").orElse(null);
		} catch (Throwable e) {
			return null;
		}
	}

	/** "fabric" / "neoforge"; "fabric" when unknown (tests). */
	static String loader() {
		try {
			return Services.PLATFORM.loader();
		} catch (Throwable e) {
			return CAKEDITION_LOADER;
		}
	}

	/**
	 * Whether the Cakedition pack exists for the running game (Fabric 1.20.4). Elsewhere the default (Cakedition's)
	 * pack addresses are ignored: offering that pack there would install Fabric 1.20.4 mods into another game.
	 */
	static boolean cakeditionTarget() {
		String mc = minecraftVersion();
		return CAKEDITION_LOADER.equals(loader()) && (mc == null || CAKEDITION_MINECRAFT.equals(mc));
	}
}
