package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The dim behind a container screen. Only screens CakeUI knows (the player's inventory) and only
 * with a layout that mentions them; every other container screen calls vanilla.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
	protected HandledScreenMixin(Text title) {
		super(title);
	}

	@WrapOperation(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$background(HandledScreen<?> self, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		ScreenPorts ports = ScreenPorts.of(self);
		if (ports == null || ports.node() == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ports.drawBackground(this, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}
}
