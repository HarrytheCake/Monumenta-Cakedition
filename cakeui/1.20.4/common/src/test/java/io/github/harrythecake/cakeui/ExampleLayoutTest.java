package io.github.harrythecake.cakeui;

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
		McValues.register(); // the real list, so the example can use any game value (none is read here)
		Loader loader = Loader.load(Path.of("examples/window_example"));
		Layout l = loader.layout();
		assertFalse(loader.fatal);
		assertTrue(l.problems.isEmpty(), () -> String.join("\n", l.problems.list().stream().map(Object::toString).toList()));
		assertNotNull(l.part("main_menu"));
		assertNotNull(l.part("hud").builtins.get("pause"));
		assertNotNull(l.part("hud").builtins.get("inventory"));
		assertTrue(l.lang.containsKey("zh_cn"));
	}
}
