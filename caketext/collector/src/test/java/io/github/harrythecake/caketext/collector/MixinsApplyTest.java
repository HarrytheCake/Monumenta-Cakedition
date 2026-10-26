package io.github.harrythecake.caketext.collector;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import io.github.harrythecake.caketext.collector.mixin.BossBarHudAccessor;
import io.github.harrythecake.caketext.collector.mixin.PlayerListHudAccessor;
import io.github.harrythecake.caketext.collector.mixin.TextDisplayAccessor;
import org.junit.jupiter.api.Test;

/** See the translator's MixinsApplyTest: loading the targets inside Fabric's class loader applies our mixins. */
class MixinsApplyTest {
	private static Class<?> load(String name) throws ClassNotFoundException {
		return Class.forName(name, false, MixinsApplyTest.class.getClassLoader());
	}

	@Test
	void mixinsApply() throws Exception {
		Class<?> hud = load("net.minecraft.client.gui.hud.InGameHud");
		assertTrue(Arrays.stream(hud.getDeclaredMethods()).anyMatch(m -> m.getName().contains("caketext$collectTitle")));
		assertTrue(Arrays.stream(hud.getDeclaredMethods()).anyMatch(m -> m.getName().contains("caketext$collectSubtitle")));
		assertTrue(BossBarHudAccessor.class.isAssignableFrom(load("net.minecraft.client.gui.hud.BossBarHud")));
		assertTrue(PlayerListHudAccessor.class.isAssignableFrom(load("net.minecraft.client.gui.hud.PlayerListHud")));
		assertTrue(TextDisplayAccessor.class.isAssignableFrom(load("net.minecraft.entity.decoration.DisplayEntity$TextDisplayEntity")));
	}
}
