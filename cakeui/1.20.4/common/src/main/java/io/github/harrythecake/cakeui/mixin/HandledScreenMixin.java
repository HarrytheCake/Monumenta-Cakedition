package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The dim behind a container screen. Only screens CakeUI knows (the player's inventory) and only
 * with a layout that mentions them; every other container screen calls vanilla.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
	protected HandledScreenMixin(Component title) {
		super(title);
	}

	@WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void cakeui$background(AbstractContainerScreen<?> self, GuiGraphics context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		ScreenPorts ports = ScreenPorts.of(self);
		if (ports == null || ports.node() == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ports.drawBackground(this, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}
}
