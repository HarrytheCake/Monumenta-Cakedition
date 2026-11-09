package io.github.harrythecake.cakemap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.RegionShader;
import io.github.harrythecake.cakemap.core.StaticSamples;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointStore;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static maps and built-in waypoints from resource packs.
 * <ul>
 * <li>{@code assets/<any>/cakemap/static/<dim ns>/<dim path>/meta.json}: {"server": "monumenta", "title": …}</li>
 * <li>{@code …/<rx>_<rz>.png}: one region, 512 x 512, one pixel per block, transparent where unknown</li>
 * <li>{@code …/samples.bin}: heights for the match check ({@link StaticSamples})</li>
 * <li>{@code assets/<any>/cakemap/waypoints/<server>.json}: built-in waypoints</li>
 * </ul>
 */
public class StaticMaps implements ResourceManagerReloadListener {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(CakeMap.MOD_ID, "static_maps");

	private static final Pattern TILE = Pattern.compile(".*/(-?\\d+)_(-?\\d+)\\.png");

	/** One shard's static map. */
	public record Shard(String server, String dimension, String title, Map<Long, Identifier> tiles, StaticSamples samples) {
		public boolean has(int rx, int rz) {
			return tiles.containsKey(StaticSamples.key(rx, rz));
		}
	}

	private static volatile Map<String, Shard> shards = Map.of();
	private static volatile Map<String, List<Waypoint>> builtinWaypoints = Map.of();
	private static int generation;

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		// waypoint icons may come from the new resource packs
		Minecraft.getInstance().execute(Icons::clear);
		Map<String, Shard> found = new HashMap<>();
		Map<Identifier, Resource> metas = manager.listResources("cakemap/static", id -> id.getPath().endsWith("/meta.json"));
		for (Map.Entry<Identifier, Resource> e : metas.entrySet()) {
			String path = e.getKey().getPath(); // cakemap/static/<ns>/<path>/meta.json
			String[] parts = path.split("/");
			if (parts.length != 5) {
				continue;
			}
			String dim = parts[2] + ":" + parts[3];
			String folder = path.substring(0, path.length() - "meta.json".length());
			try (InputStream in = e.getValue().open()) {
				JsonObject meta = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				String server = meta.has("server") ? meta.get("server").getAsString() : "";
				String title = meta.has("title") ? meta.get("title").getAsString() : dim;
				Map<Long, Identifier> tiles = new HashMap<>();
				String ns = e.getKey().getNamespace();
				for (Identifier tile : manager.listResources(folder.substring(0, folder.length() - 1), id -> id.getPath().endsWith(".png")).keySet()) {
					if (!tile.getNamespace().equals(ns)) {
						continue;
					}
					Matcher m = TILE.matcher(tile.getPath());
					if (m.matches()) {
						tiles.put(StaticSamples.key(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))), tile);
					}
				}
				StaticSamples samples = new StaticSamples();
				Optional<Resource> sr = manager.getResource(Identifier.fromNamespaceAndPath(ns, folder + "samples.bin"));
				if (sr.isPresent()) {
					try (InputStream sin = sr.get().open()) {
						samples = StaticSamples.read(sin);
					}
				}
				found.put(server + "|" + dim, new Shard(server, dim, title, tiles, samples));
			} catch (IOException | RuntimeException ex) {
				CakeMap.LOG.warn("CakeMap: static map {} can't be read: {}", e.getKey(), ex.toString());
			}
		}
		Map<String, List<Waypoint>> wps = new HashMap<>();
		for (Map.Entry<Identifier, List<Resource>> e : manager.listResourceStacks("cakemap/waypoints", id -> id.getPath().endsWith(".json")).entrySet()) {
			String file = e.getKey().getPath();
			String server = file.substring(file.lastIndexOf('/') + 1, file.length() - ".json".length());
			for (Resource r : e.getValue()) {
				try (InputStream in = r.open()) {
					wps.computeIfAbsent(server, k -> new ArrayList<>()).addAll(WaypointStore.parseBuiltin(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
				} catch (IOException | RuntimeException ex) {
					CakeMap.LOG.warn("CakeMap: built-in waypoints {} can't be read: {}", e.getKey(), ex.toString());
				}
			}
		}
		shards = found;
		builtinWaypoints = wps;
		generation++;
		MapTextures.clear("static/");
		GameColors.INSTANCE.clear();
		CakeMap.LOG.info("CakeMap: {} static map(s), built-in waypoints for {}", found.size(), wps.keySet());
		CakeMap.onRender(CakeMap::builtinWaypointsChanged);
	}

	public static Shard shard(String server, String dim) {
		Shard s = shards.get(server + "|" + dim);
		if (s == null && CakeMap.settings().devMode) {
			s = shards.get("*|" + dim);
		}
		return s;
	}

	public static List<Shard> all() {
		return new ArrayList<>(shards.values());
	}

	public static List<Waypoint> builtin(String server) {
		return builtinWaypoints.getOrDefault(server, List.of());
	}

	/** The texture of one static region (loaded from its PNG on a worker). */
	public static MapTextures.Tex texture(Shard shard, int rx, int rz, boolean full) {
		Identifier id = shard.tiles().get(StaticSamples.key(rx, rz));
		if (id == null) {
			return null;
		}
		return MapTextures.get("static/" + shard.server() + "/" + shard.dimension() + "/" + rx + "_" + rz, generation, full,
				() -> CompletableFuture.supplyAsync(() -> load(id), MapTextures.SHADE));
	}

	private static MapTextures.Pixels load(Identifier id) {
		Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(id);
		if (r.isEmpty()) {
			return null;
		}
		try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
			if (img.getWidth() != RegionData.SIZE || img.getHeight() != RegionData.SIZE) {
				CakeMap.LOG.warn("CakeMap: static tile {} is {}x{}, not 512x512", id, img.getWidth(), img.getHeight());
				return null;
			}
			int[] argb = new int[RegionData.AREA];
			for (int z = 0; z < RegionData.SIZE; z++) {
				for (int x = 0; x < RegionData.SIZE; x++) {
					argb[z * RegionData.SIZE + x] = img.getPixel(x, z); // ARGB since 1.21.2
				}
			}
			return new MapTextures.Pixels(argb, RegionShader.quarter(argb, RegionData.SIZE));
		} catch (IOException e) {
			CakeMap.LOG.warn("CakeMap: static tile {} can't be read: {}", id, e.toString());
			return null;
		}
	}
}
