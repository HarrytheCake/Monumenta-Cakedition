package io.github.harrythecake.cakeui.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD frame on Fabric 26.1: start and end of Gui.extractRenderState, and the action bar and
 * title, which move through vanilla's own translate. The other pieces are wrapped through Fabric's
 * HudElementRegistry (FabricHud); vignette, spyglass and pumpkin by the common GuiCameraOverlaysMixin.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo ci) {
		HudTiming.start();
		HudPart.beginFrame(context);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cakeui$front(GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo ci) {
		context.nextStratum();
		io.github.harrythecake.cakeui.PluginHud.drawAll(context, delta.getGameTimeDeltaPartialTick(false));
		HudPart.drawLateElements(context, "actionBar", "title");
		HudPart.drawLayer(context, "front");
		HudTiming.end();
	}

	@WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;", ordinal = 0, remap = false))
	private Matrix3x2f cakeui$actionBarPlace(Matrix3x2fStack matrices, float x, float y, Operation<Matrix3x2f> original) {
		Matrix3x2f[] out = {matrices};
		HudPart.translate("actionBar", matrices, x, y, () -> out[0] = original.call(matrices, x, y));
		return out[0];
	}

	@WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;", ordinal = 0, remap = false))
	private Matrix3x2f cakeui$titlePlace(Matrix3x2fStack matrices, float x, float y, Operation<Matrix3x2f> original) {
		Matrix3x2f[] out = {matrices};
		HudPart.translate("title", matrices, x, y, () -> out[0] = original.call(matrices, x, y));
		return out[0];
	}

	@WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
	private void cakeui$actionBarText(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
		cakeui$text("actionBar", context, font, text, x, y, width, color, original);
	}

	@WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
	private void cakeui$titleText(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
		cakeui$text("title", context, font, text, x, y, width, color, original);
	}

	private static void cakeui$text(String port, GuiGraphicsExtractor context, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
		HudPart.Port p = HudPart.active() ? HudPart.port(port) : null;
		if (p == null) {
			original.call(context, font, text, x, y, width, color);
		} else if (!p.hide) {
			int a = (color >>> 24) == 0 ? 255 : color >>> 24;
			original.call(context, font, text, x, y, width, (Math.round(a * p.opacity) << 24) | (color & 0xFFFFFF));
		}
	}
}
