package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WorldKeys;
import io.github.harrythecake.cakemap.xaero.XaeroRegionReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Before Monumenta gave each shard its own dimension, every shard said "minecraft:overworld", so
 * Xaero kept the waypoints of the Valley, the Isles, the Ring… in one folder ({@code dim%0}), and
 * the map of all of them in one overworld map. This sends each of those waypoints to its shard:
 * <ol>
 * <li>the shard whose map around the waypoint is the same as the old overworld map there (the
 * player drew that part of the overworld map while standing on that shard), else</li>
 * <li>the shard with explored ground near the waypoint's height (only when one clearly fits; the
 * waypoint's height is often a typed-in coordinate, under a roof, so this comes second), else</li>
 * <li>the only shard that knows the spot at all (a waypoint in a cave).</li>
 * </ol>
 * Anything else stays in the overworld.
 *
 * <p>Maps are read from Xaero's World Map ({@code world-map/<server>/<ns>$<path>/<mw>/<rx>_<rz>.zip},
 * the overworld is {@code null}) and from CakeMap's own ({@code <root>/<server>/<ns>/<path>/<rx>_<rz>.cmr});
 * only Monumenta's own shards ({@code monumenta:*}, not plots) are candidates.
 */
final class OverworldSplit {
	static final String OVERWORLD = "minecraft:overworld";
	/** Columns this many blocks around the waypoint are compared. */
	static final int AROUND = 4;
	/**
	 * By ground: the nearest shard wins when it is within FIT blocks and MARGIN closer than the next,
	 * or within LOOSE_FIT and LOOSE_MARGIN closer (the ground is under a roof, a tree…).
	 */
	static final int FIT = 6;
	static final int MARGIN = 3;
	static final int LOOSE_FIT = 12;
	static final int LOOSE_MARGIN = 10;
	private static final int CACHED = 24;

	/** A decoded region and its state names. */
	private record Region(RegionData data, List<String> states) {
		boolean known(int i) {
			return data.known(i);
		}

		String state(int i) {
			return states.get(data.state[i]);
		}
	}

	/** Dimension (the candidate shards and the overworld) → the folders with its map regions. */
	private final Map<String, List<Path>> maps = new HashMap<>();
	private final Set<String> shards = new LinkedHashSet<>();
	/** Decoded regions (about 2 MB each), the most recently used few; and the regions nobody has. */
	private final Map<String, Region> regions = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Region> e) {
			return size() > CACHED;
		}
	};
	private final Set<String> missing = new HashSet<>();

	/**
	 * @param worldMapServers Xaero World Map server folders of this server (Multiplayer_…)
	 * @param ownMap          CakeMap's folder of this server ({@code <root>/<server>}), may be null
	 */
	OverworldSplit(List<Path> worldMapServers, Path ownMap) throws IOException {
		for (Path server : worldMapServers) {
			if (!Files.isDirectory(server)) {
				continue;
			}
			try (DirectoryStream<Path> dims = Files.newDirectoryStream(server, Files::isDirectory)) {
				for (Path dim : dims) {
					String d = WorldKeys.fromXaeroDimension(dim.getFileName().toString());
					if (!candidate(d) && !OVERWORLD.equals(d)) {
						continue;
					}
					try (DirectoryStream<Path> worlds = Files.newDirectoryStream(dim, Files::isDirectory)) {
						for (Path mw : worlds) {
							add(d, mw);
						}
					}
				}
			}
		}
		if (ownMap != null) {
			add(OVERWORLD, ownMap.resolve(WorldKeys.dimensionFolder(OVERWORLD)));
			Path ns = ownMap.resolve("monumenta");
			if (Files.isDirectory(ns)) {
				try (DirectoryStream<Path> dims = Files.newDirectoryStream(ns, Files::isDirectory)) {
					for (Path dim : dims) {
						String d = "monumenta:" + dim.getFileName();
						if (candidate(d)) {
							add(d, dim);
						}
					}
				}
			}
		}
	}

	private void add(String dimension, Path folder) {
		if (!Files.isDirectory(folder)) {
			return;
		}
		maps.computeIfAbsent(dimension, k -> new ArrayList<>()).add(folder);
		if (!OVERWORLD.equals(dimension)) {
			shards.add(dimension);
		}
	}

	private static boolean candidate(String dimension) {
		return dimension != null && dimension.startsWith("monumenta:") && !WorldKeys.personal(dimension);
	}

	Set<String> shards() {
		return shards;
	}

	/** The shard this overworld waypoint belongs to, or null when that can't be told. */
	String shardOf(Waypoint w) {
		String byMap = sameMap(w.x, w.z);
		return byMap != null ? byMap : byGround(w);
	}

	/**
	 * The shard whose columns around (x, z) are the old overworld map's: the most matching columns
	 * (same top block, height within 1), at least 40 % of what the overworld map knows there and
	 * twice as many as any other shard.
	 */
	String sameMap(int x, int z) {
		int known = 0;
		Map<String, Integer> match = new HashMap<>();
		for (int dx = -AROUND; dx <= AROUND; dx++) {
			for (int dz = -AROUND; dz <= AROUND; dz++) {
				int bx = x + dx;
				int bz = z + dz;
				Region o = region(OVERWORLD, bx, bz);
				int i = index(bx, bz);
				if (o == null || !o.known(i)) {
					continue;
				}
				known++;
				String state = o.state(i);
				int h = o.data().height[i];
				for (String d : shards) {
					Region r = region(d, bx, bz);
					if (r != null && r.known(i) && Math.abs(r.data().height[i] - h) <= 1 && r.state(i).equals(state)) {
						match.merge(d, 1, Integer::sum);
					}
				}
			}
		}
		String best = null;
		int first = 0;
		int second = 0;
		for (Map.Entry<String, Integer> e : match.entrySet()) {
			if (e.getValue() > first) {
				second = first;
				first = e.getValue();
				best = e.getKey();
			} else if (e.getValue() > second) {
				second = e.getValue();
			}
		}
		return best != null && first * 10 >= known * 4 && first >= 2 * second + 1 ? best : null;
	}

	/** Rules 2 and 3: the waypoint's height against each shard's ground. */
	private String byGround(Waypoint w) {
		String best = null;
		int bestDy = Integer.MAX_VALUE;
		int secondDy = Integer.MAX_VALUE;
		List<String> knowing = new ArrayList<>();
		for (String d : shards) {
			int dy = groundDistance(d, w.x, w.y, w.z);
			if (dy == Integer.MAX_VALUE) {
				continue;
			}
			knowing.add(d);
			if (dy < bestDy) {
				secondDy = bestDy;
				bestDy = dy;
				best = d;
			} else if (dy < secondDy) {
				secondDy = dy;
			}
		}
		if (best != null && (bestDy <= FIT || bestDy <= LOOSE_FIT && secondDy - bestDy >= LOOSE_MARGIN)) {
			long margin = (long) secondDy - bestDy;
			return margin >= (bestDy <= FIT ? MARGIN : LOOSE_MARGIN) ? best : null;
		}
		return knowing.size() == 1 ? knowing.get(0) : null;
	}

	/**
	 * How far the waypoint is from the ground of this shard: the smallest |y - 1 - height| over the
	 * known columns around it, or Integer.MAX_VALUE when the shard knows none of them.
	 */
	int groundDistance(String dim, int x, int y, int z) {
		int best = Integer.MAX_VALUE;
		for (int dx = -AROUND; dx <= AROUND; dx++) {
			for (int dz = -AROUND; dz <= AROUND; dz++) {
				Region r = region(dim, x + dx, z + dz);
				int i = index(x + dx, z + dz);
				if (r != null && r.known(i)) {
					best = Math.min(best, Math.abs(y - 1 - r.data().height[i]));
				}
			}
		}
		return best;
	}

	private static int index(int bx, int bz) {
		return RegionData.index(Math.floorMod(bx, RegionData.SIZE), Math.floorMod(bz, RegionData.SIZE));
	}

	/** The region holding block (bx, bz) of this dimension, all its folders merged, or null. */
	private Region region(String dim, int bx, int bz) {
		int rx = Math.floorDiv(bx, RegionData.SIZE);
		int rz = Math.floorDiv(bz, RegionData.SIZE);
		String key = dim + "|" + rx + "|" + rz;
		if (missing.contains(key)) {
			return null;
		}
		Region cached = regions.get(key);
		if (cached != null) {
			return cached;
		}
		RegionData merged = null;
		for (Path folder : maps.getOrDefault(dim, List.of())) {
			RegionData part = null;
			Path zip = folder.resolve(rx + "_" + rz + ".zip");
			Path cmr = folder.resolve(rx + "_" + rz + ".cmr");
			try {
				if (Files.exists(zip)) {
					try (InputStream in = new BufferedInputStream(Files.newInputStream(zip), 1 << 16)) {
						part = XaeroRegionReader.readZip(in, rx, rz);
					}
				} else if (Files.exists(cmr)) {
					part = RegionStore.readFile(cmr, rx, rz);
				}
			} catch (IOException | RuntimeException e) {
				CakeMapCore.LOG.debug("CakeMap: {} skipped while placing overworld waypoints: {}", folder, e.toString());
			}
			if (part != null) {
				if (merged == null) {
					merged = part;
				} else {
					merged.mergeFrom(part, false);
				}
			}
		}
		if (merged == null) {
			missing.add(key);
			return null;
		}
		Region r = new Region(merged, merged.states());
		regions.put(key, r);
		return r;
	}

	/** Xaero World Map server folders that belong to this CakeMap server key. */
	static List<Path> worldMapServers(Path worldMap, String key) throws IOException {
		List<Path> out = new ArrayList<>();
		if (worldMap != null && Files.isDirectory(worldMap)) {
			try (DirectoryStream<Path> servers = Files.newDirectoryStream(worldMap, Files::isDirectory)) {
				for (Path s : servers) {
					if (key.equals(XaeroImport.serverKey(s.getFileName().toString()))) {
						out.add(s);
					}
				}
			}
		}
		return out;
	}
}
