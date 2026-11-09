package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** One background over the whole screen; any click or Esc goes back. */
final class PreviewScreen extends Screen {
	private final Screen parent;
	private final Backgrounds.Background background;

	PreviewScreen(Screen parent, Backgrounds.Background background) {
		super(Text.literal(background.title()));
		this.parent = parent;
		this.background = background;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		BackgroundTextures.Tex t = BackgroundTextures.full(background);
		if (t == null) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("cakecosmetic.gallery.loading"), width / 2, height / 2 - 4, 0xFFAAAAAA);
			return;
		}
		BackgroundTextures.drawCover(ctx, t, 0, 0, width, height, 1);
		String artist = background.meta().artist();
		Text line = artist.isEmpty() ? Text.literal(background.title())
				: Text.translatable("cakecosmetic.gallery.titleBy", background.title(), artist);
		ctx.fill(0, height - 16, width, height, 0x90000000);
		ctx.drawText(textRenderer, line, 6, height - 12, 0xFFFFFFFF, true);
		Text hint = Text.translatable("cakecosmetic.preview.back");
		ctx.drawText(textRenderer, hint, width - 6 - textRenderer.getWidth(hint), height - 12, 0xFFAAAAAA, true);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		close();
		return true;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
