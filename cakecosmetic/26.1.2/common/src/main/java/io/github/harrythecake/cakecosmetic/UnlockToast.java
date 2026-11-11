package io.github.harrythecake.cakecosmetic;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** "New background: <title>" (one unlock) or "3 new backgrounds" (several at once), plus how to open the gallery. */
final class UnlockToast implements Toast {
	private static final Identifier SPRITE = Identifier.withDefaultNamespace("toast/advancement");
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(CakeCosmetic.MOD_ID, "icon.png");
	private final Component line1;
	private final Component line2;
	private Visibility visibility = Visibility.SHOW;

	private UnlockToast(Component line1, Component line2) {
		this.line1 = line1;
		this.line2 = line2;
	}

	static void show(List<String> unlocked) {
		Component first;
		if (unlocked.size() == 1) {
			Backgrounds.Background b = Backgrounds.get(unlocked.get(0));
			String title = b != null ? b.title() : unlocked.get(0);
			first = Component.translatable("cakecosmetic.toast.one", title);
		} else {
			first = Component.translatable("cakecosmetic.toast.many", unlocked.size());
		}
		Component second = CakeCosmetic.galleryKey() == null || CakeCosmetic.galleryKey().isUnbound()
				? Component.translatable("cakecosmetic.toast.hint.menu")
				: Component.translatable("cakecosmetic.toast.hint.key", CakeCosmetic.galleryKey().getTranslatedKeyMessage());
		Minecraft.getInstance().getToastManager().addToast(new UnlockToast(first, second));
	}

	@Override
	public int width() {
		Font font = Minecraft.getInstance().font;
		int text = Math.max(font.width(line1), font.width(line2));
		return Math.max(160, Math.min(260, text + 38));
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long startTime) {
		visibility = startTime >= 6000L * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, Font font, long startTime) {
		// the sprite is 160 wide and has no nine-slice: left half, middle pieces, right half
		int w = width();
		ctx.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE, 160, 32, 0, 0, 0, 0, 80, 32);
		for (int x = 80; x < w - 80; x += 60) {
			ctx.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE, 160, 32, 50, 0, x, 0, Math.min(60, w - 80 - x), 32);
		}
		ctx.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITE, 160, 32, 80, 0, w - 80, 0, 80, 32);
		ctx.blit(RenderPipelines.GUI_TEXTURED, ICON, 8, 8, 0, 0, 16, 16, 64, 64, 64, 64);
		ctx.text(font, trim(font, line1), 30, 7, 0xFFFFFF00, false);
		ctx.text(font, trim(font, line2), 30, 18, 0xFFFFFFFF, false);
	}

	private String trim(Font font, Component text) {
		String s = text.getString();
		int max = width() - 36;
		if (font.width(s) <= max) {
			return s;
		}
		return font.plainSubstrByWidth(s, max - font.width("…")) + "…";
	}
}
