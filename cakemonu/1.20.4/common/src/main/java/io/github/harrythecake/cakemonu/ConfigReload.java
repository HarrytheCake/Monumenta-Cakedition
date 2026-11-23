package io.github.harrythecake.cakemonu;

/** CakeUI's reload hook ({@code cakeui:reload} in the mod's metadata) and {@code /cakemonu reload}: reads the icon places again. */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeuiReloadConfig() {
		CakeMonu.load();
	}
}
