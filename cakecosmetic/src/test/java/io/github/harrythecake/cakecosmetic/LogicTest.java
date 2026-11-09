package io.github.harrythecake.cakecosmetic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Files, rules, picks and image sizes, without the game. Made-up advancement ids. */
class LogicTest {
	@TempDir
	Path dir;

	@Test
	void settingsDefaultsAndParsing() throws Exception {
		Settings fresh = Settings.load(dir);
		assertEquals(Settings.Mode.ADVANCEMENT, fresh.mode);
		assertFalse(fresh.devMode);
		assertTrue(Files.exists(dir.resolve("config.json5")), "the defaults are written");
		assertEquals(Settings.Mode.ADVANCEMENT, Settings.parse(Files.readString(dir.resolve("config.json5"))).mode);

		Settings s = Settings.parse("{ mode: 'all', devMode: true, // comment\n }");
		assertEquals(Settings.Mode.ALL, s.mode);
		assertTrue(s.devMode);
		assertEquals(Settings.Mode.ADVANCEMENT, Settings.parse("{ mode: 'bogus' }").mode);
		assertEquals(Settings.Mode.ADVANCEMENT, Settings.parse("not json at all {").mode);
	}

	@Test
	void rulesRoundTripAndUnlocks() throws Exception {
		Rules r = new Rules();
		r.put("paperbox:image3", new Rules.Rule(List.of(new Rules.Need("testpack:quests/a", "Quest A")), false));
		r.put("paperbox:image5", new Rules.Rule(List.of(new Rules.Need("testpack:quests/a", "Quest A"),
				new Rules.Need("testpack:dungeons/b", "Dungeon B")), true));
		r.put("paperbox:image1", new Rules.Rule(List.of(), false));
		assertNull(r.get("paperbox:image1"), "an empty rule is no rule");
		r.save(dir);

		Rules back = Rules.load(dir);
		assertEquals(r.all(), back.all());
		assertTrue(back.get("paperbox:image5").secret());
		assertEquals("Dungeon B", back.get("paperbox:image5").needs().get(1).title());
		assertEquals(Set.of("testpack:quests/a", "testpack:dungeons/b"), back.advancementIds());

		assertEquals(List.of(), back.newlyUnlocked(Set.of(), Set.of()));
		assertEquals(List.of("paperbox:image3"), back.newlyUnlocked(Set.of("testpack:quests/a"), Set.of()));
		assertEquals(List.of("paperbox:image3", "paperbox:image5"),
				back.newlyUnlocked(Set.of("testpack:quests/a", "testpack:dungeons/b"), Set.of()));
		assertEquals(List.of("paperbox:image5"),
				back.newlyUnlocked(Set.of("testpack:quests/a", "testpack:dungeons/b"), Set.of("paperbox:image3")));
	}

	@Test
	void rulesAcceptPlainIdsAndSurviveBadFiles() {
		Rules r = Rules.parse("{ 'a:b': { advancements: ['x:y'] }, 'c:d': 5 }");
		assertEquals("x:y", r.get("a:b").needs().get(0).title());
		assertNull(r.get("c:d"));
		assertTrue(Rules.parse("{{{").all().isEmpty());
		assertTrue(Rules.load(dir).all().isEmpty(), "no file = no rules");
	}

	@Test
	void stateFilesRoundTrip() {
		UnlockState s = UnlockState.load(dir);
		assertTrue(s.backgrounds.isEmpty());
		s.backgrounds.add("paperbox:image3");
		s.advancements.add("testpack:quests/a");
		s.save(dir);
		UnlockState back = UnlockState.load(dir);
		assertEquals(Set.of("paperbox:image3"), back.backgrounds);
		assertEquals(Set.of("testpack:quests/a"), back.advancements);

		Choices c = Choices.load(dir);
		assertEquals(Choices.Mode.SHUFFLE, c.mainMenu.mode);
		c.mainMenu.mode = Choices.Mode.ONE;
		c.mainMenu.one = "paperbox:image2";
		c.loading.shuffle.add("paperbox:image4");
		c.save(dir);
		Choices cb = Choices.load(dir);
		assertEquals(Choices.Mode.ONE, cb.mainMenu.mode);
		assertEquals("paperbox:image2", cb.mainMenu.one);
		assertEquals(Set.of("paperbox:image4"), cb.loading.shuffle);
	}

	@Test
	void pools() {
		List<String> pickable = List.of("p:1", "p:2", "p:3");
		Choices.Choice c = new Choices.Choice();
		assertEquals(pickable, Picker.pool(c, pickable), "shuffle, nothing ticked = all pickable");
		c.shuffle.add("p:2");
		c.shuffle.add("p:9");
		assertEquals(List.of("p:2"), Picker.pool(c, pickable), "ticked ones that are still pickable");
		c.shuffle.add(Choices.VANILLA);
		assertEquals(List.of("p:2", Choices.VANILLA), Picker.pool(c, pickable));
		c.mode = Choices.Mode.ONE;
		c.one = "p:3";
		assertEquals(List.of("p:3"), Picker.pool(c, pickable));
		c.one = "p:9";
		assertEquals(pickable, Picker.pool(c, pickable), "the chosen one is gone or locked: shuffle all");
		c.one = Choices.VANILLA;
		assertEquals(List.of(Choices.VANILLA), Picker.pool(c, pickable));
		assertEquals(List.of(), Picker.pool(new Choices.Choice(), List.of()));
	}

	@Test
	void picks() {
		Random r = new Random(1);
		assertEquals(Choices.VANILLA, Picker.pickOne(List.of(), "x", r));
		assertEquals("p:1", Picker.pickOne(List.of("p:1"), "p:1", r));
		for (int i = 0; i < 50; i++) {
			assertEquals("p:2", Picker.pickOne(List.of("p:1", "p:2"), "p:1", r), "never the same twice in a row");
		}
		List<String> order = Picker.order(List.of("a", "b", "c", "d"), r);
		assertEquals(Set.of("a", "b", "c", "d"), Set.copyOf(order));
	}

	@Test
	void imageSizes() {
		assertArrayEquals(new int[] {1920, 1080}, Fit.shrinkToCover(3840, 2160, 1920, 1080));
		assertArrayEquals(new int[] {2439, 1358}, Fit.shrinkToCover(2439, 1358, 2560, 1440), "never enlarged");
		// 4:3 picture on a 16:9 window: wide enough, the height is cropped later
		assertArrayEquals(new int[] {1920, 1440}, Fit.shrinkToCover(4000, 3000, 1920, 1080));
		assertArrayEquals(new int[] {0, 375, 4000, 2250}, Fit.coverRegion(4000, 3000, 1920, 1080));
		assertArrayEquals(new int[] {1000, 0, 2000, 2000}, Fit.coverRegion(4000, 2000, 100, 100));
	}

	@Test
	void backgroundMeta() {
		BackgroundMeta m = BackgroundMeta.parse("{ title: { en_us: 'Dusk', zh_cn: '黄昏' }, artist: 'Mock Artist' }", "t.json");
		assertEquals("黄昏", m.title("zh_cn"));
		assertEquals("Dusk", m.title("de_de"));
		assertEquals("Mock Artist", m.artist());
		assertEquals("Plain", BackgroundMeta.parse("{ title: 'Plain' }", "t.json").title("zh_cn"));
		assertNull(BackgroundMeta.parse("[", "t.json").title("en_us"));
	}
}
