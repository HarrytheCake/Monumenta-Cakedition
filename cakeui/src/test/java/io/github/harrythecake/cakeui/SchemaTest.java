package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;
import io.github.harrythecake.cakeui.model.PropKinds;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON Schema that editors use must know every property CakeUI accepts. */
class SchemaTest {
	private static JValue.JObject obj(JValue.JObject o, String... path) {
		for (String p : path) {
			o = (JValue.JObject) o.get(p);
		}
		return o;
	}

	@Test
	void schemaKnowsEveryProperty() throws Exception {
		JValue.JObject schema = (JValue.JObject) Json5.parse(Files.readString(Path.of("schema/layout.schema.json")), "layout.schema.json");
		JValue.JObject element = obj(schema, "definitions", "element", "properties");
		for (String type : PropKinds.ELEMENT_TYPES) {
			for (String key : PropKinds.keysFor(type)) {
				assertTrue(element.map.containsKey(key), "schema element lacks '" + key + "' (" + type + ")");
			}
		}
		JValue.JObject layout = obj(schema, "properties");
		for (String key : PropKinds.keysFor("subwindow")) {
			if (key.equals("type")) {
				continue; // a subwindow file has no type
			}
			assertTrue(layout.map.containsKey(key), "schema layout lacks '" + key + "'");
		}
		JValue.JObject ports = obj(schema, "properties", "ports", "properties");
		for (var e : io.github.harrythecake.cakeui.model.Loader.PORTS.entrySet()) {
			for (String port : e.getValue()) {
				assertTrue(ports.map.containsKey(port), "schema ports lack '" + port + "' (" + e.getKey() + ")");
			}
		}
		for (String group : PropKinds.GROUPS) {
			JValue.JObject g = obj(schema, "definitions", group, "properties");
			for (String key : PropKinds.groupKeys(group)) {
				assertTrue(g.map.containsKey(key), "schema " + group + " lacks '" + key + "'");
			}
		}
	}
}
