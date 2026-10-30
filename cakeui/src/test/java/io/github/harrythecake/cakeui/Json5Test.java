package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.json.JValue;
import io.github.harrythecake.cakeui.json.Json5;
import io.github.harrythecake.cakeui.json.Json5Exception;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json5Test {
	@Test
	void readsJson5Features() {
		String text = """
				// comment
				{
				  unquoted: 'single',
				  "quoted": "double",
				  hex: 0xFF,
				  dot: .5,
				  plus: +3,
				  list: [1, 2, 3,],
				  /* block
				     comment */
				  nested: { a: true, b: null, },
				}
				""";
		JValue.JObject o = (JValue.JObject) Json5.parse(text, "t.json5");
		assertEquals("single", ((JValue.JString) o.get("unquoted")).value);
		assertEquals(255, ((JValue.JNumber) o.get("hex")).value);
		assertEquals(0.5, ((JValue.JNumber) o.get("dot")).value);
		assertEquals(3, ((JValue.JNumber) o.get("plus")).value);
		assertEquals(3, ((JValue.JArray) o.get("list")).list.size());
		assertInstanceOf(JValue.JNull.class, ((JValue.JObject) o.get("nested")).get("b"));
		assertEquals(3, o.keyLine("unquoted"));
		assertEquals(11, o.keyLine("nested"));
	}

	@Test
	void errorsNameTheLine() {
		Json5Exception e = assertThrows(Json5Exception.class, () -> Json5.parse("{\n a: 1,\n b: oops\n}", "x.json5"));
		assertEquals(3, e.line);
		assertTrue(e.problem.contains("oops"), e.problem);

		e = assertThrows(Json5Exception.class, () -> Json5.parse("{\n a: 1\n b: 2\n}", "x.json5"));
		assertEquals(3, e.line);

		e = assertThrows(Json5Exception.class, () -> Json5.parse("{\n a: 1,\n a: 2\n}", "x.json5"));
		assertTrue(e.problem.contains("twice"), e.problem);

		e = assertThrows(Json5Exception.class, () -> Json5.parse("{ a: [1, 2 }", "x.json5"));
		assertEquals(1, e.line);
	}
}
