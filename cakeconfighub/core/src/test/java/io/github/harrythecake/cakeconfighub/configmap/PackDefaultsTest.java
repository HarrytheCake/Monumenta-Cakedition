package io.github.harrythecake.cakeconfighub.configmap;

import io.github.harrythecake.cakeconfighub.json.Json5;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADR 0011: the pack's value goes in only while an option is untouched; the player's own choice always stays. */
class PackDefaultsTest {
	@TempDir
	Path game;
	Map<String, ConfigMap> maps;

	@BeforeEach
	void maps() {
		maps = ConfigMaps.load(List.of(
				new ConfigMaps.Source("sodium", "sodium.json5", """
						{ mod: "sodium", versions: ">=0.5 <0.6", file: "config/sodium-options.json", type: "json", hub: false,
						  options: [
						    { key: "quality.weather_quality", type: "enum", values: ["DEFAULT", "FAST", "FANCY"] },
						    { key: "advanced.cpu_render_ahead_limit", type: "int", min: 0, max: 9 },
						    { key: "performance.use_entity_culling", type: "bool" },
						  ] }
						"""),
				new ConfigMaps.Source("minecraft", "minecraft.json5", """
						{ mod: "minecraft", file: "options.txt", type: "options",
						  options: [ { key: "renderClouds", type: "enum", values: ['"true"', '"fast"', '"false"'] } ] }
						"""))).maps();
	}

	private void write(String rel, String text) throws Exception {
		Files.createDirectories(game.resolve(rel).getParent());
		Files.writeString(game.resolve(rel), text, StandardCharsets.UTF_8);
	}

	private String read(String rel) throws Exception {
		return Files.readString(game.resolve(rel), StandardCharsets.UTF_8);
	}

	private static PackDefaults defaults(String json5, List<String> problems) {
		return PackDefaults.read(Json5.parse(json5, "pack-defaults.json5"), problems);
	}

	private PackDefaults.Report apply(PackDefaults d, PackDefaults.Record r) {
		return d.apply(maps, r, game, id -> true, id -> id.equals("sodium") ? "0.5.8+mc1.20.4" : "1.20.4");
	}

	private static final String SODIUM = """
			{
			  "quality": { "weather_quality": "DEFAULT" },
			  "advanced": { "cpu_render_ahead_limit": 3 },
			  "performance": { "use_entity_culling": true }
			}
			""";

	@Test
	void untouchedValuesFollowThePackAndThePlayersStay() throws Exception {
		write("config/sodium-options.json", SODIUM);
		List<String> problems = new ArrayList<>();
		PackDefaults d = defaults("""
				{ format: 1, sodium: {
				    "quality.weather_quality": { value: "FAST", history: ["DEFAULT"] },  // the old pack value: untouched
				    "advanced.cpu_render_ahead_limit": { value: 5, history: [2] },     // 3 is the player's
				    "performance.use_entity_culling": true,                            // already right
				} }
				""", problems);
		assertTrue(problems.isEmpty(), problems.toString());
		PackDefaults.Record rec = new PackDefaults.Record();
		PackDefaults.Report rep = apply(d, rec);
		assertTrue(rep.problems.isEmpty(), rep.problems.toString());
		assertEquals(1, rep.written);
		assertEquals(1, rep.kept);
		assertEquals(1, rep.unchanged);
		assertEquals(SODIUM.replace("\"DEFAULT\"", "\"FAST\""), read("config/sodium-options.json"));
		assertEquals("FAST", rec.get("sodium", "quality.weather_quality"));
		assertEquals(Boolean.TRUE, rec.get("sodium", "performance.use_entity_culling"));
		assertEquals(null, rec.get("sodium", "advanced.cpu_render_ahead_limit"));  // the player's: nothing recorded
		assertTrue(rep.recordChanged);

		// the next start changes nothing
		PackDefaults.Report again = apply(d, rec);
		assertEquals(0, again.written);
		assertFalse(again.recordChanged);
	}

	@Test
	void theRecordLetsANewPackValueReplaceTheOldOne() throws Exception {
		write("config/sodium-options.json", SODIUM);
		List<String> problems = new ArrayList<>();
		PackDefaults.Record rec = new PackDefaults.Record();
		apply(defaults("{ sodium: { \"advanced.cpu_render_ahead_limit\": { value: 3 } } }", problems), rec);
		assertEquals(3L, rec.get("sodium", "advanced.cpu_render_ahead_limit"));
		// the pack later moves to 5 without listing 3 in the history: the record still knows CakeConfighub put 3 there
		PackDefaults.Report rep = apply(defaults("{ sodium: { \"advanced.cpu_render_ahead_limit\": { value: 5 } } }", problems), rec);
		assertEquals(1, rep.written);
		assertTrue(read("config/sodium-options.json").contains("\"cpu_render_ahead_limit\": 5"));
		// the player then picks 7: a later pack value 6 leaves it alone
		write("config/sodium-options.json", read("config/sodium-options.json").replace(": 5", ": 7"));
		rep = apply(defaults("{ sodium: { \"advanced.cpu_render_ahead_limit\": { value: 6, history: [5] } } }", problems), rec);
		assertEquals(0, rep.written);
		assertEquals(1, rep.kept);
		assertTrue(read("config/sodium-options.json").contains("\"cpu_render_ahead_limit\": 7"));
	}

	@Test
	void missingKeysAreAddedAndOptionsTxtIsRaw() throws Exception {
		write("config/sodium-options.json", "{\n  \"quality\": {}\n}\n");
		write("options.txt", "version:3700\nrenderClouds:\"true\"\n");
		List<String> problems = new ArrayList<>();
		PackDefaults d = defaults("""
				{ sodium: { "quality.weather_quality": "FAST" },
				  minecraft: { renderClouds: { value: '"false"', history: ['"true"'] } } }
				""", problems);
		PackDefaults.Report rep = apply(d, new PackDefaults.Record());
		assertTrue(rep.problems.isEmpty(), rep.problems.toString());
		assertEquals(2, rep.written);
		assertEquals("{\n  \"quality\": {\n    \"weather_quality\": \"FAST\"\n  }\n}\n", read("config/sodium-options.json"));
		assertEquals("version:3700\nrenderClouds:\"false\"\n", read("options.txt"));
	}

	@Test
	void skipsWhatItCantOrShouldntTouch() throws Exception {
		List<String> problems = new ArrayList<>();
		PackDefaults d = defaults("""
				{ sodium: { "quality.weather_quality": { value: "FAST", history: ["DEFAULT"] }, "advanced.cpu_render_ahead_limit": 42, "no.such": 1 },
				  nomap: { a: 1 } }
				""", problems);
		// no file yet: skipped, nothing created
		PackDefaults.Report rep = apply(d, new PackDefaults.Record());
		assertEquals(0, rep.written);
		assertFalse(Files.exists(game.resolve("config/sodium-options.json")));
		assertTrue(rep.lines.stream().anyMatch(l -> l.contains("doesn't exist yet")), rep.lines.toString());
		assertTrue(rep.problems.stream().anyMatch(p -> p.contains("nomap")), rep.problems.toString());

		write("config/sodium-options.json", SODIUM);
		rep = apply(d, new PackDefaults.Record());
		assertEquals(1, rep.written);  // FAST; 42 is out of range, no.such isn't an option
		assertTrue(rep.problems.stream().anyMatch(p -> p.contains("42")), rep.problems.toString());
		assertTrue(rep.problems.stream().anyMatch(p -> p.contains("no.such")), rep.problems.toString());

		// mod not installed, or a version the map wasn't written for: the file stays as it is
		write("config/sodium-options.json", SODIUM);
		assertEquals(0, d.apply(maps, new PackDefaults.Record(), game, id -> false, id -> "0.5.8").written);
		rep = d.apply(maps, new PackDefaults.Record(), game, id -> true, id -> "0.6.0");
		assertEquals(0, rep.written);
		assertTrue(rep.lines.stream().anyMatch(l -> l.contains("isn't in the map's versions")), rep.lines.toString());
		assertEquals(SODIUM, read("config/sodium-options.json"));

		// a file that isn't valid JSON is left alone
		write("config/sodium-options.json", "{ broken");
		assertEquals(0, apply(d, new PackDefaults.Record()).written);
		assertEquals("{ broken", read("config/sodium-options.json"));
	}

	@Test
	void recordRoundTrip() {
		List<String> problems = new ArrayList<>();
		PackDefaults.Record r = new PackDefaults.Record();
		r.put("sodium", "quality.weather_quality", "FAST");
		r.put("sodium", "advanced.cpu_render_ahead_limit", 5L);
		r.put("zoomify", "zoomInTime", 1.5);
		r.put("x", "flag", true);
		PackDefaults.Record back = PackDefaults.Record.read(r.text(), problems);
		assertTrue(problems.isEmpty());
		assertEquals("FAST", back.get("sodium", "quality.weather_quality"));
		assertTrue(ConfigDoc.same(5L, back.get("sodium", "advanced.cpu_render_ahead_limit")));
		assertEquals(1.5, back.get("zoomify", "zoomInTime"));
		assertEquals(Boolean.TRUE, back.get("x", "flag"));
		PackDefaults.Record.read("{ nope", problems);
		assertEquals(1, problems.size());
	}

	@Test
	void newerFormatIsNotApplied() {
		List<String> problems = new ArrayList<>();
		assertTrue(defaults("{ format: 2, sodium: { a: 1 } }", problems).entries.isEmpty());
		assertEquals(1, problems.size());
	}
}
