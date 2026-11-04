package io.github.harrythecake.cakeui.fabric.mixin;

import io.github.harrythecake.cakeui.HudPart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The boss bars port on Fabric 1.21+: vanilla's Gui draws them from a lambda layer, so the hook sits on the overlay itself. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void cakeui$pre(GuiGraphics context, CallbackInfo ci) {
		if (HudPart.pre("bossBars", context, context.guiWidth() / 2.0, 12)) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void cakeui$post(GuiGraphics context, CallbackInfo ci) {
		HudPart.post("bossBars", context);
	}
}
