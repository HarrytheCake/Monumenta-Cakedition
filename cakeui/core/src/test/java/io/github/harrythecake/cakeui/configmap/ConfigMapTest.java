package io.github.harrythecake.cakeui.configmap;

import io.github.harrythecake.cakeui.json.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMapTest {
	private static ConfigMap.Read read(String json5) {
		return ConfigMap.read(Json5.parse(json5, "test.json5"), "test.json5");
	}

	private static final String ZOOM = """
			{
			  format: 1, mod: "zoomify", versions: ">=2.13 <3", file: "config/zoomify.json", type: "json",
			  title: "Zoomify",
			  sections: [ { title: "Zoom", options: [
			    { key: "initialZoom", type: "int", min: 1, max: 10, label: "Zoom", default: 4 },
			    { key: "zoomInTime", type: "float", min: 0.1, max: 5, step: 0.1 },
			    { key: "zoomKeyBehaviour", type: "enum", values: ["hold", { value: "toggle", label: "Toggle" }] },
			    { key: "secondaryHideHUDOnZoom", type: "bool" },
			    { key: "pingTextColor", type: "colour" },
			    { key: "argb", type: "colour", colourAs: "int", alpha: true },
			    { key: "broken", type: "nope" },
			  ] } ],
			}
			""";

	@Test
	void readsAMapAndSkipsBadOptions() {
		ConfigMap.Read r = read(ZOOM);
		ConfigMap m = r.map();
		assertNotNull(m);
		assertEquals(1, r.problems().size(), r.problems().toString());
		assertTrue(r.problems().get(0).contains("broken"));
		assertEquals("zoomify", m.mod);
		assertEquals(Format.JSON, m.type);
		assertTrue(m.hub && m.reloadable);
		assertEquals(6, m.options().size());
		assertEquals(4L, m.option("initialZoom").fallback);
		assertEquals("Toggle", m.option("zoomKeyBehaviour").choices.get(1).label());
		assertTrue(m.versions.contains("2.15.2+1.20.4"));
		assertFalse(m.versions.contains("3.0.0"));
	}

	@Test
	void aModWithOneIdPerLoader() {
		ConfigMap m = read("{ mod: ['caketext-translator', 'caketext_translator'], file: 'config/caketext/translator.json', type: 'json', options: [ { key: 'enabled', type: 'bool' } ] }").map();
		assertEquals("caketext-translator", m.mod);
		assertEquals("caketext_translator", m.loadedId(id -> id.equals("caketext_translator")));
		assertNull(m.loadedId(id -> false));
	}

	@Test
	void unusableMaps() {
		assertNull(read("{ mod: 'x', file: 'config/x.json' }").map());                     // no type
		assertNull(read("{ mod: 'x', file: '../x.json', type: 'json' }").map());           // outside the game folder
		assertNull(read("{ mod: 'x', file: 'C:/x.json', type: 'json' }").map());
		assertNull(read("{ format: 2, mod: 'x', file: 'config/x.json', type: 'json' }").map());
		assertNull(read("{ mod: 'x', file: 'config/x.json', type: 'json', versions: 'abc' }").map());
		ConfigMap flat = read("{ mod: 'x', file: 'config/x.json', type: 'json', hub: false, options: [ { key: 'a', type: 'bool' } ] }").map();
		assertNotNull(flat);
		assertFalse(flat.hub);
		assertEquals(1, flat.options().size());
	}

	@Test
	void typedReadsAndWrites() {
		ConfigMap m = read(ZOOM).map();
		ConfigMap.Option zoom = m.option("initialZoom"), time = m.option("zoomInTime"), key = m.option("zoomKeyBehaviour"),
				colour = m.option("pingTextColor"), argb = m.option("argb");
		assertEquals(4L, zoom.read(4.0));
		assertNull(zoom.read(4.5));
		assertNull(zoom.read("x"));
		assertEquals(1.0, time.read(1.0));
		assertEquals("toggle", key.read("toggle"));
		assertNull(key.read("press"));
		assertEquals(0xA0A0A0L, colour.read("#A0A0A0"));
		assertEquals("#A0A0A0", colour.toFile(0xA0A0A0L));
		assertEquals(0xFFFFFFFFL, argb.read(-1.0));
		assertEquals(-1L, argb.toFile(0xFFFFFFFFL));
		assertTrue(zoom.allows(10L));
		assertFalse(zoom.allows(11L));
		assertFalse(key.allows("press"));
	}

	@Test
	void optionsTxtMapReadsRawText() {
		ConfigMap m = read("""
				{ mod: "minecraft", file: "options.txt", type: "options", options: [
				  { key: "guiScale", type: "int", min: 0, max: 6 },
				  { key: "renderClouds", type: "enum", values: ['"true"', '"fast"', '"false"'], default: '"false"' },
				  { key: "chatOpacity", type: "float", min: 0, max: 1 },
				  { key: "showSubtitles", type: "bool" },
				] }
				""").map();
		ConfigDoc d = ConfigDoc.parse(Format.OPTIONS, "guiScale:2\nrenderClouds:\"fast\"\nchatOpacity:1.0\nshowSubtitles:false\n");
		assertEquals(2L, m.option("guiScale").read(d.get(m.option("guiScale").path)));
		assertEquals("\"fast\"", m.option("renderClouds").read(d.get(m.option("renderClouds").path)));
		assertEquals("\"false\"", m.option("renderClouds").fallback);
		assertEquals(Boolean.FALSE, m.option("showSubtitles").read(d.get(m.option("showSubtitles").path)));
		d.set(m.option("showSubtitles").path, m.option("showSubtitles").toFile(true));
		d.set(m.option("chatOpacity").path, m.option("chatOpacity").toFile(0.5));
		assertEquals("guiScale:2\nrenderClouds:\"fast\"\nchatOpacity:0.5\nshowSubtitles:true\n", d.text());
	}

	@Test
	void fileSavesInOneStepAndLeavesBadFilesAlone(@TempDir Path game) throws Exception {
		ConfigMap m = read(ZOOM).map();
		Path file = game.resolve("config/zoomify.json");
		ConfigFile missing = ConfigFile.open(m, game);
		assertFalse(missing.usable());
		assertTrue(missing.problem.contains("doesn't exist yet"));

		Files.createDirectories(file.getParent());
		String original = "{\n  \"initialZoom\": 4,\n  \"zoomInTime\": 1.0\n}\n";
		Files.writeString(file, original, StandardCharsets.UTF_8);
		ConfigFile f = ConfigFile.open(m, game);
		assertTrue(f.usable(), f.problem);
		assertEquals(4L, f.read(m.option("initialZoom")));
		assertFalse(f.has(m.option("zoomKeyBehaviour")));
		assertTrue(f.save());  // nothing changed: not written
		f.write(m.option("initialZoom"), 7L);
		f.write(m.option("zoomKeyBehaviour"), "toggle");
		assertTrue(f.changed());
		assertTrue(f.save());
		assertEquals("{\n  \"initialZoom\": 7,\n  \"zoomInTime\": 1.0,\n  \"zoomKeyBehaviour\": \"toggle\"\n}\n",
				Files.readString(file, StandardCharsets.UTF_8));
		assertFalse(Files.exists(file.resolveSibling("zoomify.json.cakeui-tmp")));

		Files.writeString(file, "{ broken", StandardCharsets.UTF_8);
		ConfigFile bad = ConfigFile.open(m, game);
		assertFalse(bad.usable());
		assertTrue(bad.problem.contains("leaves it alone"), bad.problem);
		assertEquals("{ broken", Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void versionRanges() {
		assertTrue(VersionRange.parse("*").contains(null));
		assertFalse(VersionRange.parse(">=1").contains(null));
		assertTrue(VersionRange.compare("1.10", "1.9") > 0);
		assertEquals(0, VersionRange.compare("3", "3.0.0"));
		assertTrue(VersionRange.compare("1.2.0-beta.1", "1.2.0") < 0);
		assertTrue(VersionRange.compare("1.2.0-beta.2", "1.2.0-beta.10") < 0);
		assertEquals(0, VersionRange.compare("2.15.2+1.20.4", "2.15.2"));
		VersionRange r = VersionRange.parse(">=3.0.10 <3.1");
		assertTrue(r.contains("3.0.17"));
		assertFalse(r.contains("3.1.0"));
		assertFalse(r.contains("3.0.9"));
		assertTrue(VersionRange.parse("1.10").contains("1.10"));
		assertFalse(VersionRange.parse("1.10").contains("1.10.1"));
	}
}
