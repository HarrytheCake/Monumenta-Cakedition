package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The main menu's gallery button: 20x20, the mod icon instead of a label. Its label key
 * (cakecosmetic.gallery.title) is its button id for CakeUI layouts.
 */
final class GalleryButton extends Button.Plain {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(CakeCosmetic.MOD_ID, "icon.png");

	GalleryButton(int x, int y, Screen parent) {
		super(x, y, 20, 20, Component.translatable("cakecosmetic.gallery.title"),
				b -> Minecraft.getInstance().setScreen(GalleryScreen.open(parent)), DEFAULT_NARRATION);
		setTooltip(Tooltip.create(Component.translatable("cakecosmetic.gallery.title")));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		extractDefaultSprite(ctx);
		ctx.blit(RenderPipelines.GUI_TEXTURED, ICON, getX() + 2, getY() + 2, 0, 0, 16, 16, 64, 64, 64, 64, ARGB.white(alpha));
	}
}
