package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The example layout shipped in the repo must load without a single problem. */
class ExampleLayoutTest {
	@Test
	void exampleLoadsCleanly() {
		for (String name : new String[] {"time", "menu.version", "menu.splash", "mouse.x", "mouse.y"}) {
			GameValues.register(name, () -> 0.0);
		}
		Loader loader = Loader.load(Path.of("examples/window_example"));
		Layout l = loader.layout();
		assertFalse(loader.fatal);
		assertTrue(l.problems.isEmpty(), () -> String.join("\n", l.problems.list().stream().map(Object::toString).toList()));
		assertNotNull(l.part("main_menu"));
		assertTrue(l.lang.containsKey("zh_cn"));
	}
}
