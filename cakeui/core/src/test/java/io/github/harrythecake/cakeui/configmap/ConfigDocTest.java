package io.github.harrythecake.cakeui.configmap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Value-only edits: everything but the value's own characters stays as it was (ADR 0011). */
class ConfigDocTest {
	private static List<String> p(String dotted) {
		return ConfigDoc.path(dotted);
	}

	// shaped like AsteorBar's / AppleSkin's JSON5: tabs, a comment above every key, nested objects, no trailing comma
	private static final String JSON5 = """
			{
				"overlay": {
					// Whether to enable the overlay.
					"enableOverlay": true,
					// The text scale of the overlay
					"overlayTextScale": 1.0,
					"overlayLayoutStyle": 0
				},
				/* block comment */ "showSaturationHudOverlay": true
			}
			""";

	@Test
	void json5ReplacesOnlyTheValue() {
		ConfigDoc d = ConfigDoc.parse(Format.JSON5, JSON5);
		assertEquals(Boolean.TRUE, d.get(p("overlay.enableOverlay")));
		assertEquals(1.0, d.get(p("overlay.overlayTextScale")));
		assertSame(ConfigDoc.OTHER, d.get(p("overlay")));
		assertNull(d.get(p("overlay.missing")));
		d.set(p("overlay.enableOverlay"), false);
		d.set(p("overlay.overlayTextScale"), 0.75);
		d.set(p("overlay.overlayLayoutStyle"), 3L);
		d.set(p("showSaturationHudOverlay"), false);
		assertEquals(JSON5.replace("\"enableOverlay\": true", "\"enableOverlay\": false")
				.replace("1.0,", "0.75,").replace("\"overlayLayoutStyle\": 0", "\"overlayLayoutStyle\": 3")
				.replace("\"showSaturationHudOverlay\": true", "\"showSaturationHudOverlay\": false"), d.text());
	}

	@Test
	void json5AddsAMissingKeyInTheFilesStyle() {
		ConfigDoc d = ConfigDoc.parse(Format.JSON5, JSON5);
		d.set(p("overlay.overlayBarInnerHeight"), 4L);
		assertTrue(d.text().contains("\"overlayLayoutStyle\": 0,\n\t\t\"overlayBarInnerHeight\": 4\n\t},"), d.text());
		d.set(p("newTop"), "x");
		assertTrue(d.text().contains("\"showSaturationHudOverlay\": true,\n\t\"newTop\": \"x\"\n}"), d.text());
		assertEquals(4.0, ConfigDoc.parse(Format.JSON5, d.text()).get(p("overlay.overlayBarInnerHeight")));
	}

	@Test
	void trailingCommaStyleIsKept() {
		String t = "{\n  a: 1,\n  b: 'two',\n}\n";
		ConfigDoc d = ConfigDoc.parse(Format.JSON5, t);
		d.set(p("b"), "three");
		d.set(p("c"), true);
		assertEquals("{\n  a: 1,\n  b: \"three\",\n  c: true,\n}\n", d.text());
	}

	@Test
	void missingObjectsAreCreated() {
		ConfigDoc d = ConfigDoc.parse(Format.JSON, "{\n  \"a\": 1\n}");
		d.set(p("x.y.z"), 2L);
		assertEquals(2.0, ConfigDoc.parse(Format.JSON, d.text()).get(p("x.y.z")));
		ConfigDoc empty = ConfigDoc.parse(Format.JSON, "{}");
		empty.set(p("k"), "v");
		assertEquals("{\n  \"k\": \"v\"\n}", empty.text());
	}

	@Test
	void jsonNumbersKeepTheirKind() {
		// shaped like Zoomify's JSON: 2-space indent, floats written with a decimal point
		ConfigDoc d = ConfigDoc.parse(Format.JSON, "{\n  \"initialZoom\": 4,\n  \"zoomInTime\": 1.0,\n  \"zoomKeyBehaviour\": \"hold\"\n}\n");
		d.set(p("initialZoom"), 6L);
		d.set(p("zoomInTime"), 2.0);
		d.set(p("zoomKeyBehaviour"), "toggle");
		assertEquals("{\n  \"initialZoom\": 6,\n  \"zoomInTime\": 2.0,\n  \"zoomKeyBehaviour\": \"toggle\"\n}\n", d.text());
	}

	@Test
	void listsAndObjectsAreNotOverwritten() {
		ConfigDoc d = ConfigDoc.parse(Format.JSON, "{\"list\": [1, 2], \"obj\": {}}");
		assertThrows(ConfigDocException.class, () -> d.set(p("list"), 1L));
		assertThrows(ConfigDocException.class, () -> d.set(p("obj"), 1L));
		assertThrows(ConfigDocException.class, () -> d.set(p("list.x"), 1L));
	}

	@Test
	void badJsonNamesTheLine() {
		ConfigDocException e = assertThrows(ConfigDocException.class, () -> ConfigDoc.parse(Format.JSON, "{\n  \"a\": 1\n  \"b\": 2\n}"));
		assertEquals(3, e.line);  // where the unexpected "b" is (the comma is missing before it)
	}

	@Test
	void keysWithDotsAndEscapes() {
		assertEquals(List.of("render_settings", "dimensionFogDistance", "minecraft:overworld"),
				p("render_settings.dimensionFogDistance.minecraft:overworld"));
		assertEquals(List.of("a.b", "c"), p("a\\.b.c"));
		ConfigDoc d = ConfigDoc.parse(Format.JSON, "{\"text\": \"say \\\"hi\\\"\\n\"}");
		assertEquals("say \"hi\"\n", d.get(p("text")));
		d.set(p("text"), "a\\b \"q\"");
		assertEquals("{\"text\": \"a\\\\b \\\"q\\\"\"}", d.text());
	}

	// shaped like Item Highlighter's Forge-style TOML
	private static final String TOML = """

			#Client Configuration
			[client]

				[client.options]
					# If new item markers should be cleared when the inventory is closed.
					clear_on_close = true
					#Allowed Values: UpperLeft, UpperRight, LowerLeft, LowerRight
					icon_position = "UpperLeft" # trailing comment
					scale = 1.5
					list = [
						"a", # inside
						"b",
					]

			[other]
				"quoted key" = 'literal'
			""";

	@Test
	void tomlReplacesOnlyTheValue() {
		ConfigDoc d = ConfigDoc.parse(Format.TOML, TOML);
		assertEquals(Boolean.TRUE, d.get(p("client.options.clear_on_close")));
		assertEquals("UpperLeft", d.get(p("client.options.icon_position")));
		assertEquals(1.5, d.get(p("client.options.scale")));
		assertSame(ConfigDoc.OTHER, d.get(p("client.options.list")));
		assertEquals("literal", d.get(List.of("other", "quoted key")));
		d.set(p("client.options.clear_on_close"), false);
		d.set(p("client.options.icon_position"), "LowerRight");
		d.set(p("client.options.scale"), 2.0);
		assertEquals(TOML.replace("clear_on_close = true", "clear_on_close = false")
				.replace("\"UpperLeft\" #", "\"LowerRight\" #").replace("scale = 1.5", "scale = 2.0"), d.text());
	}

	@Test
	void tomlAddsKeysToTheirTable() {
		ConfigDoc d = ConfigDoc.parse(Format.TOML, TOML);
		d.set(p("client.options.show_on_hotbar"), true);
		assertTrue(d.text().contains("\t\t]\n\t\tshow_on_hotbar = true\n"), d.text());
		d.set(p("brand.new"), 3L);
		assertTrue(d.text().endsWith("\n[brand]\nnew = 3\n"), d.text());
		ConfigDoc again = ConfigDoc.parse(Format.TOML, d.text());
		assertEquals(Boolean.TRUE, again.get(p("client.options.show_on_hotbar")));
		assertEquals(3.0, again.get(p("brand.new")));
		assertThrows(ConfigDocException.class, () -> again.set(p("client.options.list"), 1L));
	}

	@Test
	void optionsTxtIsRawText() {
		String t = "version:3700\r\nrenderDistance:12\r\nrenderClouds:\"false\"\r\nkey_key.attack:key.mouse.left\r\n";
		ConfigDoc d = ConfigDoc.parse(Format.OPTIONS, t);
		assertEquals("12", d.get(List.of("renderDistance")));
		assertEquals("\"false\"", d.get(List.of("renderClouds")));
		assertEquals("key.mouse.left", d.get(List.of("key_key.attack")));
		d.set(List.of("renderDistance"), 8L);
		d.set(List.of("renderClouds"), "\"fast\"");
		d.set(List.of("guiScale"), 2L);
		assertEquals("version:3700\r\nrenderDistance:8\r\nrenderClouds:\"fast\"\r\nkey_key.attack:key.mouse.left\r\nguiScale:2\r\n", d.text());
		assertThrows(ConfigDocException.class, () -> d.set(List.of("x"), "two\nlines"));
	}

	@Test
	void propertiesKeepCommentsAndSeparators() {
		String t = "# Sodium Extra\nmixin.foo=true\nmixin.bar : false\n! old comment\n";
		ConfigDoc d = ConfigDoc.parse(Format.PROPERTIES, t);
		assertEquals("true", d.get(List.of("mixin.foo")));
		assertEquals("false", d.get(List.of("mixin.bar")));
		d.set(List.of("mixin.bar"), true);
		d.set(List.of("mixin.new"), false);
		assertEquals("# Sodium Extra\nmixin.foo=true\nmixin.bar : true\n! old comment\nmixin.new=false\n", d.text());
	}
}
