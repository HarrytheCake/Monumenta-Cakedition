package io.github.harrythecake.cakemap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakemap.core.WaypointColors;
import io.github.harrythecake.cakemap.core.WaypointKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** en_us, zh_cn and zh_tw have the same keys; every waypoint kind has a name and an SVG icon. */
class LangTest {
	private static JsonObject lang(String name) throws IOException {
		try (InputStream in = LangTest.class.getResourceAsStream("/assets/cakemap/lang/" + name + ".json")) {
			assertNotNull(in, name);
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static String read(String path) {
		try (InputStream in = LangTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void sameKeys() throws IOException {
		Set<String> en = new TreeSet<>(lang("en_us").keySet());
		assertEquals(en, new TreeSet<>(lang("zh_cn").keySet()));
		assertEquals(en, new TreeSet<>(lang("zh_tw").keySet()));
		for (WaypointKind k : WaypointKind.values()) {
			assertTrue(en.contains(k.translationKey()), k.translationKey());
		}
		for (String key : CakeMapKeys.ALL) {
			assertTrue(en.contains("key.cakemap." + key), key);
		}
	}

	@Test
	void everyIconExists() {
		for (WaypointKind k : WaypointKind.values()) {
			String path = "/assets/cakemap/textures/waypoint/" + k.icon() + ".svg";
			assertNotNull(LangTest.class.getResource(path), path);
		}
		assertNotNull(LangTest.class.getResource("/assets/cakemap/textures/waypoint/star.svg"));
		String player = read("/assets/cakemap/textures/waypoint/player.svg");
		String target = WaypointColors.recolorTarget(player);
		assertTrue(player.replaceFirst("<!--.*?-->", "").toLowerCase(java.util.Locale.ROOT).contains(target.toLowerCase(java.util.Locale.ROOT)),
				"player.svg uses the colour a waypoint's colour replaces (" + target + ")");
		assertFalse(WaypointColors.recolorSvg(player, 0xE5484D).toLowerCase(java.util.Locale.ROOT).contains(target.toLowerCase(java.util.Locale.ROOT)),
				"recolouring replaces every " + target);
		assertNotNull(LangTest.class.getResource("/assets/cakemap/textures/gui/arrow.png"));
		assertNotNull(LangTest.class.getResource("/assets/cakemap/icon.png"));
		assertTrue(Files.exists(Path.of("src/main/resources/assets/cakemap/lang/en_us.json")));
	}

	/** The key names CakeMap registers (kept in step with CakeMap.onInitializeClient). */
	static final class CakeMapKeys {
		static final String[] ALL = {"world_map", "waypoints", "new_waypoint", "settings", "temporary_waypoint", "enlarge", "cave_mode", "zoom_in",
				"zoom_out", "toggle_minimap"};
	}
}
