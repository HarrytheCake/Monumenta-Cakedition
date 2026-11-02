package io.github.harrythecake.cakefixes;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * config/cakefixes/config.json5: one switch per fix, all on by default, plus options that change what you see
 * (off by default).
 * Created on first launch; modpacks can ship their own. {@link #parse} and {@link #write} are plain Java for the tests.
 */
public final class Config {
	/** Skip team removals for players not on that team instead of throwing (see TeamRemoval). */
	public boolean teamRemovalFix = true;
	/** Answer AppleSkin's per-frame glIsEnabled(GL_BLEND) from Minecraft's tracked state (see BlendQuery). */
	public boolean appleSkinBlendQueryFix = true;
	/** Option, not a fix: banners farther than this many blocks aren't drawn. 0 = off (vanilla 64). */
	public int bannerDrawDistance = 0;

	static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(CakeFixes.MOD_ID).resolve("config.json5");
	}

	static Config load() {
		Path file = file();
		try {
			if (!Files.exists(file)) {
				Config defaults = new Config();
				save(file, defaults);
				return defaults;
			}
			String text = Files.readString(file, StandardCharsets.UTF_8);
			Config config = parse(text);
			if (needsRewrite(text)) {
				// A config from an older version: add the new settings (with their comments), keep the values.
				save(file, config);
			}
			return config;
		} catch (Exception e) {
			CakeFixes.LOGGER.error("Could not read {}, using defaults: {}", file, e.toString());
			return new Config();
		}
	}

	/** Gson reads leniently, which allows the // comments; missing switches keep their defaults. */
	static Config parse(String json) {
		Config config = new Gson().fromJson(json, Config.class);
		return config == null ? new Config() : config;
	}

	/** True when the file lacks a setting this version has (every public field must appear as "name"). */
	static boolean needsRewrite(String text) {
		for (java.lang.reflect.Field f : Config.class.getFields()) {
			if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && !text.contains("\"" + f.getName() + "\"")) {
				return true;
			}
		}
		return false;
	}

	static String write(Config config) {
		return """
				// CakeFixes settings: one switch per fix, true = on. Comments are allowed; trailing commas are not.
				{
				  // The server removes a player from a scoreboard team they are not on. Vanilla throws and builds a
				  // crash report every time (a 100-500 ms freeze); this fix skips that one removal instead.
				  "teamRemovalFix": %s,
				  // AppleSkin asks the graphics driver every frame whether blending is on, which makes the game wait
				  // for the driver. This fix answers from Minecraft's own record instead. Nothing on screen changes.
				  "appleSkinBlendQueryFix": %s,
				  // Option (changes what you see, so off by default): banners farther than this many blocks aren't
				  // drawn. Banners are decoration and cost 3-4%% of frame time in banner-heavy dungeons.
				  // 0 = off (vanilla draws them up to 64 blocks). Mod Menu offers 16, 24, 32 and 48.
				  "bannerDrawDistance": %d
				}
				""".formatted(config.teamRemovalFix, config.appleSkinBlendQueryFix, config.bannerDrawDistance);
	}

	static void save(Path file, Config config) throws Exception {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, write(config), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** Saves to config/cakefixes/config.json5 and makes it the live config. */
	static void saveAndApply(Config config) {
		try {
			save(file(), config);
		} catch (Exception e) {
			CakeFixes.LOGGER.error("Could not save {}: {}", file(), e.toString());
		}
		CakeFixes.setConfig(config);
	}

	Config copy() {
		Config c = new Config();
		c.teamRemovalFix = teamRemovalFix;
		c.appleSkinBlendQueryFix = appleSkinBlendQueryFix;
		c.bannerDrawDistance = bannerDrawDistance;
		return c;
	}
}
