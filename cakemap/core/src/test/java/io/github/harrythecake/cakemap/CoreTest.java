package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.BlockStates;
import io.github.harrythecake.cakemap.core.ChatCoords;
import io.github.harrythecake.cakemap.core.MapSettings;
import io.github.harrythecake.cakemap.core.MatchCheck;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.RegionShader;
import io.github.harrythecake.cakemap.core.StaticSamples;
import io.github.harrythecake.cakemap.core.Waypoint;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;
import io.github.harrythecake.cakemap.core.WaypointStore;
import io.github.harrythecake.cakemap.core.WorldKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of CakeMap that need no game. Test data uses made-up names. */
class CoreTest {
	@TempDir
	Path dir;

	@Test
	void regionRoundTripAndMerge() throws IOException {
		RegionData r = new RegionData(-3, 7);
		assertTrue(r.set(0, 0, "minecraft:stone", "minecraft:plains", 64, null, 0));
		assertFalse(r.set(0, 0, "minecraft:stone", "minecraft:plains", 64, null, 0), "same column again is no change");
		r.set(511, 511, "minecraft:sand", "minecraft:beach", -12, BlockStates.WATER, 5);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		r.write(out);
		RegionData back = RegionData.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(-3, back.rx);
		assertEquals(7, back.rz);
		int i = RegionData.index(511, 511);
		assertEquals("minecraft:sand", back.stateAt(i));
		assertEquals(-12, back.height[i]);
		assertEquals(5, back.depth[i]);
		assertEquals(2, back.knownCount());

		RegionData other = new RegionData(-3, 7);
		other.set(0, 0, "minecraft:dirt", "minecraft:plains", 70, null, 0);
		other.set(1, 0, "minecraft:dirt", "minecraft:plains", 70, null, 0);
		back.mergeFrom(other, false);
		assertEquals("minecraft:stone", back.stateAt(RegionData.index(0, 0)), "merge keeps what is known");
		assertEquals("minecraft:dirt", back.stateAt(RegionData.index(1, 0)));
		assertEquals(3, back.knownCount());
	}

	@Test
	void worldKeys() {
		assertEquals("monumenta", WorldKeys.server("server.playmonumenta.com:25565"));
		assertEquals("monumenta", WorldKeys.server("PLAYMONUMENTA.COM"));
		assertEquals("mp_example.org", WorldKeys.server("example.org"));
		assertEquals("monumenta", WorldKeys.fromXaeroServerFolder("Multiplayer_server.playmonumenta.com"));
		assertEquals("monumenta:valley", WorldKeys.fromXaeroDimension("dim%monumenta$valley"));
		assertEquals("monumenta:valley", WorldKeys.fromXaeroDimension("monumenta$valley"));
		assertEquals("minecraft:overworld", WorldKeys.fromXaeroDimension("null"));
		assertEquals("minecraft:the_nether", WorldKeys.fromXaeroDimension("DIM-1"));
		assertEquals("monumenta/valley", WorldKeys.dimensionFolder("monumenta:valley"));
		assertTrue(WorldKeys.personal("minecraft:plot1234"));
		assertTrue(WorldKeys.personal("minecraft:guildplot12"));
		assertTrue(WorldKeys.personal("monumenta:plots"));
		assertFalse(WorldKeys.personal("monumenta:valley"));
		assertFalse(WorldKeys.personal("monumenta:orange_exalted"));
	}

	@Test
	void chatCoordinates() {
		List<ChatCoords.Found> f = ChatCoords.find("Head to x: -1234, y: 64, z: 567 to find Mock_Pl4yer");
		assertEquals(1, f.size());
		assertEquals(-1234, f.get(0).x());
		assertEquals(64, f.get(0).y());
		assertEquals(567, f.get(0).z());
		f = ChatCoords.find("qwertfox: meet at (-120, 70, 340) please");
		assertEquals(1, f.size());
		assertEquals(340, f.get(0).z());
		f = ChatCoords.find("the gate is at -812 101 455");
		assertEquals(1, f.size());
		f = ChatCoords.find("-812/101/455");
		assertEquals(1, f.size());
		assertTrue(ChatCoords.find("I got 1 2 3 drops").isEmpty(), "small numbers aren't places");
		assertTrue(ChatCoords.find("score 12 34 56").isEmpty(), "plain numbers without separators or minus aren't places");
		assertTrue(ChatCoords.find("(100, 999, 200)").isEmpty(), "y must look like a height");
		f = ChatCoords.find("x -40 z 900");
		assertEquals(1, f.size());
		assertNull(f.get(0).y());
	}

	@Test
	void matchCheck() {
		MatchCheck m = new MatchCheck();
		assertTrue(m.matches(), "trusted until decided");
		for (int i = 0; i < MatchCheck.MIN_SAMPLES; i++) {
			m.sample(70, 71);
		}
		assertTrue(m.decided());
		assertTrue(m.matches());
		m.reset();
		for (int i = 0; i < MatchCheck.MIN_SAMPLES; i++) {
			m.sample(70, i % 3 == 0 ? 70 : 120);
		}
		assertFalse(m.matches(), "a third agreeing is not enough");
		m.reset();
		m.sample(70, Integer.MIN_VALUE);
		assertEquals(0, m.total(), "no static sample, no count");
	}

	@Test
	void staticSamples() throws IOException {
		RegionData r = new RegionData(1, -2);
		r.set(4, 4, "minecraft:stone", "", 88, null, 0);
		StaticSamples s = new StaticSamples();
		s.put(1, -2, RegionShader.samples(r));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		s.write(out);
		StaticSamples back = StaticSamples.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(88, back.heightAt(512 + 4, -1024 + 4));
		assertEquals(Integer.MIN_VALUE, back.heightAt(512 + 12, -1024 + 4), "unknown sample");
		assertEquals(Integer.MIN_VALUE, back.heightAt(512 + 5, -1024 + 4), "not a sample column");
	}

	@Test
	void shading() {
		RegionData r = new RegionData(0, 0);
		r.set(10, 10, "a", "b1", 64, null, 0);
		r.set(10, 11, "a", "b1", 66, null, 0); // higher than its north neighbour: brighter
		r.set(10, 12, "a", "b1", 60, null, 0); // lower: darker
		r.set(20, 20, "a", "b1", 64, "w", 3);
		RegionShader.Colors c = new RegionShader.Colors() {
			public int base(String s) {
				return s.equals("w") ? 0xFFFFFF : 0x808080;
			}

			public int tint(String s) {
				return s.equals("w") ? RegionShader.TINT_WATER : RegionShader.TINT_NONE;
			}

			public int grass(String b) {
				return 0x00FF00;
			}

			public int foliage(String b) {
				return 0x00AA00;
			}

			public int water(String b) {
				return 0x0000FF;
			}
		};
		int[] px = RegionShader.shade(r, c);
		assertEquals(0, px[RegionData.index(0, 0)], "unknown is transparent");
		int flat = px[RegionData.index(10, 10)] & 0xFF;
		int up = px[RegionData.index(10, 11)] & 0xFF;
		int down = px[RegionData.index(10, 12)] & 0xFF;
		assertTrue(up > flat && down < flat, flat + " " + up + " " + down);
		int water = px[RegionData.index(20, 20)];
		assertTrue((water & 0xFF) > ((water >> 16) & 0xFF), "water is blue");
		int[] q = RegionShader.quarter(px, 512);
		assertEquals(128 * 128, q.length);
		assertTrue((q[2 * 128 + 2] >>> 24) != 0, "the quarter keeps known pixels (block 10,10 is in quarter pixel 2,2)");
	}

	@Test
	void waypointStore() throws IOException {
		Path file = dir.resolve("mock_server").resolve("waypoints.json");
		WaypointStore s = new WaypointStore(file);
		s.load();
		Waypoint own = new Waypoint("Mock base", WaypointKind.PLAYER, "monumenta:valley", 10, 64, 20);
		s.add(own);
		Waypoint temp = new Waypoint("t", WaypointKind.PLAYER_TEMPORARY, "monumenta:valley", 100, 64, 100);
		s.add(temp);
		List<Waypoint> builtin = WaypointStore.parseBuiltin("""
				{ "_source": "made up", "monumenta:valley": [ { "name": "Mock Dungeon", "kind": "dungeon", "x": 1, "y": 2, "z": 3 } ] }
				""");
		assertEquals(1, builtin.size());
		s.setBuiltin(builtin);
		Waypoint b = s.all().stream().filter(w -> w.builtin).findFirst().orElseThrow();
		s.setBookmarked(b, true);
		s.setKindHidden(WaypointKind.POI, true);
		s.save();

		WaypointStore again = new WaypointStore(file);
		again.load();
		again.setBuiltin(builtin);
		assertEquals(1, again.own().size(), "temporary waypoints aren't saved");
		assertTrue(again.all().stream().filter(w -> w.builtin).findFirst().orElseThrow().bookmarked, "built-in bookmarks are kept by id");
		assertTrue(again.kindHidden(WaypointKind.POI));
		assertEquals(2, again.visible("monumenta:valley").size());

		// a temporary waypoint is reached within REACHED blocks
		again.add(new Waypoint("t2", WaypointKind.QUEST_OBJECTIVE, "monumenta:valley", 50, 64, 50));
		assertEquals(0, again.reach("monumenta:valley", 40, 40).size());
		assertEquals(1, again.reach("monumenta:valley", 51, 52).size());
		// the compass keeps one target
		Waypoint c1 = new Waypoint("c", WaypointKind.QUEST_OBJECTIVE, "monumenta:valley", 0, 0, 0);
		c1.source = "compass";
		again.replaceTemporary("compass", c1);
		Waypoint c2 = new Waypoint("c", WaypointKind.QUEST_OBJECTIVE, "monumenta:valley", 9, 0, 9);
		c2.source = "compass";
		again.replaceTemporary("compass", c2);
		assertEquals(1, again.temporary().size());
		// imports don't duplicate
		assertEquals(0, again.addAllNew(List.of(new Waypoint("Mock base", WaypointKind.PLAYER, "monumenta:valley", 10, 64, 20))));

		// tracking: saved by id, drawn on top, shown even when hidden; the map choice filters
		Waypoint poi = new Waypoint("Mock POI", WaypointKind.POI, "monumenta:isles", 0, 64, 0);
		Waypoint onlyStatic = new Waypoint("Mock static", WaypointKind.PLAYER, "monumenta:isles", 5, 64, 5);
		onlyStatic.maps = Waypoint.STATIC;
		Waypoint tracked = new Waypoint("Mock tracked", WaypointKind.PLAYER, "monumenta:isles", 1, 64, 1);
		again.add(tracked);
		again.add(poi);
		again.add(onlyStatic);
		again.track(tracked);
		again.setHidden(tracked, true);
		List<Waypoint> overStatic = again.shown("monumenta:isles", true);
		assertEquals(tracked, overStatic.get(overStatic.size() - 1), "the tracked waypoint is drawn last (on top), even when hidden");
		assertTrue(overStatic.contains(onlyStatic));
		assertFalse(again.shown("monumenta:isles", false).contains(onlyStatic), "a static-only waypoint isn't drawn over the dynamic map");
		Waypoint compass = new Waypoint("c", WaypointKind.QUEST_OBJECTIVE, "monumenta:isles", 0, 64, 0);
		compass.source = "compass";
		again.replaceTemporary("compass", compass);
		List<Waypoint> order = again.shown("monumenta:isles", true);
		assertTrue(order.indexOf(compass) > order.indexOf(poi), "the compass target is drawn over a POI at the same place");
		Waypoint town = new Waypoint("Mock Town", WaypointKind.TOWN, "monumenta:isles", 300, 64, 0);
		Waypoint far = new Waypoint("Far Town", WaypointKind.TOWN, "monumenta:isles", 3000, 64, 0);
		again.add(town);
		again.add(far);
		assertEquals(town, again.nearest("monumenta:isles", WaypointKind.TOWN, 0, 0, null));
		assertEquals(far, again.nearest("monumenta:isles", WaypointKind.TOWN, 300, 0, town), "not the town that was clicked");
		again.save();
		WaypointStore third = new WaypointStore(file);
		third.load();
		assertEquals(tracked.id, third.tracked().id, "tracking is saved");
		assertEquals(Waypoint.STATIC, third.find(onlyStatic.id).maps);
		assertEquals(WaypointColors.DEFAULT, WaypointColors.opaque(0));
		assertEquals(WaypointColors.PALETTE[1], WaypointColors.nearest(0xFF5555), "Xaero red → red");

		Files.writeString(file, "{ not json");
		WaypointStore broken = new WaypointStore(file);
		broken.load();
		assertTrue(Files.exists(file.resolveSibling("waypoints.json.broken")), "a broken file is kept aside");
	}

	@Test
	void settings() throws IOException {
		Path f = dir.resolve("config.json");
		MapSettings s = MapSettings.load(f);
		assertEquals(MapSettings.Mode.STATIC, s.mode);
		s.size = 9999;
		s.zoom = -3;
		s.fix().save(f);
		MapSettings back = MapSettings.load(f);
		assertEquals(256, back.size);
		assertEquals(1, back.zoom);
		Files.writeString(f, "{ \"shape\": \"TRIANGLE\", \"size\": 64 }");
		MapSettings odd = MapSettings.load(f);
		assertEquals(MapSettings.Shape.SQUARE, odd.shape, "unknown enum names fall back");
		assertEquals(64, odd.size);
		assertEquals(2, MapSettings.nextZoom(1, true));
		assertEquals(0.5, MapSettings.nextZoom(1, false));
		assertEquals(4, MapSettings.nextZoom(4, true));
	}

	@Test
	void deathWaypoints() throws IOException {
		Path file = dir.resolve("death_server").resolve("waypoints.json");
		WaypointStore s = new WaypointStore(file);
		s.load();
		s.add(new Waypoint("Mock base", WaypointKind.PLAYER, "monumenta:valley", 0, 64, 0));
		Waypoint first = null;
		for (int i = 0; i < WaypointStore.DEATHS_KEPT + 2; i++) {
			Waypoint d = new Waypoint("Death " + i, WaypointKind.DEATH, i % 2 == 0 ? "monumenta:valley" : "monumenta:isles", i, 64, i);
			d.created = 1000 + i;
			if (i == 0) {
				first = d;
				s.track(d);
			}
			List<Waypoint> removed = s.addDeath(d, WaypointStore.DEATHS_KEPT);
			assertEquals(i < WaypointStore.DEATHS_KEPT ? 0 : 1, removed.size(), "death " + i);
		}
		long deaths = s.own().stream().filter(w -> w.kind() == WaypointKind.DEATH).count();
		assertEquals(WaypointStore.DEATHS_KEPT, deaths, "only the newest deaths stay, over every dimension");
		assertTrue(s.own().stream().noneMatch(w -> w.name.equals("Death 0") || w.name.equals("Death 1")), "the oldest go first");
		assertTrue(s.own().stream().anyMatch(w -> w.name.equals("Mock base")), "other waypoints stay");
		assertFalse(s.isTracked(first));
		assertNull(s.tracked(), "a removed death stops being tracked");
		assertFalse(WaypointKind.DEATH.temporary, "deaths are kept (saved)");
		s.save();
		WaypointStore again = new WaypointStore(file);
		again.load();
		assertEquals(WaypointStore.DEATHS_KEPT, again.own().stream().filter(w -> w.kind() == WaypointKind.DEATH).count());
	}

	@Test
	void iconRecolor() {
		String old = "<svg><path fill=\"#3A8DFF\"/></svg>";
		assertEquals("#3a8dff", WaypointColors.recolorTarget(old));
		assertEquals("<svg><path fill=\"#e5484d\"/></svg>", WaypointColors.recolorSvg(old, 0xE5484D));
		String marked = "<svg>\n  <!-- cakemap-recolor: #9a9a9a  (note) -->\n  <path fill=\"#9a9a9a\"/><path fill=\"#3a8dff\"/></svg>";
		assertEquals("#9a9a9a", WaypointColors.recolorTarget(marked));
		String r = WaypointColors.recolorSvg(marked, 0x4CC764);
		assertTrue(r.contains("fill=\"#4cc764\"") && r.contains("fill=\"#3a8dff\"") && !r.contains("9a9a9a"), r);
	}

	@Test
	void clipping() {
		List<double[]> square = MapGeometry.rect(-10, -10, 20, 20);
		List<double[]> clipped = MapGeometry.clip(square, 0, 0, 512, 512);
		assertEquals(4, clipped.size());
		double minX = clipped.stream().mapToDouble(p -> p[0]).min().orElseThrow();
		double maxX = clipped.stream().mapToDouble(p -> p[0]).max().orElseThrow();
		assertEquals(0, minX, 1e-9);
		assertEquals(10, maxX, 1e-9);
		assertTrue(MapGeometry.clip(square, 100, 100, 200, 200).isEmpty());
		MapGeometry.View v = new MapGeometry.View();
		v.centerX = 100;
		v.centerZ = -50;
		v.scale = 2;
		v.angle = 0.7;
		v.screenX = 64;
		v.screenY = 64;
		double[] s = v.toScreen(130, -20);
		double[] w = v.toWorld(s[0], s[1]);
		assertEquals(130, w[0], 1e-9);
		assertEquals(-20, w[1], 1e-9);
	}
}
