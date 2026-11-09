package io.github.harrythecake.cakeui.mixin;

import io.github.harrythecake.cakeui.HudBounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Measures where a HUD port is drawn (for the HUD editor) on 26.1: every render state submitted while
 * {@link HudBounds} records a port adds its screen rectangle (already through the pose) to the port's
 * box. One boolean check otherwise.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateBoundsMixin {
	private static void cakeui$add(ScreenArea area) {
		ScreenRectangle r = area.bounds();
		if (r != null) {
			HudBounds.addScreen(r.left(), r.top(), r.right(), r.bottom());
		}
	}

	@Inject(method = "addGuiElement", at = @At("HEAD"))
	private void cakeui$element(GuiElementRenderState state, CallbackInfo ci) {
		if (HudBounds.recording()) {
			cakeui$add(state);
		}
	}

	@Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"))
	private void cakeui$blit(BlitRenderState state, CallbackInfo ci) {
		if (HudBounds.recording()) {
			cakeui$add(state);
		}
	}

	@Inject(method = "addText", at = @At("HEAD"))
	private void cakeui$text(GuiTextRenderState state, CallbackInfo ci) {
		if (HudBounds.recording()) {
			cakeui$add(state);
		}
	}

	@Inject(method = "addItem", at = @At("HEAD"))
	private void cakeui$item(GuiItemRenderState state, CallbackInfo ci) {
		if (HudBounds.recording()) {
			cakeui$add(state);
		}
	}

	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
	private void cakeui$pip(PictureInPictureRenderState state, CallbackInfo ci) {
		if (HudBounds.recording()) {
			cakeui$add(state);
		}
	}
}
