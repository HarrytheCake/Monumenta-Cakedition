package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The main menu's gallery button: 20x20, the mod icon instead of a label. Its label key
 * (cakecosmetic.gallery.title) is its button id for CakeUI layouts.
 */
final class GalleryButton extends ButtonWidget {
	private static final Identifier ICON = new Identifier(CakeCosmetic.MOD_ID, "icon.png");

	GalleryButton(int x, int y, Screen parent) {
		super(x, y, 20, 20, Text.translatable("cakecosmetic.gallery.title"),
				b -> MinecraftClient.getInstance().setScreen(GalleryScreen.open(parent)), DEFAULT_NARRATION_SUPPLIER);
		setTooltip(Tooltip.of(Text.translatable("cakecosmetic.gallery.title")));
	}

	@Override
	public void drawMessage(DrawContext ctx, TextRenderer font, int color) {
		ctx.drawTexture(ICON, getX() + 2, getY() + 2, 16, 16, 0, 0, 64, 64, 64, 64);
	}
}
