package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The main menu's gallery button: 20x20, the mod icon instead of a label. Its label key
 * (cakecosmetic.gallery.title) is its button id for CakeUI layouts.
 */
final class GalleryButton extends Button {
	private static final ResourceLocation ICON = new ResourceLocation(CakeCosmetic.MOD_ID, "icon.png");

	GalleryButton(int x, int y, Screen parent) {
		super(x, y, 20, 20, Component.translatable("cakecosmetic.gallery.title"),
				b -> Minecraft.getInstance().setScreen(GalleryScreen.open(parent)), DEFAULT_NARRATION);
		setTooltip(Tooltip.create(Component.translatable("cakecosmetic.gallery.title")));
	}

	@Override
	public void renderString(GuiGraphics ctx, Font font, int color) {
		ctx.blit(ICON, getX() + 2, getY() + 2, 16, 16, 0, 0, 64, 64, 64, 64);
	}
}
