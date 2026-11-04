package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plugins filling ports (ADR 0008), with a made-up mod "testmod". */
class PluginPortsTest {
	@BeforeEach
	@AfterEach
	void clear() {
		PluginPorts.clear();
	}

	@Test
	void apiVersionKnowsFillPort() {
		assertEquals(2, CakeUIApi.VERSION);
	}

	@Test
	void noFillerMeansVanilla() {
		assertFalse(PluginPorts.fill(PluginPorts.MAIN_MENU_BACKGROUND, false, null, null, 1));
		assertFalse(PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, null, null, 1));
		assertNull(PluginPorts.owner(PluginPorts.MAIN_MENU_BACKGROUND));
	}

	@Test
	void fillerDrawsWhenTheLayoutDoesNotMentionThePort() {
		AtomicInteger calls = new AtomicInteger();
		float[] seen = {0};
		CakeUIApi.get().fillPort("main_menu/background", "testmod", (ctx, screen, alpha) -> {
			calls.incrementAndGet();
			seen[0] = alpha;
			return true;
		});
		assertEquals("testmod", PluginPorts.owner(PluginPorts.MAIN_MENU_BACKGROUND));
		assertTrue(PluginPorts.fill(PluginPorts.MAIN_MENU_BACKGROUND, false, null, null, 0.5F));
		assertEquals(0.5F, seen[0]);
		// the layout mentions the port: the layout wins, the filler isn't even asked
		assertFalse(PluginPorts.fill(PluginPorts.MAIN_MENU_BACKGROUND, true, null, null, 1));
		assertEquals(1, calls.get());
		// the other port is untouched
		assertFalse(PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, null, null, 1));
	}

	@Test
	void fillerWithNothingToShowLeavesVanilla() {
		CakeUIApi.get().fillPort("loading_screen/background", "testmod", (ctx, screen, alpha) -> false);
		assertFalse(PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, null, null, 1));
	}

	@Test
	void throwingFillerFallsBackToVanilla() {
		AtomicInteger calls = new AtomicInteger();
		CakeUIApi.get().fillPort("loading_screen/background", "testmod", (ctx, screen, alpha) -> {
			calls.incrementAndGet();
			throw new IllegalStateException("broken on purpose");
		});
		assertFalse(PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, null, null, 1));
		assertFalse(PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, null, null, 1));
		assertEquals(2, calls.get());
	}

	@Test
	void onlyKnownPortsAndOneFillerEach() {
		CakeUIApi api = CakeUIApi.get();
		assertThrows(IllegalArgumentException.class, () -> api.fillPort("hud/hotbar", "testmod", (c, s, a) -> true));
		assertThrows(NullPointerException.class, () -> api.fillPort("main_menu/background", "testmod", null));
		api.fillPort("main_menu/background", "testmod", (c, s, a) -> true);
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> api.fillPort("main_menu/background", "othermod", (c, s, a) -> true));
		assertTrue(e.getMessage().contains("testmod"), e.getMessage());
		assertEquals("testmod", PluginPorts.owner(PluginPorts.MAIN_MENU_BACKGROUND));
	}
}
