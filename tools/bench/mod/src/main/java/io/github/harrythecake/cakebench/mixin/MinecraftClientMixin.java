package io.github.harrythecake.cakebench.mixin;

import io.github.harrythecake.cakebench.CakeBench;
import io.github.harrythecake.cakebench.GpuTimer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.TickDurationMonitor;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The end of MinecraftClient.render is the end of one frame, whatever the renderer (Sodium, Nvidium, ...). */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void cakebench$frame(boolean tick, CallbackInfo ci) {
		CakeBench.onFrame((MinecraftClient) (Object) this);
	}

	/** The run loop stores this in the client's profiler field each frame, so the GPU timer sees every section. */
	@Inject(method = "startMonitor", at = @At("RETURN"), cancellable = true)
	private void cakebench$profiler(boolean active, TickDurationMonitor monitor, CallbackInfoReturnable<Profiler> cir) {
		cir.setReturnValue(GpuTimer.wrap(cir.getReturnValue()));
	}
}
