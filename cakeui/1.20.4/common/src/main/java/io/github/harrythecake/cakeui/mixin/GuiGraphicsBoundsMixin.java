package io.github.harrythecake.cakeui.mixin;

import io.github.harrythecake.cakeui.HudBounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measures where a HUD port is drawn (for the HUD editor): every blit, fill, text and item drawn while
 * {@link HudBounds} records a port is added to its box. One boolean check otherwise.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsBoundsMixin {
	@Shadow
	public abstract com.mojang.blaze3d.vertex.PoseStack pose();

	@Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("HEAD"))
	private void cakeui$blit(ResourceLocation tex, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0, float v1, CallbackInfo ci) {
		if (HudBounds.recording()) {
			HudBounds.add(pose().last().pose(), x1, y1, x2, y2);
		}
	}

	@Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At("HEAD"))
	private void cakeui$blitColored(ResourceLocation tex, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0, float v1,
			float r, float g, float b, float a, CallbackInfo ci) {
		if (HudBounds.recording()) {
			HudBounds.add(pose().last().pose(), x1, y1, x2, y2);
		}
	}

	@Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"))
	private void cakeui$fill(RenderType type, int x1, int y1, int x2, int y2, int z, int color, CallbackInfo ci) {
		if (HudBounds.recording()) {
			HudBounds.add(pose().last().pose(), Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
		}
	}

	@Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"))
	private void cakeui$string(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
		if (HudBounds.recording() && text != null) {
			HudBounds.add(pose().last().pose(), x, y, x + font.width(text), y + font.lineHeight);
		}
	}

	@Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"))
	private void cakeui$sequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
		if (HudBounds.recording()) {
			HudBounds.add(pose().last().pose(), x, y, x + font.width(text), y + font.lineHeight);
		}
	}

	@Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"))
	private void cakeui$item(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
		if (HudBounds.recording() && !stack.isEmpty()) {
			HudBounds.add(pose().last().pose(), x, y, x + 16, y + 16);
		}
	}
}
