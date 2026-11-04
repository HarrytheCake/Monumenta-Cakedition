package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.HudPart;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The full-screen overlays (vignette, spyglass, pumpkin) inside Gui.extractCameraOverlays: one HUD
 * layer since 1.20.5, on both loaders, so each piece is wrapped here. Powder snow and portal stay vanilla.
 */
@Mixin(Gui.class)
public abstract class GuiCameraOverlaysMixin {
	private static final String PUMPKIN = "textures/misc/pumpkinblur.png";

	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractVignette(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/Entity;)V"))
	private void cakeui$vignette(Gui self, GuiGraphicsExtractor context, Entity entity, Operation<Void> original) {
		HudPart.piece("vignette", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, entity));
	}

	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
	private void cakeui$spyglass(Gui self, GuiGraphicsExtractor context, float scale, Operation<Void> original) {
		HudPart.piece("spyglass", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, scale));
	}

	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
	private void cakeui$overlay(Gui self, GuiGraphicsExtractor context, Identifier texture, float opacity, Operation<Void> original) {
		if (!texture.getPath().equals(PUMPKIN)) {
			original.call(self, context, texture, opacity); // powder snow stays vanilla
			return;
		}
		HudPart.piece("pumpkin", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0, false, () -> original.call(self, context, texture, opacity));
	}
}
