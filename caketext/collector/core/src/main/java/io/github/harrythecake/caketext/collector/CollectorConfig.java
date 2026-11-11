package io.github.harrythecake.caketext.collector;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.Json;
import io.github.harrythecake.caketext.core.context.Surfaces;

/** {@code config/caketext/collector.json} */
public final class CollectorConfig {
	public boolean enabled = true;
	/** Store collected files gzip-compressed ({@code .json.gz}); off = plain {@code .json}. */
	public boolean gzip = true;
	/** Record block positions with each sighting (16-block cells; exact for nameplates). */
	public boolean positions = true;
	/** Collect on multiplayer servers only (not in singleplayer worlds). */
	public boolean multiplayerOnly = true;
	/**
	 * Keep the Crowdin files ({@code exports/crowdin/<server>/}) up to date by itself (every {@code autoExportMinutes},
	 * on leaving a server and on quitting), so there's always a recent export even if nobody presses the button.
	 */
	public boolean autoExport = true;
	public int autoExportMinutes = 10;
	/** Exports include block positions. */
	public boolean exportPositions = true;
	/** How far away nameplates and holograms are collected, in blocks. */
	public int nameplateRange = 24;
	/** Distinct places remembered per string. */
	public int maxContexts = 16;
	/** Seconds between saves. */
	public int saveSeconds = 60;
	/** Per surface (and per module surface): collect it? */
	public Map<String, Boolean> sources = new LinkedHashMap<>();

	public boolean source(String id) {
		return sources.getOrDefault(id, true);
	}

	static Path path() {
		return CakeText.dir().resolve("collector.json");
	}

	public static CollectorConfig load() {
		CollectorConfig c = Json.load(path(), CollectorConfig.class, CollectorConfig::new);
		if (c.sources == null) {
			c.sources = new LinkedHashMap<>();
		}
		for (String s : Surfaces.BUILT_IN) {
			c.sources.putIfAbsent(s, true);
		}
		c.maxContexts = Math.max(1, Math.min(256, c.maxContexts));
		c.saveSeconds = Math.max(10, c.saveSeconds);
		c.autoExportMinutes = Math.max(1, c.autoExportMinutes);
		c.nameplateRange = Math.max(4, Math.min(128, c.nameplateRange));
		return c;
	}

	public void save() {
		Json.save(path(), this);
	}
}
