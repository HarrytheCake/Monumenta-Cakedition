package io.github.harrythecake.cakecosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** "New background: <title>" (one unlock) or "3 new backgrounds" (several at once), plus how to open the gallery. */
final class UnlockToast implements Toast {
	private static final Identifier SPRITE = new Identifier("toast/advancement");
	private static final Identifier ICON = new Identifier(CakeCosmetic.MOD_ID, "icon.png");
	private final Text line1;
	private final Text line2;

	private UnlockToast(Text line1, Text line2) {
		this.line1 = line1;
		this.line2 = line2;
	}

	static void show(List<String> unlocked) {
		Text first;
		if (unlocked.size() == 1) {
			Backgrounds.Background b = Backgrounds.get(unlocked.get(0));
			String title = b != null ? b.title() : unlocked.get(0);
			first = Text.translatable("cakecosmetic.toast.one", title);
		} else {
			first = Text.translatable("cakecosmetic.toast.many", unlocked.size());
		}
		Text second = CakeCosmetic.galleryKey().isUnbound()
				? Text.translatable("cakecosmetic.toast.hint.menu")
				: Text.translatable("cakecosmetic.toast.hint.key", CakeCosmetic.galleryKey().getBoundKeyLocalizedText());
		MinecraftClient.getInstance().getToastManager().add(new UnlockToast(first, second));
	}

	@Override
	public int getWidth() {
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		int text = Math.max(font.getWidth(line1), font.getWidth(line2));
		return Math.max(160, Math.min(260, text + 38));
	}

	@Override
	public Visibility draw(DrawContext ctx, ToastManager manager, long startTime) {
		TextRenderer font = manager.getClient().textRenderer;
		// the sprite is 160 wide and has no nine-slice: left half, middle pieces, right half
		int w = getWidth();
		ctx.drawGuiTexture(SPRITE, 160, 32, 0, 0, 0, 0, 80, 32);
		for (int x = 80; x < w - 80; x += 60) {
			ctx.drawGuiTexture(SPRITE, 160, 32, 50, 0, x, 0, Math.min(60, w - 80 - x), 32);
		}
		ctx.drawGuiTexture(SPRITE, 160, 32, 80, 0, w - 80, 0, 80, 32);
		ctx.drawTexture(ICON, 8, 8, 16, 16, 0, 0, 64, 64, 64, 64);
		ctx.drawText(font, trim(font, line1), 30, 7, 0xFFFFFF00, false);
		ctx.drawText(font, trim(font, line2), 30, 18, 0xFFFFFFFF, false);
		return startTime >= 6000L * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	private String trim(TextRenderer font, Text text) {
		String s = text.getString();
		int max = getWidth() - 36;
		if (font.getWidth(s) <= max) {
			return s;
		}
		return font.trimToWidth(s, max - font.getWidth("…")) + "…";
	}
}
