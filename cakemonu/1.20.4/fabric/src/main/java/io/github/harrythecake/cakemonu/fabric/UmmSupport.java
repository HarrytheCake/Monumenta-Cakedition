package io.github.harrythecake.cakemonu.fabric;

import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Which Unofficial Monumenta Mod CakeMonu can bridge. CakeMonu hooks into UMM's own ability bar code, so it
 * only does that for UMM versions it was checked against; any other version keeps UMM's bar as UMM made it
 * (no mixin applied, the settings screen says so). Loads no UMM class.
 */
public final class UmmSupport {
	public static final String UMM_ID = "unofficial-monumenta-mod";
	/** Checked against UMM's source and jar for these versions (Modrinth, Minecraft 1.20.4). */
	static final Set<String> SUPPORTED = Set.of("1.10");

	private UmmSupport() {
	}

	/** UMM's version, or null when it isn't installed. */
	public static String version() {
		return FabricLoader.getInstance().getModContainer(UMM_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	public static boolean supported() {
		String v = version();
		return v != null && SUPPORTED.contains(v);
	}
}
