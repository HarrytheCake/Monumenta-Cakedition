package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
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
		assertNotNull(l.container("minecraft:furnace"));
	}

	/** The example of a mod's default layout, with stand-ins for what that mod's plugin registers. */
	@Test
	void modExampleLoadsCleanly() {
		McValues.register();
		CakeUIApi api = CakeUIApi.get();
		api.registerMenuValue("examplemod.press.energy", ContainerPartTest.PressMenu.class, m -> 1200);
		api.registerMenuValue("examplemod.press.capacity", ContainerPartTest.PressMenu.class, m -> 5000);
		api.registerElement("examplemod:tank", c -> { });
		Loader loader = Loader.loadMod(Path.of("examples/mod_layout/assets/examplemod/cakeui"), "examplemod");
		Layout l = loader.layout();
		assertFalse(loader.fatal);
		assertTrue(l.problems.isEmpty(), () -> String.join("\n", l.problems.list().stream().map(Object::toString).toList()));
		assertNotNull(l.container("examplemod:press"));
		assertTrue(l.lang.containsKey("en_us"));
	}
}
