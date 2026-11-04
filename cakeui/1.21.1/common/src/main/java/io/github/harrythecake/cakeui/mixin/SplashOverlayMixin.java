package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import io.github.harrythecake.cakeui.LaunchPart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow
	private float currentProgress;
	@Shadow
	@Final
	private boolean fadeIn;
	/** This frame's context, for the hooks whose call has none. */
	@Unique
	private GuiGraphics cakeui$context;

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		cakeui$context = context;
		if (LaunchPart.part() != null) {
			LaunchPart.beginFrame(context, currentProgress, fadeIn);
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
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
	private void cakeui$fill(GuiGraphics context, RenderType layer, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(context, layer, x1, y1, x2, y2, color);
			return;
		}
		float alpha = (color >>> 24) / 255.0F;
		if (LaunchPart.PORTS.vanilla("background")) {
			int a = Math.round((color >>> 24) * LaunchPart.PORTS.portOpacity("background"));
			original.call(context, layer, x1, y1, x2, y2, (a << 24) | (color & 0xFFFFFF));
		}
		context.flush();
		LaunchPart.drawBack(context, alpha);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;setColor(FFFF)V", ordinal = 0))
	private void cakeui$logoAlpha(GuiGraphics context, float r, float g, float b, float a, Operation<Void> original) {
		original.call(context, r, g, b, LaunchPart.part() == null ? a : LaunchPart.logoAlpha(a));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
	private void cakeui$logo(GuiGraphics context, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th,
			Operation<Void> original) {
		if (LaunchPart.part() == null || LaunchPart.PORTS.port("logo") == null) {
			original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th);
			return;
		}
		double cx = context.guiWidth() / 2.0;
		double cy = context.guiHeight() / 2.0;
		LaunchPart.PORTS.vanillaPiece("logo", context, cx, cy, () -> original.call(context, texture, x, y, w, h, u, v, rw, rh, tw, th));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
	private void cakeui$bar(LoadingOverlay self, GuiGraphics context, int minX, int minY, int maxX, int maxY, float opacity, Operation<Void> original) {
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
	private void cakeui$front(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (LaunchPart.part() != null) {
			LaunchPart.drawFront(context);
		}
	}
}
