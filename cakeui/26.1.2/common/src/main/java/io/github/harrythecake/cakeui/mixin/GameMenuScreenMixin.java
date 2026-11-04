package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pause menu (`hud/subwindow_pause/`): background port, elements behind and in front of the
 * buttons. Buttons (and the "Game Menu" title, a text widget) go through the buttons port in
 * ScreenPorts. F3+Esc's "Game Paused" screen is left alone. With no layout every hook calls vanilla.
 */
@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
	@Shadow
	@Final
	private boolean showPauseMenu;

	protected GameMenuScreenMixin(Component title) {
		super(title);
	}

	private boolean cakeui$active() {
		return showPauseMenu && ScreenPorts.PAUSE.node() != null;
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void cakeui$background(PauseScreen self, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (!cakeui$active()) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ScreenPorts.PAUSE.drawBackground(this, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cakeui$front(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.PAUSE.drawElements(this, context, "front", 1);
		}
		ScreenPorts.drawButtonIds(this, context);
	}
}
