package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.HudPart;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The full-screen overlays (vignette, spyglass, pumpkin) inside Gui.renderCameraOverlays: one HUD
 * layer since 1.20.5, on both loaders, so each piece is wrapped here. Powder snow and portal stay vanilla.
 */
@Mixin(Gui.class)
public abstract class GuiCameraOverlaysMixin {
	private static final String PUMPKIN = "textures/misc/pumpkinblur.png";

	@WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderVignette(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;)V"))
	private void cakeui$vignette(Gui self, GuiGraphics context, Entity entity, Operation<Void> original) {
		HudPart.piece("vignette", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, entity));
	}

	@WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	private void cakeui$spyglass(Gui self, GuiGraphics context, float scale, Operation<Void> original) {
		HudPart.piece("spyglass", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, scale));
	}

	@WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;F)V"))
	private void cakeui$overlay(Gui self, GuiGraphics context, ResourceLocation texture, float opacity, Operation<Void> original) {
		if (!texture.getPath().equals(PUMPKIN)) {
			original.call(self, context, texture, opacity); // powder snow stays vanilla
			return;
		}
		HudPart.piece("pumpkin", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, texture, opacity));
	}
}
