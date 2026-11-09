package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.BlockStates;
import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.xaero.XaeroRegionReader;
import io.github.harrythecake.cakemap.xaero.XaeroSettings;
import io.github.harrythecake.cakemap.xaero.XaeroWaypoints;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Xaero's files, from hand-made bytes (the format as found in Xaero's World Map 1.39, version 6.8). */
class XaeroTest {
	private static void nbtState(DataOutputStream o, String name, Map<String, String> props) throws IOException {
		o.writeByte(10);
		o.writeUTF("");
		o.writeByte(8);
		o.writeUTF("Name");
		o.writeUTF(name);
		if (!props.isEmpty()) {
			o.writeByte(10);
			o.writeUTF("Properties");
			for (Map.Entry<String, String> e : props.entrySet()) {
				o.writeByte(8);
				o.writeUTF(e.getKey());
				o.writeUTF(e.getValue());
			}
			o.writeByte(0);
		}
		o.writeByte(0);
	}

	private static int params(boolean state, boolean newState, boolean overlay, boolean top, boolean biome, boolean newBiome, int height) {
		int p = 0;
		p |= state ? 1 : 0;
		p |= overlay ? 2 : 0;
		p |= (height & 0xFF) << 12;
		p |= ((height >> 8) & 0xF) << 25;
		p |= biome ? 1 << 20 : 0;
		p |= newState ? 1 << 21 : 0;
		p |= newBiome ? 1 << 22 : 0;
		p |= top ? 1 << 24 : 0;
		return p;
	}

	/** Tile (1, 2), chunk 0 full, chunks 1–15 empty. */
	static byte[] region() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream o = new DataOutputStream(bytes);
		o.writeByte(0xFF);
		o.writeInt(0x00060008);
		o.writeByte((1 << 4) | 2);
		for (int k = 0; k < 16; k++) {
			if (k != 0) {
				o.writeInt(-1);
				continue;
			}
			for (int i = 0; i < 256; i++) {
				if (i == 0) {
					o.writeInt(params(true, true, false, false, true, true, 70));
					nbtState(o, "minecraft:oak_leaves", new java.util.TreeMap<>(Map.of("persistent", "true", "distance", "7")));
					o.writeUTF("minecraft:swamp");
				} else if (i == 1) {
					// grass (no state), on a biome already known
					o.writeInt(params(false, false, false, false, true, false, 300));
					o.writeInt(0);
				} else if (i == 2) {
					// sand under 4 blocks of water: new state, top byte, one water overlay
					o.writeInt(params(true, true, true, true, true, false, 60));
					nbtState(o, "minecraft:sand", Map.of());
					o.writeByte(64);
					o.writeByte(1);
					o.writeInt(0x00000800);
					o.writeInt(0);
				} else if (i == 3) {
					// below zero: -10
					o.writeInt(params(true, false, false, false, false, false, -10 & 0xFFF));
					o.writeInt(1);
				} else {
					o.writeInt(params(true, false, false, false, true, false, 71));
					o.writeInt(0);
					o.writeInt(0);
				}
			}
			o.writeByte(1);
			o.writeInt(Integer.MAX_VALUE);
			o.writeByte(30);
		}
		return bytes.toByteArray();
	}

	@Test
	void readsAHandMadeRegion() throws IOException {
		RegionData r = XaeroRegionReader.read(region(), 4, -1);
		// tile (1, 2) → x 64…, z 128…; pixel i → x + i / 16, z + i % 16
		int first = RegionData.index(64, 128);
		assertEquals("minecraft:oak_leaves[distance=7,persistent=true]", r.stateAt(first));
		assertEquals(70, r.height[first]);
		int grass = RegionData.index(64, 129);
		assertEquals("minecraft:grass_block[snowy=false]", r.stateAt(grass));
		assertEquals(300, r.height[grass], "heights above 255 use the high bits");
		int sand = RegionData.index(64, 130);
		assertEquals("minecraft:sand", r.stateAt(sand));
		assertEquals(BlockStates.WATER, r.states().get(r.overlay[sand]));
		assertEquals(4, r.depth[sand]);
		assertEquals(-10, r.height[RegionData.index(64, 131)]);
		assertEquals(256, r.knownCount());
		assertEquals("minecraft:swamp", r.biomes().get(r.biome[first] & 0xFF));
	}

	@Test
	void refusesOtherVersions() throws IOException {
		byte[] b = region();
		b[2] = 7; // major 7
		assertThrows(IOException.class, () -> XaeroRegionReader.read(b, 0, 0));
	}

	@Test
	void waypoints() {
		String text = """
				#
				#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination
				#
				waypoint:Mock Base:M:-549:68:-299:10:false:0:gui.xaero_default:false:0:0:false
				waypoint:gui.xaero_deathpoint_old:D:567:305:230:0:false:2:gui.xaero_default:false:0:1:true
				waypoint:Gate§§North:G:5:~:7:4:true:0:gui.xaero_default:false:0:0:false
				waypoint:broken
				""";
		List<Waypoint> w = XaeroWaypoints.parse(text, "monumenta:ring");
		assertEquals(2, w.size());
		assertEquals("Mock Base", w.get(0).name);
		assertEquals(-549, w.get(0).x);
		assertEquals(0x4CC764, w.get(0).color, "Xaero's light green → CakeMap's green swatch");
		assertEquals("Gate:North", w.get(1).name);
		assertEquals(64, w.get(1).y, "an unknown y becomes 64");
		assertTrue(w.get(1).hidden);
		assertEquals("xaero", w.get(1).source);
	}

	/**
	 * The import found Xaero's waypoints but lost them: it wrote waypoints.json, then the session
	 * that was open saved its old, empty list over it. Waypoints of the server the player is on now
	 * go into that server's live store; other servers get their file.
	 */
	@Test
	void waypointImportReachesTheLiveStore(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path minimap = tmp.resolve("xaero").resolve("minimap");
		String line = "waypoint:Mock Base:M:1:64:2:12:false:0:gui.xaero_default:false:0:0:false" + System.lineSeparator();
		Path mon = minimap.resolve("Multiplayer_server.playmonumenta.com").resolve("dim%monumenta$valley");
		Files.createDirectories(mon);
		Files.writeString(mon.resolve("mw$default_1.txt"), line);
		Path other = minimap.resolve("Multiplayer_mock.example.org").resolve("dim%0");
		Files.createDirectories(other);
		Files.writeString(other.resolve("mw$default_1.txt"), line);
		Path root = tmp.resolve("cakemap");

		io.github.harrythecake.cakemap.core.WaypointStore live = new io.github.harrythecake.cakemap.core.WaypointStore(
				root.resolve("monumenta").resolve("waypoints.json"));
		live.load();
		XaeroImport.LiveStore liveStore = (key, change) -> key.equals("monumenta") ? change.applyAsInt(live) : -1;
		int n = XaeroImport.importWaypoints(minimap, tmp.resolve("no-world-map"), root, Waypoint.STATIC, liveStore);
		assertEquals(2, n);
		assertEquals(1, live.own().size(), "the open server's waypoints go into its live store");
		assertEquals(Waypoint.STATIC, live.own().get(0).maps, "the chosen map is kept");
		assertFalse(Files.exists(root.resolve("monumenta").resolve("waypoints.json")), "not written behind the live store's back");
		io.github.harrythecake.cakemap.core.WaypointStore file = new io.github.harrythecake.cakemap.core.WaypointStore(
				root.resolve("mp_mock.example.org").resolve("waypoints.json"));
		file.load();
		assertEquals(1, file.own().size(), "another server's waypoints go into its file");
		assertEquals("minecraft:overworld", file.own().get(0).dimension);
		// a second import adds nothing new
		assertEquals(0, XaeroImport.importWaypoints(minimap, tmp.resolve("no-world-map"), root, Waypoint.BOTH, liveStore));
	}

	private static void ground(Path root, String shard, int x, int z, int height) throws IOException {
		Path f = root.resolve("monumenta").resolve(io.github.harrythecake.cakemap.core.WorldKeys.dimensionFolder(
				shard.contains(":") ? shard : "monumenta:" + shard)).resolve("0_0.cmr");
		RegionData r = Files.exists(f) ? RegionStore.readFile(f, 0, 0) : new RegionData(0, 0);
		r.set(x, z, "minecraft:stone", "minecraft:plains", height, null, 0);
		Files.createDirectories(f.getParent());
		RegionStore.writeFile(f, r);
	}

	/**
	 * Monumenta's shards all used to be "minecraft:overworld", so Xaero's dim%0 mixes the Valley, the
	 * Isles… The import puts each of those waypoints on the shard whose map is the old overworld map
	 * there, else whose ground fits it; an earlier
	 * import's overworld copy moves along (keeping its colour); what can't be told stays.
	 */
	@Test
	void overworldWaypointsGoToTheirShard(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
		Path root = tmp.resolve("cakemap");
		ground(root, "valley", 10, 10, 70);
		ground(root, "isles", 10, 10, 40);
		ground(root, "valley", 200, 200, 80); // only the Valley knows this spot
		ground(root, "isles", 400, 400, 63); // both at sea level: a tie
		ground(root, "valley", 400, 400, 63);
		ground(root, "plots", 100, 100, 70); // personal worlds aren't candidates
		// the old overworld map was drawn on the Isles here, so it wins over the Valley's ground
		// (the waypoint is a typed-in coordinate, under a roof)
		for (int dx = -2; dx <= 2; dx++) {
			ground(root, "minecraft:overworld", 300 + dx, 300, 90);
			ground(root, "isles", 300 + dx, 300, 90);
			ground(root, "valley", 300 + dx, 300, 63);
		}
		Path minimap = tmp.resolve("xaero").resolve("minimap");
		Path dim0 = minimap.resolve("Multiplayer_server.playmonumenta.com").resolve("dim%0");
		Files.createDirectories(dim0);
		String nl = System.lineSeparator();
		Files.writeString(dim0.resolve("mw$default_1.txt"), String.join(nl,
				"waypoint:Mock Hill:M:10:71:10:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Shore:M:11:41:9:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Cave:M:200:20:201:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Beach:M:400:64:400:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Unknown:M:5000:64:5000:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Plot:M:100:71:100:0:false:0:gui.xaero_default:false:0:0:false",
				"waypoint:Mock Town:M:300:64:300:0:false:0:gui.xaero_default:false:0:0:false") + nl);

		io.github.harrythecake.cakemap.core.WaypointStore store = new io.github.harrythecake.cakemap.core.WaypointStore(
				root.resolve("monumenta").resolve("waypoints.json"));
		store.load();
		Waypoint earlier = new Waypoint("Mock Hill", io.github.harrythecake.cakemap.core.WaypointKind.PLAYER, "minecraft:overworld", 10, 71, 10);
		earlier.source = "xaero";
		earlier.color = 0xE5484D;
		store.add(earlier);
		store.save();

		XaeroImport.importWaypoints(minimap, tmp.resolve("no-world-map"), root, Waypoint.BOTH, null);
		store = new io.github.harrythecake.cakemap.core.WaypointStore(root.resolve("monumenta").resolve("waypoints.json"));
		store.load();
		Map<String, String> where = new java.util.HashMap<>();
		for (Waypoint w : store.own()) {
			assertTrue(where.put(w.name, w.dimension) == null, "one copy of " + w.name);
		}
		assertEquals("monumenta:valley", where.get("Mock Hill"));
		assertEquals("monumenta:isles", where.get("Mock Shore"));
		assertEquals("monumenta:valley", where.get("Mock Cave"));
		assertEquals("minecraft:overworld", where.get("Mock Beach"));
		assertEquals("minecraft:overworld", where.get("Mock Unknown"));
		assertEquals("minecraft:overworld", where.get("Mock Plot"));
		assertEquals("monumenta:isles", where.get("Mock Town"), "the old overworld map decides first");
		assertEquals(0xE5484D, store.own().stream().filter(w -> w.name.equals("Mock Hill")).findFirst().orElseThrow().color,
				"the earlier copy moved, with its colour");
	}

	/** Local only: where a real Xaero folder's overworld waypoints go (CAKEMAP_XAERO, else the repo's xaero/ sample; skipped without dim%0). */
	@Test
	void realOverworldSplit() throws IOException {
		String env = System.getenv("CAKEMAP_XAERO"); // e.g. an instance's .minecraft/xaero
		Path xaero = env != null ? Path.of(env) : Path.of("..", "xaero", "xaero");
		Path dim0 = xaero.resolve("minimap").resolve("Multiplayer_server.playmonumenta.com").resolve("dim%0");
		Assumptions.assumeTrue(Files.isDirectory(dim0), "no local Xaero sample");
		OverworldSplit split = new OverworldSplit(OverworldSplit.worldMapServers(xaero.resolve("world-map"), "monumenta"), null);
		Map<String, Integer> count = new java.util.TreeMap<>();
		try (Stream<Path> files = Files.list(dim0)) {
			for (Path f : (Iterable<Path>) files::iterator) {
				for (Waypoint w : XaeroWaypoints.parse(Files.readString(f), "minecraft:overworld")) {
					String d = split.shardOf(w);
					count.merge(String.valueOf(d), 1, Integer::sum);
					if (System.getenv("CAKEMAP_VERBOSE") != null) {
						StringBuilder sb = new StringBuilder();
						for (String c : new java.util.TreeSet<>(split.shards())) {
							int dy = split.groundDistance(c, w.x, w.y, w.z);
							if (dy != Integer.MAX_VALUE) {
								sb.append(' ').append(c.substring(c.indexOf(':') + 1)).append('=').append(dy);
							}
						}
						System.out.println("SPLIT " + d + "  " + w.x + " " + w.y + " " + w.z + " map=" + split.sameMap(w.x, w.z) + " |" + sb);
					}
				}
			}
		}
		System.out.println("SPLIT " + count);
		assertTrue(count.keySet().stream().anyMatch(k -> k.startsWith("monumenta:")));
	}

	@Test
	void settingsAndKeys() {
		String cfg = """
				//comment
				minimap:true
				caveMaps:0
				lockNorth:true
				minimapShape:1
				minimapOpacity:75.0
				showWaypoints:false
				module;id=xaerominimap:minimap;active=true;x=12;y=37;centered=false;fromRight=true;fromBottom=false;
				""";
		MapSettings s = new MapSettings();
		List<String> changed = XaeroSettings.apply(cfg, s);
		assertEquals(MapSettings.Shape.CIRCLE, s.shape);
		assertFalse(s.rotate);
		assertEquals(MapSettings.Cave.OFF, s.cave);
		assertEquals(0.75, s.opacity, 1e-9);
		assertFalse(s.waypointsOnMinimap);
		assertEquals(MapSettings.Corner.TOP_RIGHT, s.corner);
		assertEquals(12, s.offsetX);
		assertEquals(37, s.offsetY);
		assertTrue(changed.contains("position"));
		Map<String, String> keys = XaeroSettings.keys("key_gui.xaero_open_map:key.keyboard.m\nkey_gui.xaero_new_waypoint:key.keyboard.j\nkey_other:key.keyboard.x\n");
		assertEquals("key.keyboard.j", keys.get("new_waypoint"));
		assertEquals("key.keyboard.m", keys.get("world_map"));
		assertEquals(2, keys.size());
	}

	/** Local only: every region of the repo's xaero/ sample parses (skipped where the folder isn't there). */
	@Test
	void realRegionsParse() throws IOException {
		Path root = Path.of("..", "xaero", "xaero", "world-map");
		Assumptions.assumeTrue(Files.isDirectory(root), "no local Xaero sample");
		int n = 0;
		try (Stream<Path> s = Files.walk(root)) {
			for (Path zip : (Iterable<Path>) s.filter(p -> p.toString().endsWith(".zip")).limit(40)::iterator) {
				try (InputStream in = Files.newInputStream(zip)) {
					RegionData r = XaeroRegionReader.readZip(in, 0, 0);
					assertTrue(r.knownCount() >= 0);
					n++;
				}
			}
		}
		assertTrue(n > 0);
	}
}
