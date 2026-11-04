package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.api.ElementContext;
import io.github.harrythecake.cakeui.api.ElementType;
import io.github.harrythecake.cakeui.api.PropKind;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Problems;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What another mod can register, and how layouts use it (made-up mod "testmod"). */
class ApiTest {
	@TempDir
	Path dir;

	@BeforeAll
	static void register() {
		CakeUIApi api = CakeUIApi.get();
		api.registerValue("testmod.mana", () -> 42);
		api.registerElement("testmod:gauge", new ElementType() {
			@Override
			public Map<String, PropKind> properties() {
				return Map.of("level", PropKind.NUMBER, "tone", PropKind.COLOR);
			}

			@Override
			public void draw(ElementContext context) {
			}
		});
		api.registerAction("testmod:ping", (argument, screen) -> {
		});
	}

	private void write(String path, String text) throws IOException {
		Path p = dir.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text);
	}

	private static String problems(Layout l) {
		return l.problems.list().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
	}

	@Test
	void layoutsUseWhatModsRegister() throws IOException {
		write("window.json5", "{ format: 1 }");
		write("main_menu/layout.json5", """
				{
				  elements: {
				    g: { type: "testmod:gauge", level: "= testmod.mana / 100", tone: "#ff0000", x: 10 },
				    a: { type: "button", action: "testmod:ping" },
				    b: { type: "button", action: { "testmod:ping": "hello" } },
				    c: { type: "button", action: "testmod:nope" },
				    d: { type: "testmod:gauge", bogus: 1 },
				    e: { type: "othermod:thing" },
				  },
				}
				""");
		Layout l = Loader.load(dir).layout();
		Map<String, Node> byName = l.part("main_menu").children.stream().collect(Collectors.toMap(n -> n.name, n -> n));
		assertTrue(byName.get("g").has("level"), problems(l));
		assertTrue(byName.get("g").has("tone"));
		assertEquals("testmod:ping", byName.get("a").action.kind());
		assertEquals("hello", byName.get("b").action.argument());
		assertNotNull(byName.get("d"));
		String p = problems(l);
		assertTrue(p.contains("unknown action 'testmod:nope': no installed mod registered it (mods registered: testmod:ping)"), p);
		assertTrue(p.contains("'bogus' isn't a property of testmod:gauge"), p);
		assertTrue(p.contains("unknown type 'othermod:thing'") && p.contains("is that mod installed?"), p);
		assertEquals(3, l.problems.list().size(), p);
	}

	@Test
	void namesAndKindsAreChecked() {
		CakeUIApi api = CakeUIApi.get();
		assertThrows(IllegalArgumentException.class, () -> api.registerValue("mana", () -> 1));
		assertThrows(IllegalArgumentException.class, () -> api.registerAction("ping", (a, s) -> {
		}));
		assertThrows(IllegalArgumentException.class, () -> api.registerElement("gauge", c -> {
		}));
		// "color" is a colour everywhere: a mod can't make it a number
		assertThrows(IllegalArgumentException.class, () -> api.registerElement("testmod:bad", new ElementType() {
			@Override
			public Map<String, PropKind> properties() {
				return Map.of("color", PropKind.NUMBER);
			}

			@Override
			public void draw(ElementContext context) {
			}
		}));
	}
}
