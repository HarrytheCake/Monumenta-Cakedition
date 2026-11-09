package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointStore;
import io.github.harrythecake.cakemap.core.WorldKeys;
import io.github.harrythecake.cakemap.xaero.XaeroRegionReader;
import io.github.harrythecake.cakemap.xaero.XaeroSettings;
import io.github.harrythecake.cakemap.xaero.XaeroWaypoints;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports Xaero's Minimap and World Map: settings, key bindings, waypoints and explored maps. Reads
 * only; nothing of Xaero's is changed or deleted. Map regions are merged into CakeMap's without
 * overwriting what CakeMap already knows. This class does the files (no Minecraft classes);
 * XaeroImportRun runs it on its own thread in the game.
 */
public final class XaeroImport {
	private static final Pattern REGION = Pattern.compile("(-?\\d+)_(-?\\d+)\\.zip");
	/** Xaero's Minimap before 2024 (and Fair-Play 24.x) keeps waypoints here, next to xaero/. */
	public static final String LEGACY_WAYPOINTS = "XaeroWaypoints";

	/** What to bring over. */
	public static final class Options {
		public boolean settings = true;
		public boolean keys = true;
		public boolean waypoints = true;
		public boolean maps = true;
		/** Which map the imported waypoints show on: Waypoint.BOTH, STATIC or DYNAMIC. */
		public String waypointMaps = Waypoint.BOTH;
	}

	/** Progress for the screen: done / total region files, and a summary at the end. */
	public static final class Progress {
		public final AtomicInteger done = new AtomicInteger();
		public volatile int total;
		public volatile boolean finished;
		public volatile String error;
		public volatile int waypoints;
		public volatile int regions;
		public volatile int failed;
		public volatile List<String> settings = List.of();
		public volatile int keys;
	}

	private XaeroImport() {
	}

	/** The xaero folder inside a game folder, or the folder itself when it is one. Null when there is none. */
	public static Path xaeroDir(Path chosen) {
		if (chosen == null) {
			return null;
		}
		if (Files.isDirectory(chosen.resolve("xaero"))) {
			return chosen.resolve("xaero");
		}
		if (Files.isDirectory(chosen.resolve(LEGACY_WAYPOINTS))) {
			// only the older minimap's waypoint folder: the (missing) xaero folder stands for the game folder
			return chosen.resolve("xaero");
		}
		if (Files.isDirectory(chosen.resolve("minimap")) || Files.isDirectory(chosen.resolve("world-map"))) {
			return chosen;
		}
		if (Files.isDirectory(chosen.resolve(".minecraft").resolve("xaero"))) {
			return chosen.resolve(".minecraft").resolve("xaero");
		}
		return null;
	}

	/** The game folder belonging to an xaero folder (for config/ and options.txt). */
	public static Path gameDir(Path xaero) {
		Path p = xaero.getParent();
		return p == null ? xaero : p;
	}

	public static boolean found(Path gameDir) {
		Path x = xaeroDir(gameDir);
		return x != null;
	}

	/** Changes a server's store in memory; returns what the change returned, or -1 when that server isn't loaded. */
	public interface LiveStore {
		int apply(String server, ToIntFunction<WaypointStore> change);
	}

	/**
	 * {@code minimap/<server>/dim%<dim>/<world>_<n>.txt} → each server's waypoints, shown on
	 * {@code maps} (Waypoint.BOTH, STATIC or DYNAMIC). A server that {@code live} holds in memory
	 * gets them there; the others get them in {@code <root>/<server>/waypoints.json}. Monumenta's old
	 * overworld waypoints go to the shard whose map fits them ({@link OverworldSplit}, maps from
	 * {@code worldMap} = Xaero's {@code world-map} folder, and CakeMap's own); waypoints an earlier
	 * import left in the overworld are moved too.
	 */
	public static int importWaypoints(Path minimap, Path worldMap, Path root, String maps, LiveStore live) throws IOException {
		if (!Files.isDirectory(minimap)) {
			return 0;
		}
		int total = 0;
		try (DirectoryStream<Path> servers = Files.newDirectoryStream(minimap, Files::isDirectory)) {
			for (Path server : servers) {
				String key = serverKey(server.getFileName().toString());
				if (key == null) {
					continue;
				}
				List<Waypoint> found = new ArrayList<>();
				try (DirectoryStream<Path> dims = Files.newDirectoryStream(server, d -> Files.isDirectory(d) && d.getFileName().toString().startsWith("dim%"))) {
					for (Path dim : dims) {
						String dimension = WorldKeys.fromXaeroDimension(dim.getFileName().toString());
						if (dimension == null) {
							continue;
						}
						try (DirectoryStream<Path> files = Files.newDirectoryStream(dim, "*.txt")) {
							for (Path f : files) {
								found.addAll(XaeroWaypoints.parse(Files.readString(f), dimension));
							}
						}
					}
				}
				if (found.isEmpty()) {
					continue;
				}
				for (Waypoint w : found) {
					w.maps = maps;
				}
				List<Waypoint> moved = new ArrayList<>();
				if (key.equals(WorldKeys.MONUMENTA) && found.stream().anyMatch(w -> OverworldSplit.OVERWORLD.equals(w.dimension))) {
					OverworldSplit split = new OverworldSplit(OverworldSplit.worldMapServers(worldMap, key), root.resolve(key));
					for (Waypoint w : found) {
						if (OverworldSplit.OVERWORLD.equals(w.dimension)) {
							String d = split.shardOf(w);
							if (d != null) {
								w.dimension = d;
								moved.add(w);
							}
						}
					}
					CakeMapCore.LOG.info("CakeMap: {} of Monumenta's old overworld waypoints placed on their shards", moved.size());
				}
				ToIntFunction<WaypointStore> change = store -> {
					store.moveImported(OverworldSplit.OVERWORLD, moved);
					return store.addAllNew(found);
				};
				int n = live == null ? -1 : live.apply(key, change);
				if (n >= 0) {
					total += n;
					continue;
				}
				WaypointStore store = new WaypointStore(root.resolve(key).resolve("waypoints.json"));
				store.load();
				total += change.applyAsInt(store);
				store.save();
			}
		}
		return total;
	}

	static String serverKey(String xaeroFolder) {
		String k = WorldKeys.fromXaeroServerFolder(xaeroFolder);
		if (k != null) {
			return k;
		}
		return xaeroFolder.equals("backup") ? null : WorldKeys.singleplayer(xaeroFolder);
	}

	/**
	 * {@code world-map/<server>/<dim>/<mw>/<rx>_<rz>.zip} → {@code <root>/<server>/<ns>/<path>/<rx>_<rz>.cmr}.
	 * {@code onlyServer} limits it to one server key (null = all).
	 */
	public static void importMaps(Path worldMap, Path root, Progress p, String onlyServer) throws IOException {
		if (!Files.isDirectory(worldMap)) {
			return;
		}
		// target file → the Xaero files that go into it (one per multiworld folder)
		Map<Path, List<Path>> jobs = new HashMap<>();
		Map<Path, int[]> coords = new HashMap<>();
		try (DirectoryStream<Path> servers = Files.newDirectoryStream(worldMap, Files::isDirectory)) {
			for (Path server : servers) {
				String key = serverKey(server.getFileName().toString());
				if (key == null || (onlyServer != null && !onlyServer.equals(key))) {
					continue;
				}
				try (DirectoryStream<Path> dims = Files.newDirectoryStream(server, Files::isDirectory)) {
					for (Path dim : dims) {
						String dimension = WorldKeys.fromXaeroDimension(dim.getFileName().toString());
						if (dimension == null) {
							continue;
						}
						Path target = root.resolve(key).resolve(WorldKeys.dimensionFolder(dimension));
						try (DirectoryStream<Path> worlds = Files.newDirectoryStream(dim, Files::isDirectory)) {
							for (Path mw : worlds) {
								try (DirectoryStream<Path> zips = Files.newDirectoryStream(mw, "*.zip")) {
									for (Path zip : zips) {
										Matcher m = REGION.matcher(zip.getFileName().toString());
										if (m.matches()) {
											int rx = Integer.parseInt(m.group(1));
											int rz = Integer.parseInt(m.group(2));
											Path out = target.resolve(rx + "_" + rz + ".cmr");
											jobs.computeIfAbsent(out, k -> new ArrayList<>()).add(zip);
											coords.put(out, new int[] {rx, rz});
										}
									}
								}
							}
						}
					}
				}
			}
		}
		p.total = jobs.size();
		for (Map.Entry<Path, List<Path>> e : jobs.entrySet()) {
			Path out = e.getKey();
			int[] c = coords.get(out);
			try {
				RegionData target = Files.exists(out) ? RegionStore.readFile(out, c[0], c[1]) : new RegionData(c[0], c[1]);
				int before = target.knownCount();
				for (Path zip : e.getValue()) {
					try (InputStream in = new BufferedInputStream(Files.newInputStream(zip), 1 << 16)) {
						target.mergeFrom(XaeroRegionReader.readZip(in, c[0], c[1]), false);
					} catch (IOException | RuntimeException ex) {
						p.failed++;
						CakeMapCore.LOG.warn("CakeMap: Xaero region {} skipped: {}", zip, ex.toString());
					}
				}
				if (target.knownCount() > before || !Files.exists(out)) {
					RegionStore.writeFile(out, target);
				}
				p.regions++;
			} catch (IOException | RuntimeException ex) {
				p.failed++;
				CakeMapCore.LOG.warn("CakeMap: importing {} failed: {}", out, ex.toString());
			}
			p.done.incrementAndGet();
		}
	}

	/** For the import screen: how many waypoint files and map regions are there. */
	public static int[] count(Path xaero) {
		int[] n = {0, 0};
		countWaypointFiles(xaero, n);
		countWaypointFiles(gameDir(xaero).resolve(LEGACY_WAYPOINTS), n);
		try (var s = Files.walk(xaero.resolve("world-map"), 4)) {
			s.forEach(f -> {
				if (REGION.matcher(f.getFileName().toString()).matches()) {
					n[1]++;
				}
			});
		} catch (IOException ignored) {
			// no maps
		}
		return n;
	}

	private static void countWaypointFiles(Path dir, int[] n) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var s = Files.walk(dir, 4)) {
			s.forEach(f -> {
				String name = f.getFileName().toString();
				if (name.endsWith(".txt") && f.getParent() != null && f.getParent().getFileName().toString().startsWith("dim%")) {
					n[0]++;
				}
			});
		} catch (IOException ignored) {
			// counted what we could
		}
	}
}
