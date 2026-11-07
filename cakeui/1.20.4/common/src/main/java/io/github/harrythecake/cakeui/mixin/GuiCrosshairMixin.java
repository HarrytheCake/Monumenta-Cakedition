package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.HudCrosshair;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A crosshair style (HUD editor) in place of vanilla's crosshair sprite; the attack indicator after it stays. */
@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {
	@WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0))
	private void cakeui$crosshairSprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!HudCrosshair.draw(g, x + w / 2f, y + h / 2f)) {
			original.call(g, sprite, x, y, w, h);
		}
	}
}
