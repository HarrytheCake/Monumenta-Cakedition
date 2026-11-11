package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** One background over the whole screen; any click or Esc goes back. */
final class PreviewScreen extends Screen {
	private final Screen parent;
	private final Backgrounds.Background background;

	PreviewScreen(Screen parent, Backgrounds.Background background) {
		super(Component.literal(background.title()));
		this.parent = parent;
		this.background = background;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		BackgroundTextures.Tex t = BackgroundTextures.full(background);
		if (t == null) {
			ctx.centeredText(font, Component.translatable("cakecosmetic.gallery.loading"), width / 2, height / 2 - 4, 0xFFAAAAAA);
			return;
		}
		BackgroundTextures.drawCover(ctx, t, 0, 0, width, height, 1);
		String artist = background.meta().artist();
		Component line = artist.isEmpty() ? Component.literal(background.title())
				: Component.translatable("cakecosmetic.gallery.titleBy", background.title(), artist);
		ctx.fill(0, height - 16, width, height, 0x90000000);
		ctx.text(font, line, 6, height - 12, 0xFFFFFFFF, true);
		Component hint = Component.translatable("cakecosmetic.preview.back");
		ctx.text(font, hint, width - 6 - font.width(hint), height - 12, 0xFFAAAAAA, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		onClose();
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
