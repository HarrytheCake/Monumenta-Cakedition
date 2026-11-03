package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
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
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
	@Shadow
	@Final
	private boolean showMenu;

	protected GameMenuScreenMixin(Text title) {
		super(title);
	}

	private boolean cakeui$active() {
		return showMenu && ScreenPorts.PAUSE.node() != null;
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.PAUSE.beginFrame(this, mouseX, mouseY);
		}
	}

	@WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$background(GameMenuScreen self, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (!cakeui$active()) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ScreenPorts.PAUSE.drawBackground(this, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.PAUSE.drawElements(this, context, "front", 1);
		}
		ScreenPorts.drawButtonIds(this, context);
	}
}
