package io.github.harrythecake.cakeconfighub.configmap;

import io.github.harrythecake.cakeconfighub.json.Json5;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubModelTest {
	@TempDir
	Path game;

	private Map<String, ConfigMap> maps() {
		return ConfigMaps.load(List.of(
				new ConfigMaps.Source("zoomify", "zoomify.json5", """
						{ mod: "zoomify", versions: ">=2", file: "config/zoomify.json", type: "json", title: "Zoomify",
						  options: [ { key: "initialZoom", type: "int", min: 1, max: 10, default: 4 },
						             { key: "zoomKeyBehaviour", type: "enum", values: ["hold", "toggle"] } ] }
						"""),
				new ConfigMaps.Source("asteorbar", "asteorbar.json5", """
						{ mod: "asteorbar", file: "config/asteorbar.json5", type: "json5", title: "AsteorBar",
						  options: [ { key: "overlay.enableOverlay", type: "bool" } ] }
						"""),
				new ConfigMaps.Source("old", "old.json5", """
						{ mod: "oldmod", versions: "<1", file: "config/old.json", type: "json", options: [ { key: "a", type: "bool" } ] }
						"""),
				new ConfigMaps.Source("sodium", "sodium.json5", """
						{ mod: "sodium", file: "config/sodium-options.json", type: "json", hub: false, options: [ { key: "a", type: "bool" } ] }
						"""),
				new ConfigMaps.Source("absent", "absent.json5", """
						{ mod: "notinstalled", file: "config/x.json", type: "json", options: [ { key: "a", type: "bool" } ] }
						"""))).maps();
	}

	@Test
	void showsLoadedModsAndSavesOnlyChanges() throws Exception {
		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve("config/zoomify.json"), "{\n  \"initialZoom\": 4,\n  \"zoomKeyBehaviour\": \"hold\"\n}\n", StandardCharsets.UTF_8);
		List<String> problems = new ArrayList<>();
		PackDefaults pack = PackDefaults.read(Json5.parse("{ zoomify: { initialZoom: 6 } }", "p"), problems);
		HubModel hub = HubModel.build(maps(), pack, game, id -> !id.equals("notinstalled"), id -> id.equals("oldmod") ? "2.0" : "2.15.2");

		// sorted by title (case ignored); hub:false and not-installed mods left out; old version and missing file greyed
		assertEquals(List.of("AsteorBar", "oldmod", "Zoomify"), hub.mods.stream().map(m -> m.map.title).toList());
		HubModel.Mod aster = hub.mods.get(0), old = hub.mods.get(1), zoom = hub.mods.get(2);
		assertFalse(aster.editable());
		assertTrue(aster.unavailable.contains("doesn't exist yet"));
		assertEquals("version", old.unavailable);
		assertTrue(zoom.editable());

		ConfigMap.Option level = zoom.map.option("initialZoom"), key = zoom.map.option("zoomKeyBehaviour");
		assertEquals(4L, hub.value(zoom, level));
		assertEquals(6L, hub.packDefault(zoom, level));
		assertNull(hub.packDefault(zoom, key));
		assertFalse(hub.set(zoom, level, 11L));     // out of range
		assertTrue(hub.set(zoom, key, "toggle"));
		assertTrue(hub.dirty());
		assertTrue(hub.set(zoom, key, "hold"));     // back to the saved value: no change left
		assertFalse(hub.dirty());

		hub.resetMod(zoom);                          // initialZoom -> the pack's 6; the enum has no reset value
		assertEquals(6L, hub.value(zoom, level));
		List<HubModel.Saved> saved = hub.save();
		assertEquals(1, saved.size());
		assertTrue(saved.get(0).ok());
		assertFalse(hub.dirty());
		assertEquals("{\n  \"initialZoom\": 6,\n  \"zoomKeyBehaviour\": \"hold\"\n}\n",
				Files.readString(game.resolve("config/zoomify.json"), StandardCharsets.UTF_8));

		hub.set(zoom, key, "toggle");
		hub.discard();
		assertEquals("hold", hub.value(zoom, key));
		assertTrue(hub.save().isEmpty());
	}

	@Test
	void resetFallsBackToTheMapsDefault() throws Exception {
		Files.createDirectories(game.resolve("config"));
		Files.writeString(game.resolve("config/zoomify.json"), "{\"initialZoom\": 9}", StandardCharsets.UTF_8);
		HubModel hub = HubModel.build(maps(), null, game, id -> true, id -> "3.0");
		HubModel.Mod zoom = hub.mods.stream().filter(m -> m.id.equals("zoomify")).findFirst().orElseThrow();
		hub.reset(zoom, zoom.map.option("initialZoom"));
		assertEquals(4L, hub.value(zoom, zoom.map.option("initialZoom")));
		assertNull(hub.value(zoom, zoom.map.option("zoomKeyBehaviour")));  // missing from the file
		hub.set(zoom, zoom.map.option("zoomKeyBehaviour"), "toggle");
		assertTrue(hub.save().get(0).ok());
		assertEquals("{\"initialZoom\": 4,\n  \"zoomKeyBehaviour\": \"toggle\"}",
				Files.readString(game.resolve("config/zoomify.json"), StandardCharsets.UTF_8));
	}
}
