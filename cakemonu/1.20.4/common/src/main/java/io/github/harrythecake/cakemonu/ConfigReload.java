package io.github.harrythecake.cakemonu;

/** CakeConfighub's reload hook ({@code cakeconfighub:reload} in the mod's metadata) and {@code /cakemonu reload}: reads the icon places again. */
public final class ConfigReload {
	private ConfigReload() {
	}

	public static void cakeconfighubReloadConfig() {
		CakeMonu.load();
	}
}
