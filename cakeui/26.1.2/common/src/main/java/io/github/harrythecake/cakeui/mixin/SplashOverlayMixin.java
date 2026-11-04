package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.harrythecake.cakeui.LaunchPart;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game launching screen (`game_launch`): Mojang's overlay at start and on resource reloads.
 * Ports background (the brand-colour fill or clear colour), logo and bar. With no layout every hook
 * calls vanilla. 26.1: the first start sets the frame's clear colour instead of filling.
 */
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
	@Shadow
	private float currentProgress;
	@Shadow
	@Final
	private boolean fadeIn;
	@Unique
	private GuiGraphicsExtractor cakeui$context;
	/** The background was drawn this frame (by a fill or the clear colour). */
	@Unique
	private boolean cakeui$backDrawn;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		cakeui$context = context;
		cakeui$backDrawn = false;
		if (LaunchPart.part() != null) {
			LaunchPart.beginFrame(context, currentProgress, fadeIn);
		}
	}

	/** First start: vanilla clears the window to the brand colour (black when the background port hides it). */
	@WrapOperation(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;clearColorOverride:I", opcode = 181)) // PUTFIELD
	private void cakeui$clear(GuiRenderState state, int color, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(state, color);
			return;
		}
		original.call(state, LaunchPart.PORTS.vanilla("background") ? color : 0xFF000000);
		cakeui$backDrawn = true;
		LaunchPart.drawBack(cakeui$context, 1);
	}

	/** Resource reloads: vanilla fades the brand colour in over the screen, and out at the end. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void cakeui$fill(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(context, x1, y1, x2, y2, color);
			return;
		}
		float alpha = (color >>> 24) / 255.0F;
		if (LaunchPart.PORTS.vanilla("background")) {
			int a = Math.round((color >>> 24) * LaunchPart.PORTS.portOpacity("background"));
			original.call(context, x1, y1, x2, y2, (a << 24) | (color & 0xFFFFFF));
		}
		cakeui$backDrawn = true;
		LaunchPart.drawBack(context, alpha);
	}

	/** The two halves of the logo; their colour carries vanilla's fade. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void cakeui$logo(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h,
			int rw, int rh, int tw, int th, int color, Operation<Void> original) {
		if (LaunchPart.part() == null) {
			original.call(context, pipeline, texture, x, y, u, v, w, h, rw, rh, tw, th, color);
			return;
		}
		float alpha = LaunchPart.logoAlpha((color >>> 24) / 255.0F);
		int shown = (Math.round(alpha * 255) << 24) | (color & 0xFFFFFF);
		if (LaunchPart.PORTS.port("logo") == null) {
			original.call(context, pipeline, texture, x, y, u, v, w, h, rw, rh, tw, th, shown);
			return;
		}
		double cx = context.guiWidth() / 2.0;
		double cy = context.guiHeight() / 2.0;
		LaunchPart.PORTS.vanillaPiece("logo", context, cx, cy, () -> original.call(context, pipeline, texture, x, y, u, v, w, h, rw, rh, tw, th, shown));
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
	private void cakeui$bar(LoadingOverlay self, GuiGraphicsExtractor context, int minX, int minY, int maxX, int maxY, float opacity, Operation<Void> original) {
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

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cakeui$front(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (LaunchPart.part() != null) {
			LaunchPart.drawFront(context);
		}
	}
}
