package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.api.CakeUIApi;
import io.github.harrythecake.cakeui.model.GameValues;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Container screens: the Minecraft members the mixins and ContainerPart rely on, and the menu values. */
class ContainerPartTest {
	/** A made-up machine menu, as a mod would write it (no menu type: getType() throws, like the player's inventory). */
	static final class PressMenu extends AbstractContainerMenu {
		int progress = 7;

		PressMenu() {
			super(null, 1);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}

	@Test
	void mixinTargetsExist() throws Exception {
		// strings in ContainerScreenMixin, the accessors and the Fabric highlight mixin: a rename breaks them only at runtime
		Class<?> c = AbstractContainerScreen.class;
		c.getDeclaredMethod("init");
		c.getDeclaredMethod("render", GuiGraphics.class, int.class, int.class, float.class);
		c.getDeclaredMethod("renderBackground", GuiGraphics.class, int.class, int.class, float.class);
		c.getDeclaredMethod("renderBg", GuiGraphics.class, float.class, int.class, int.class);
		c.getDeclaredMethod("renderSlot", GuiGraphics.class, Slot.class);
		c.getDeclaredMethod("renderLabels", GuiGraphics.class, int.class, int.class);
		for (String f : List.of("leftPos", "topPos", "imageWidth", "imageHeight", "hoveredSlot")) {
			c.getDeclaredField(f);
		}
		Slot.class.getDeclaredField("x");
		Slot.class.getDeclaredField("y");
		Slot.class.getMethod("isHighlightable");
		AbstractContainerMenu.class.getDeclaredField("dataSlots");
		MultiPlayerGameMode.class.getMethod("handleInventoryButtonClick", int.class, int.class);
		Screen.class.getMethod("setTooltipForNextRenderPass", List.class);
	}

	@Test
	void aMenuWithoutATypeIsNeverLaidOut() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap(); // a menu holds ItemStacks, which need the registries
		assertNull(ContainerPart.menuId(new PressMenu()));
	}

	@Test
	void menuValuesReadTheOpenMenuOrTheirFallback() {
		CakeUIApi.get().registerMenuValue("testmod.press.progress", PressMenu.class, m -> m.progress);
		CakeUIApi.get().registerMenuValue("testmod.press.state", PressMenu.class, m -> "busy", "closed");
		// no screen is open in a unit test
		assertEquals(0.0, GameValues.resolve("testmod.press.progress").eval(null));
		assertEquals("closed", GameValues.resolve("testmod.press.state").eval(null));
		assertThrows(IllegalArgumentException.class, () -> CakeUIApi.get().registerMenuValue("Press", PressMenu.class, m -> 1));
	}

	@Test
	void menuDataIsANumberedFamily() {
		McValues.register();
		assertTrue(GameValues.has("menu.data.0"));
		assertTrue(GameValues.has("menu.data.12"));
		assertEquals(0.0, GameValues.resolve("menu.data.3").eval(null), "no menu open: 0");
		assertTrue(GameValues.has("menu.id") && GameValues.has("slot.index"));
	}
}
