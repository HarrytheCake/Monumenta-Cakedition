package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import io.github.harrythecake.cakeui.TitleMenu;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The main menu's ports. Every hook first asks TitleMenu whether the active layout mentions the
 * piece; with no layout each one just calls vanilla.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Shadow
	private long backgroundFadeStart;
	@Shadow
	@Final
	private boolean doBackgroundFade;

	protected TitleScreenMixin(Text title) {
		super(title);
	}

	private float cakeui$seconds() {
		if (!doBackgroundFade || backgroundFadeStart == 0L) {
			return doBackgroundFade ? 0 : 2;
		}
		return (Util.getMeasuringTimeMs() - backgroundFadeStart) / 1000.0F;
	}

	/** How far the menu has faded in (vanilla's g). */
	private float cakeui$fade() {
		return doBackgroundFade ? MathHelper.clamp(cakeui$seconds() - 1.0F, 0.0F, 1.0F) : 1.0F;
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (TitleMenu.part() != null) {
			TitleMenu.beginFrame(this, mouseX, mouseY, cakeui$fade());
		}
	}

	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/SplashTextResourceSupplier;get()Lnet/minecraft/client/gui/screen/SplashTextRenderer;"))
	private SplashTextRenderer cakeui$splash(SplashTextResourceSupplier supplier, Operation<SplashTextRenderer> original) {
		if (TitleMenu.port("splash") == null) {
			return original.call(supplier);
		}
		return TitleMenu.pickSplash(supplier);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/RotatingCubeMapRenderer;render(FF)V"))
	private void cakeui$panorama(RotatingCubeMapRenderer renderer, float delta, float alpha, Operation<Void> original) {
		if (TitleMenu.port("background") == null || TitleMenu.vanillaBackground()) {
			original.call(renderer, delta, alpha);
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"))
	private void cakeui$overlay(DrawContext context, Identifier texture, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th,
			Operation<Void> original) {
		if (TitleMenu.port("background") == null) {
			original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
			return;
		}
		if (TitleMenu.vanillaBackground()) {
			original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
		}
		context.setShaderColor(1, 1, 1, 1);
		TitleMenu.drawBackground(context, MathHelper.clamp(cakeui$seconds(), 0, 1));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
	private void cakeui$logo(LogoDrawer logo, DrawContext context, int screenWidth, float alpha, Operation<Void> original) {
		if (TitleMenu.port("logo") == null) {
			original.call(logo, context, screenWidth, alpha);
			return;
		}
		float a = alpha * TitleMenu.portOpacity("logo");
		TitleMenu.vanillaPiece("logo", context, screenWidth / 2.0, 52, () -> original.call(logo, context, screenWidth, a));
		TitleMenu.drawPortElements("logo", context, alpha);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;I)V"))
	private void cakeui$splashDraw(SplashTextRenderer splash, DrawContext context, int screenWidth, TextRenderer textRenderer, int alpha,
			Operation<Void> original) {
		if (TitleMenu.port("splash") == null) {
			original.call(splash, context, screenWidth, textRenderer, alpha);
			return;
		}
		float fade = ((alpha >>> 24) & 255) / 255.0F;
		if (!TitleMenu.drawSplash(context, fade)) {
			int a = MathHelper.ceil(fade * TitleMenu.portOpacity("splash") * 255.0F) << 24;
			TitleMenu.vanillaPiece("splash", context, screenWidth / 2.0 + 123, 69, () -> original.call(splash, context, screenWidth, textRenderer, a));
			TitleMenu.drawPortElements("splash", context, fade);
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"))
	private int cakeui$version(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
		if (TitleMenu.port("version") == null) {
			return original.call(context, textRenderer, text, x, y, color);
		}
		String shown = TitleMenu.versionText(text);
		int shownColor = TitleMenu.PORTS.color("version", color);
		int[] out = {0};
		TitleMenu.vanillaPiece("version", context, x, y, () -> out[0] = original.call(context, textRenderer, shown, x, y, shownColor));
		TitleMenu.drawPortElements("version", context, ((color >>> 24) & 255) / 255.0F);
		return out[0];
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$back(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		TitleMenu.drawElements(this, context, "back", cakeui$fade());
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$fade() > 0) {
			TitleMenu.drawElements(this, context, "front", cakeui$fade());
		}
		ScreenPorts.drawButtonIds(this, context);
	}
}
