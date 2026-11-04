package io.github.harrythecake.cakeui.mixin;

import io.github.harrythecake.cakeui.WindowPart;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The window title: the layout's `title` when it sets one (vanilla's stays available as {window.title}). */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void cakeui$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(WindowPart.title(cir.getReturnValue()));
	}
}
