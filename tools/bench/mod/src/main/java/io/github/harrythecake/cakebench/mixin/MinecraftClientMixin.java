package io.github.harrythecake.cakebench.mixin;

import io.github.harrythecake.cakebench.CakeBench;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The end of MinecraftClient.render is the end of one frame, whatever the renderer (Sodium, Nvidium, ...). */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void cakebench$frame(boolean tick, CallbackInfo ci) {
		CakeBench.onFrame((MinecraftClient) (Object) this);
	}
}
