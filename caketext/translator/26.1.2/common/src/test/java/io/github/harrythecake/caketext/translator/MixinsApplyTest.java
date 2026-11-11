package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import io.github.harrythecake.caketext.translator.mixin.ChatHudAccessor;
import io.github.harrythecake.caketext.translator.mixin.ScreenAccessor;
import org.junit.jupiter.api.Test;

/**
 * Runs inside Fabric's class loader (fabric-loader-junit, client side): loading a target class applies our mixins,
 * and a wrong target or injection point throws here instead of in the game.
 */
class MixinsApplyTest {
	private static Class<?> load(String name) throws ClassNotFoundException {
		// false: don't run static initializers (they need a bootstrapped game); the mixins apply on load anyway
		return Class.forName(name, false, MixinsApplyTest.class.getClassLoader());
	}

	private static void assertHandler(String className, String handler) throws ClassNotFoundException {
		Class<?> c = load(className);
		boolean found = Arrays.stream(c.getDeclaredMethods()).anyMatch(m -> m.getName().contains(handler));
		assertTrue(found, handler + " not merged into " + className);
	}

	@Test
	void injectorsApply() throws Exception {
		assertHandler("net.minecraft.client.gui.Gui", "caketext$title");
		assertHandler("net.minecraft.client.gui.Gui", "caketext$subtitle");
		assertHandler("net.minecraft.client.gui.Gui", "caketext$sidebarTitle");
		assertHandler("net.minecraft.client.gui.Gui", "caketext$sidebarRow");
		assertHandler("net.minecraft.client.gui.components.BossHealthOverlay", "caketext$name");
		assertHandler("net.minecraft.client.gui.components.ChatComponent", "caketext$chat");
		assertHandler("net.minecraft.client.renderer.entity.EntityRenderer", "caketext$label");
		assertHandler("net.minecraft.world.entity.Display$TextDisplay", "caketext$text");
	}

	@Test
	void accessorsApply() throws Exception {
		assertTrue(ChatHudAccessor.class.isAssignableFrom(load("net.minecraft.client.gui.components.ChatComponent")));
		assertTrue(ScreenAccessor.class.isAssignableFrom(load("net.minecraft.client.gui.screens.Screen")));
	}
}
