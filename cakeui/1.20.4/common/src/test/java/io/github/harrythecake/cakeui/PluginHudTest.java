package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HUD pieces other mods add, with a made-up mod "testmod". */
class PluginHudTest {
	@TempDir
	Path dir;

	@BeforeEach
	@AfterEach
	void clear() {
		PluginHud.clear();
	}

	@Test
	void namesAreCheckedAndUnique() {
		CakeUIApi api = CakeUIApi.get();
		api.addHudPiece("minimap", "testmod", (ctx, w, h, delta) -> {
		});
		assertEquals(List.of("minimap"), PluginHud.names());
		assertEquals("testmod", PluginHud.owner("minimap"));
		assertNull(PluginHud.owner("compass"));
		assertThrows(IllegalStateException.class, () -> api.addHudPiece("minimap", "othermod", (ctx, w, h, delta) -> {
		}));
		assertThrows(IllegalArgumentException.class, () -> api.addHudPiece("hotbar", "testmod", (ctx, w, h, delta) -> {
		}));
		assertThrows(IllegalArgumentException.class, () -> api.addHudPiece("my map", "testmod", (ctx, w, h, delta) -> {
		}));
	}

	@Test
	void aLayoutCanNameAnAddedPieceAsAPort() throws IOException {
		Files.writeString(dir.resolve("window.json5"), "{ format: 1, design: [640, 360] }");
		Files.createDirectories(dir.resolve("hud"));
		Files.writeString(dir.resolve("hud/layout.json5"), """
				{
				  ports: {
				    minimap: { x: -10, scale: 0.8, elements: { label: { type: "text", text: "N" } } },
				  },
				}
				""");
		// without the piece, the port is unknown
		Layout before = Loader.load(dir).layout();
		assertTrue(before.problems.list().stream().anyMatch(p -> p.message().contains("unknown port 'minimap'")));
		CakeUIApi.get().addHudPiece("minimap", "testmod", (ctx, w, h, delta) -> {
		});
		Layout after = Loader.load(dir).layout();
		assertTrue(after.problems.list().isEmpty(), after.problems.list().toString());
		Node hud = after.part("hud");
		assertEquals(1, hud.ports.get("minimap").children.size());
	}
}
