package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.LoadingPart;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The background of the loading screens (connecting, loading terrain, …), which all draw it through
 * Screen.render. Every other screen, and every screen with no layout, calls vanilla.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$loadingBackground(Screen self, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (LoadingPart.kind(self) == null || ScreenPorts.LOADING.node() == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ScreenPorts.LOADING.drawBackground(self, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}
}
