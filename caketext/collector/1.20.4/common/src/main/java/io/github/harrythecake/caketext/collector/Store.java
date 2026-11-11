package io.github.harrythecake.caketext.collector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.strings.Exchanger;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import org.jetbrains.annotations.Nullable;

/**
 * Collected strings of the current server, in memory, saved to {@code config/caketext/collected/<server>.json.gz}.
 * One entry per template; each keeps up to {@code maxContexts} distinct places it was seen (surface, dimension,
 * shard, area, 16-block cell). Seeing the same string on the same surface again within 5 s doesn't count, so
 * tooltips and scoreboards (read many times a second) don't inflate the counts.
 */
final class Store {
	private static final long REPEAT_MS = 5_000;

	private static final class Item {
		final StringEntry entry;
		final LinkedHashMap<String, Map<String, Object>> contexts = new LinkedHashMap<>();

		Item(StringEntry entry) {
			this.entry = entry;
		}
	}

	private final Map<String, Item> items = new LinkedHashMap<>();
	private final Map<String, Long> recent = new HashMap<>();
	private CollectorConfig config;
	private @Nullable String server;
	private boolean dirty;
	private int added;
	/** Sightings recorded since start, and the count at the last auto-export. */
	private long changes;
	private long exportedChanges;

	Store(CollectorConfig config) {
		this.config = config;
	}

	synchronized void setConfig(CollectorConfig config) {
		boolean formatChanged = config.gzip != this.config.gzip;
		this.config = config;
		if (formatChanged) {
			dirty = true;
		}
	}

	static Path dir() {
		return CakeText.dir().resolve("collected");
	}

	private static String safe(String server) {
		return server.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static Path file(String server, boolean gzip) {
		return dir().resolve(safe(server) + (gzip ? ".json.gz" : ".json"));
	}

	synchronized @Nullable Path currentFile() {
		return server == null ? null : file(server, config.gzip);
	}

	synchronized @Nullable String server() {
		return server;
	}

	synchronized int size() {
		return items.size();
	}

	/** New strings since the game started. */
	synchronized int added() {
		return added;
	}

	/** Switches to {@code server}'s file (saving the previous one). */
	void open(String newServer) {
		synchronized (this) {
			if (newServer.equals(server)) {
				return;
			}
		}
		flush();
		CollectorMod.autoExportNow();
		synchronized (this) {
			items.clear();
			recent.clear();
			server = newServer;
			dirty = false;
			exportedChanges = changes;
			for (boolean gz : new boolean[] {config.gzip, !config.gzip}) {
				Path p = file(newServer, gz);
				if (Files.exists(p)) {
					try {
						int purged = 0;
						for (StringEntry e : StringsFile.read(p).entries) {
							if (PrivacyFilter.rejectStored(e)) {
								// collected by an older version that missed this chat format
								purged++;
								continue;
							}
							Item item = new Item(e);
							if (e.contexts != null) {
								for (Map<String, Object> c : e.contexts) {
									item.contexts.put(keyOf(c), c);
								}
							}
							items.put(e.src, item);
						}
						dirty = gz != config.gzip || purged > 0;
						if (purged > 0) {
							CakeText.LOG.info("Removed {} stored lines that look like player chat", purged);
						}
					} catch (IOException | RuntimeException ex) {
						CakeText.LOG.warn("Could not read collected strings {}", p, ex);
					}
					break;
				}
			}
		}
	}

	/** Records a sighting; true if the template is new. Call with a template that passed the filters. */
	synchronized boolean record(String template, GameContext context) {
		if (server == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		String recentKey = context.surface() + '\u0000' + template;
		Long last = recent.get(recentKey);
		if (last != null && now - last < REPEAT_MS) {
			return false;
		}
		if (recent.size() > 20_000) {
			recent.clear();
		}
		recent.put(recentKey, now);

		Item item = items.get(template);
		boolean isNew = item == null;
		if (isNew) {
			StringEntry e = new StringEntry(template, "");
			e.count = 0L;
			e.first = Instant.ofEpochMilli(now).toString();
			item = new Item(e);
			items.put(template, item);
			added++;
		}
		item.entry.count = (item.entry.count == null ? 0 : item.entry.count) + 1;
		item.entry.last = Instant.ofEpochMilli(now).toString();
		String key = context.mergeKey(Surfaces.isPlaced(context.surface()));
		if (!item.contexts.containsKey(key) && item.contexts.size() < config.maxContexts) {
			item.contexts.put(key, context.toJson(config.positions));
		}
		dirty = true;
		changes++;
		return isNew;
	}

	/** The merge key of a context read back from a file (same rule as {@link GameContext#mergeKey}). */
	private static String keyOf(Map<String, Object> c) {
		String surface = String.valueOf(c.get("surface"));
		String cell = "";
		if (c.get("pos") instanceof List<?> pos && pos.size() == 3) {
			int[] p = new int[3];
			for (int i = 0; i < 3; i++) {
				p[i] = ((Number) pos.get(i)).intValue();
			}
			cell = Surfaces.isPlaced(surface) ? p[0] + ", " + p[1] + ", " + p[2]
					: (p[0] >> 4) + "," + (p[1] >> 4) + "," + (p[2] >> 4);
		}
		return surface + '|' + c.get("server") + '|' + c.get("dimension") + '|' + c.get("shard") + '|' + c.get("area") + '|' + cell;
	}

	private StringsFile snapshot(Predicate<StringEntry> filter, boolean positions, boolean byCount) {
		StringsFile file = new StringsFile();
		file.server = server;
		List<StringEntry> list = new ArrayList<>();
		for (Item item : items.values()) {
			if (!filter.test(item.entry)) {
				continue;
			}
			StringEntry e = new StringEntry(item.entry.src, item.entry.dst == null ? "" : item.entry.dst);
			e.count = item.entry.count;
			e.first = item.entry.first;
			e.last = item.entry.last;
			e.contexts = new ArrayList<>();
			for (Map<String, Object> c : item.contexts.values()) {
				Map<String, Object> copy = new LinkedHashMap<>(c);
				if (!positions) {
					copy.remove("pos");
				}
				e.contexts.add(copy);
			}
			list.add(e);
		}
		if (byCount) {
			list.sort(Comparator.comparingLong((StringEntry e) -> e.count == null ? 0 : e.count).reversed());
		}
		file.entries = list;
		return file;
	}

	/** Saves if anything changed. Safe from any thread; the file is written outside the lock. */
	void flush() {
		StringsFile file;
		Path path;
		boolean gzip;
		synchronized (this) {
			if (!dirty || server == null) {
				return;
			}
			file = snapshot(e -> true, true, false);
			gzip = config.gzip;
			path = file(server, gzip);
			dirty = false;
		}
		try {
			file.write(path, gzip);
			Files.deleteIfExists(file(file.server, !gzip));
		} catch (IOException e) {
			CakeText.LOG.warn("Could not save collected strings to {}", path, e);
			synchronized (this) {
				dirty = true;
			}
		}
	}

	/** Where this server's Crowdin files go: {@code exports/crowdin/<server>/}. */
	synchronized @Nullable Path crowdinDir() {
		return server == null ? null : CakeText.dir().resolve("exports").resolve("crowdin").resolve(safe(server));
	}

	/**
	 * Writes the Crowdin files (English {@code .pot} per surface + {@code crowdin.yml}, see
	 * {@link io.github.harrythecake.caketext.core.strings.Exchanger#writeCrowdin}). Every string goes in, translated or
	 * not: a Crowdin source missing strings would drop them, translations included. Returns the folder.
	 */
	Path export(boolean positions) throws IOException {
		synchronized (this) {
			if (server == null) {
				throw new IOException("not on a server");
			}
			exportedChanges = changes;
		}
		return write(positions);
	}

	/** Like {@link #export}, but only if anything was collected since the last export. Null when nothing was new. */
	@Nullable Path autoExport(boolean positions) throws IOException {
		synchronized (this) {
			if (server == null || items.isEmpty() || changes == exportedChanges) {
				return null;
			}
			exportedChanges = changes;
		}
		return write(positions);
	}

	private Path write(boolean positions) throws IOException {
		StringsFile file;
		Path dir;
		synchronized (this) {
			// first-seen order, so a new export only adds lines and diffs stay small
			file = snapshot(e -> true, positions, false);
			dir = crowdinDir();
		}
		Exchanger.writeCrowdin(file, dir, true);
		return dir;
	}
}
