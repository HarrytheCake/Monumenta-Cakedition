package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import io.github.harrythecake.cakeui.LaunchPart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game launching screen (`game_launch`): Mojang's overlay at start and on resource reloads.
 * Ports background (the brand-colour fill), logo and bar. With no layout every hook calls vanilla.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow
	private float progress;
	@Shadow
	@Final
	private boolean reloading;
	/** This frame's context, for the hooks whose call has none. */
	@Unique
	private DrawContext cakeui$context;

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		cakeui$context = context;
		if (LaunchPart.part() != null) {
			LaunchPart.beginFrame(context, progress, reloading);
		}
	}

	/** First start: vanilla clears the window to the brand colour (black when the background port hides it). */
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clear(IZ)V"))
	private void cakeui$clear(int mask, boolean checkError, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(mask, checkError);
			return;
		}
		if (!LaunchPart.PORTS.vanilla("background")) {
			GlStateManager._clearColor(0, 0, 0, 1);
		}
		original.call(mask, checkError);
		LaunchPart.drawBack(cakeui$context, 1);
	}

	/** Resource reloads: vanilla fades the brand colour in over the screen, and out at the end. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIII)V"))
	private void cakeui$fill(DrawContext context, RenderLayer layer, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(context, layer, x1, y1, x2, y2, color);
			return;
		}
		float alpha = (color >>> 24) / 255.0F;
		if (LaunchPart.PORTS.vanilla("background")) {
			int a = Math.round((color >>> 24) * LaunchPart.PORTS.portOpacity("background"));
			original.call(context, layer, x1, y1, x2, y2, (a << 24) | (color & 0xFFFFFF));
		}
		context.draw();
		LaunchPart.drawBack(context, alpha);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;setShaderColor(FFFF)V", ordinal = 0))
	private void cakeui$logoAlpha(DrawContext context, float r, float g, float b, float a, Operation<Void> original) {
		original.call(context, r, g, b, LaunchPart.part() == null ? a : LaunchPart.logoAlpha(a));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"))
	private void cakeui$logo(DrawContext context, Identifier texture, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th,
			Operation<Void> original) {
		if (LaunchPart.part() == null || LaunchPart.PORTS.port("logo") == null) {
			original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
			return;
		}
		double cx = context.getScaledWindowWidth() / 2.0;
		double cy = context.getScaledWindowHeight() / 2.0;
		LaunchPart.PORTS.vanillaPiece("logo", context, cx, cy, () -> original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashOverlay;renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V"))
	private void cakeui$bar(SplashOverlay self, DrawContext context, int minX, int minY, int maxX, int maxY, float opacity, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(self, context, minX, minY, maxX, maxY, opacity);
			return;
		}
		LaunchPart.bar(opacity);
		if (LaunchPart.PORTS.port("bar") == null) {
			original.call(self, context, minX, minY, maxX, maxY, opacity);
			return;
		}
		LaunchPart.PORTS.vanillaPiece("bar", context, (minX + maxX) / 2.0, (minY + maxY) / 2.0,
				() -> original.call(self, context, minX, minY, maxX, maxY, opacity));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (LaunchPart.part() != null) {
			LaunchPart.drawFront(context);
		}
	}
}
