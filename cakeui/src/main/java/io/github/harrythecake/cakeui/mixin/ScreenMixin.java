package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.LoadingPart;
import io.github.harrythecake.cakeui.PluginPorts;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The background of the loading screens (connecting, loading terrain, …), which all draw it through
 * Screen.render. A plugin's filler draws it when the layout doesn't mention the port (ADR 0008);
 * every other screen, and every loading screen with neither, calls vanilla.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$loadingBackground(Screen self, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (LoadingPart.kind(self) == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		if (ScreenPorts.LOADING.node() == null) {
			if (!PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, false, context, self, 1)) {
				original.call(self, context, mouseX, mouseY, delta);
			}
			return;
		}
		// a layout's loading screen: its background port wins; without one, the plugin's filler, else vanilla
		boolean mentioned = ScreenPorts.LOADING.port("background") != null;
		ScreenPorts.LOADING.drawBackground(self, context, () -> {
			if (!PluginPorts.fill(PluginPorts.LOADING_BACKGROUND, mentioned, context, self, 1)) {
				original.call(self, context, mouseX, mouseY, delta);
			}
		});
	}
}
