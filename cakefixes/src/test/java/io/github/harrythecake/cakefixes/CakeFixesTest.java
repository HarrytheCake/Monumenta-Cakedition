package io.github.harrythecake.cakefixes;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CakeFixesTest {
	private static final List<String> LANGUAGES = List.of("en_us", "zh_cn", "zh_tw");

	@Test
	void teamRemovalSkipsOnlyAMismatchWhileOn() {
		Object red = new Object(), blue = new Object();
		long before = TeamRemoval.skipped();
		assertTrue(TeamRemoval.shouldRemove(true, red, red, "red"), "on that team: vanilla removes it");
		assertFalse(TeamRemoval.shouldRemove(true, blue, red, "red"), "on another team: vanilla would throw");
		assertFalse(TeamRemoval.shouldRemove(true, null, red, "red"), "on no team: vanilla would throw");
		assertTrue(TeamRemoval.shouldRemove(false, null, red, "red"), "fix off: vanilla behaviour");
		assertEquals(before + 2, TeamRemoval.skipped());
	}

	@Test
	void blendQueryUsesTheTrackedStateOnlyForGlBlendWhileOn() {
		int[] driverCalls = {0};
		java.util.function.IntPredicate driver = cap -> { driverCalls[0]++; return false; };
		long before = BlendQuery.answered();
		assertTrue(BlendQuery.isEnabled(true, BlendQuery.GL_BLEND, () -> true, driver), "tracked state wins");
		assertEquals(0, driverCalls[0], "no driver call when the tracked state answers");
		assertFalse(BlendQuery.isEnabled(true, 0x0B71 /* GL_DEPTH_TEST */, () -> true, driver), "other caps: driver");
		assertFalse(BlendQuery.isEnabled(false, BlendQuery.GL_BLEND, () -> true, driver), "fix off: driver");
		assertFalse(BlendQuery.isEnabled(true, BlendQuery.GL_BLEND, null, driver), "state not found: driver");
		assertEquals(3, driverCalls[0]);
		assertEquals(before + 1, BlendQuery.answered());
	}

	@Test
	void bannerDistanceIsVanillaWhenOffAndHidesOnlyWhatVanillaWouldDraw() {
		// off (0), negative and >= 64 all mean vanilla's 64 blocks, strict "<" like Vec3d.isInRange
		for (int off : new int[] {0, -5, 64, 100}) {
			assertEquals(BannerDistance.VANILLA, BannerDistance.effective(off));
			assertTrue(BannerDistance.visible(off, 63.9 * 63.9));
			assertFalse(BannerDistance.visible(off, 64.0 * 64.0));
		}
		long before = BannerDistance.hidden();
		assertTrue(BannerDistance.visible(24, 23.9 * 23.9));
		assertFalse(BannerDistance.visible(24, 24.0 * 24.0), "at the limit: hidden");
		assertFalse(BannerDistance.visible(24, 70.0 * 70.0), "beyond 64: vanilla hides it too, not counted");
		assertEquals(before + 1, BannerDistance.hidden());
		assertEquals(0, BannerDistance.CHOICES.get(0), "the first choice is off");
	}

	@Test
	void anOlderConfigIsRewrittenWithTheNewSettingsAndKeepsItsValues() {
		String v010 = "{\n  \"teamRemovalFix\": false\n}\n"; // what 0.1.0 wrote, with the fix turned off
		assertTrue(Config.needsRewrite(v010));
		Config upgraded = Config.parse(v010);
		String rewritten = Config.write(upgraded);
		assertFalse(Config.needsRewrite(rewritten), "the rewrite names every setting");
		assertFalse(Config.parse(rewritten).teamRemovalFix, "the player's value survives");
		assertTrue(Config.parse(rewritten).appleSkinBlendQueryFix, "new fixes arrive on");
		assertEquals(0, Config.parse(rewritten).bannerDrawDistance, "new options arrive off");
	}

	@Test
	void bannerDistanceDefaultsOffAndRoundTrips() {
		assertEquals(0, new Config().bannerDrawDistance, "changes what you see: off by default");
		assertEquals(0, Config.parse("{}").bannerDrawDistance);
		Config c = Config.parse("{ \"bannerDrawDistance\": 24 }");
		assertEquals(24, c.bannerDrawDistance);
		assertEquals(24, c.copy().bannerDrawDistance, "copy() lost bannerDrawDistance");
		assertEquals(24, Config.parse(Config.write(c)).bannerDrawDistance, "written config lost bannerDrawDistance");
	}

	@Test
	void configDefaultsAreOnAndSurviveAMissingSwitch() {
		for (String sw : switches()) {
			assertTrue(get(new Config(), sw), sw + " defaults to on");
			assertTrue(get(Config.parse("// comment\n{}"), sw), sw + " missing from the file stays on");
		}
		Config c = Config.parse("{ \"teamRemovalFix\": false }");
		assertFalse(c.teamRemovalFix);
		assertTrue(c.appleSkinBlendQueryFix, "an older config without the new switch keeps it on");
	}

	@Test
	void copyKeepsEverySwitch() {
		Config off = Config.parse("{ \"teamRemovalFix\": false, \"appleSkinBlendQueryFix\": false }");
		for (String sw : switches()) {
			assertFalse(get(off.copy(), sw), "copy() lost " + sw);
		}
	}

	private static boolean get(Config c, String sw) {
		try {
			return Config.class.getField(sw).getBoolean(c);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void writtenConfigReadsBackAndNamesEverySwitch() {
		Config off = Config.parse("{ \"teamRemovalFix\": false, \"appleSkinBlendQueryFix\": false }");
		String text = Config.write(off);
		for (String option : options()) {
			assertTrue(text.contains("\"" + option + "\""), "config template lacks " + option);
		}
		for (String sw : switches()) {
			assertTrue(text.contains("\"" + sw + "\""), "config template lacks " + sw);
			assertFalse(get(Config.parse(text), sw), "written config lost " + sw);
		}
	}

	@Test
	void everyLanguageHasTheSameKeysAndEverySwitchIsTranslated() throws Exception {
		Map<String, String> english = lang("en_us");
		for (String sw : switches()) {
			for (String suffix : List.of("", ".tooltip", ".count")) {
				assertTrue(english.containsKey("cakefixes.fix." + sw + suffix), "en_us lacks " + sw + suffix);
			}
		}
		for (String option : options()) {
			for (String suffix : List.of("", ".tooltip", ".count", ".value")) {
				assertTrue(english.containsKey("cakefixes.fix." + option + suffix), "en_us lacks " + option + suffix);
			}
		}
		for (String language : LANGUAGES) {
			assertEquals(english.keySet(), lang(language).keySet(), language + " keys differ from en_us");
		}
	}

	/** Every public boolean field of Config is a fix switch. */
	private static Set<String> switches() {
		return settings(boolean.class);
	}

	/** Every public int field of Config is an option (0 = off). */
	private static Set<String> options() {
		return settings(int.class);
	}

	private static Set<String> settings(Class<?> type) {
		return Arrays.stream(Config.class.getFields())
				.filter(f -> f.getType() == type && !Modifier.isStatic(f.getModifiers()))
				.map(Field::getName).collect(Collectors.toSet());
	}

	private static Map<String, String> lang(String language) throws Exception {
		try (var in = CakeFixesTest.class.getResourceAsStream("/assets/cakefixes/lang/" + language + ".json")) {
			assertNotNull(in, "missing lang file " + language);
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
					new TypeToken<Map<String, String>>() {}.getType());
		}
	}
}
