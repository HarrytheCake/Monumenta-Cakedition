package io.github.harrythecake.cakemap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.harrythecake.cakemap.core.MatchCheck;
import io.github.harrythecake.cakemap.core.WaypointStore;
import io.github.harrythecake.cakemap.core.WorldKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

/**
 * What CakeMap knows while connected to one server: its folder, the waypoints, the shards whose
 * static map is revealed, and per dimension the dynamic map store and the static map's match check.
 */
public final class MapSession {
	private static final Gson GSON = new Gson();

	public final String server;
	public final Path dir;
	public final WaypointStore waypoints;
	private final TreeSet<String> revealed = new TreeSet<>();
	private final Map<String, RegionStore> stores = new HashMap<>();
	private final Map<String, MatchCheck> checks = new HashMap<>();
	private String dimension = "";
	private boolean announcedMismatch;

	MapSession(String server, Path root) {
		this.server = server;
		this.dir = root.resolve(server);
		this.waypoints = new WaypointStore(dir.resolve("waypoints.json"));
		waypoints.load();
		Path r = dir.resolve("revealed.json");
		if (Files.exists(r)) {
			try {
				TreeSet<String> s = GSON.fromJson(Files.readString(r), new TypeToken<TreeSet<String>>() {
				}.getType());
				if (s != null) {
					revealed.addAll(s);
				}
			} catch (IOException | RuntimeException e) {
				CakeMap.LOG.warn("CakeMap: can't read {}: {}", r, e.toString());
			}
		}
	}

	/** The server key of the current connection, or null outside a world. */
	public static String currentServerKey(Minecraft mc) {
		IntegratedServer sp = mc.getSingleplayerServer();
		if (sp != null) {
			Path folder = sp.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return WorldKeys.singleplayer(folder == null ? "world" : folder.toString());
		}
		ServerData info = mc.getCurrentServer();
		if (info != null) {
			return WorldKeys.server(info.ip);
		}
		return mc.level == null ? null : "mp_unknown";
	}

	public String dimension() {
		return dimension;
	}

	/** Called every tick with the player's dimension; entering a shard reveals its static map. */
	void enter(String dim) {
		if (dim.equals(dimension)) {
			return;
		}
		dimension = dim;
		announcedMismatch = false;
		if (revealed.add(dim)) {
			saveRevealed();
		}
		checks.computeIfAbsent(dim, d -> new MatchCheck()).reset();
	}

	public boolean revealed(String dim) {
		return revealed.contains(dim);
	}

	public java.util.Set<String> revealedSet() {
		return java.util.Collections.unmodifiableSet(revealed);
	}

	private void saveRevealed() {
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("revealed.json"), GSON.toJson(revealed) + "\n");
		} catch (IOException e) {
			CakeMap.LOG.warn("CakeMap: can't save revealed shards: {}", e.toString());
		}
	}

	public RegionStore store() {
		return store(dimension);
	}

	public RegionStore store(String dim) {
		return stores.computeIfAbsent(dim, d -> new RegionStore(dir.resolve(WorldKeys.dimensionFolder(d)), d));
	}

	public MatchCheck check(String dim) {
		return checks.computeIfAbsent(dim, d -> new MatchCheck());
	}

	/** A column the scanner saw, against the static map's sample there. */
	void sampleSeen(int x, int z, int height) {
		StaticMaps.Shard shard = StaticMaps.shard(server, dimension);
		if (shard == null) {
			return;
		}
		MatchCheck c = check(dimension);
		boolean before = c.matches();
		c.sample(height, shard.samples().heightAt(x, z));
		if (before && !c.matches() && !announcedMismatch) {
			announcedMismatch = true;
			CakeMap.LOG.info("CakeMap: the static map of {} doesn't match this world ({} of {} samples agree); showing the dynamic map",
					dimension, c.agree(), c.total());
			CakeMap.notice(net.minecraft.network.chat.Component.translatable("cakemap.static.mismatch"));
		}
	}

	/** The waypoints to draw over the map of {@code dim} that is shown now, bottom to top. */
	public java.util.List<io.github.harrythecake.cakemap.core.Waypoint> shownWaypoints(String dim) {
		return waypoints.shown(dim, showStatic(dim));
	}

	/** Should the static map of {@code dim} be drawn now? */
	public boolean showStatic(String dim) {
		if (CakeMap.settings().mode != io.github.harrythecake.cakemap.core.MapSettings.Mode.STATIC) {
			return false;
		}
		if (StaticMaps.shard(server, dim) == null || !revealed(dim)) {
			return false;
		}
		return !dim.equals(dimension) || check(dim).matches();
	}

	void tick(int playerRx, int playerRz) {
		store().tick(playerRx, playerRz, false);
		for (Map.Entry<String, RegionStore> e : stores.entrySet()) {
			if (!e.getKey().equals(dimension)) {
				e.getValue().tick(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, true);
			}
		}
	}

	void close() {
		for (RegionStore s : stores.values()) {
			s.close();
		}
		stores.clear();
		try {
			waypoints.save();
		} catch (RuntimeException e) {
			CakeMap.LOG.warn("CakeMap: {}", e.getMessage());
		}
	}

	/** Every dimension with a map here (dynamic files, or a static map that is revealed). */
	public java.util.List<String> dimensions() {
		TreeSet<String> out = new TreeSet<>();
		out.add(dimension);
		out.addAll(stores.keySet());
		for (String d : revealed) {
			if (StaticMaps.shard(server, d) != null) {
				out.add(d);
			}
		}
		try (var ns = Files.newDirectoryStream(dir, Files::isDirectory)) {
			for (Path n : ns) {
				try (var ps = Files.newDirectoryStream(n, Files::isDirectory)) {
					for (Path p : ps) {
						out.add(n.getFileName() + ":" + p.getFileName());
					}
				}
			}
		} catch (IOException ignored) {
			// no folder yet
		}
		out.remove("");
		return new java.util.ArrayList<>(out);
	}
}
