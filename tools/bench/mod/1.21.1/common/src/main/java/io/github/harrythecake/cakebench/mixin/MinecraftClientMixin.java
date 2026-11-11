package io.github.harrythecake.cakebench.mixin;

import io.github.harrythecake.cakebench.CakeBench;
import io.github.harrythecake.cakebench.GpuTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.SingleTickProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The end of MinecraftClient.render is the end of one frame, whatever the renderer (Sodium, Nvidium, ...). */
@Mixin(Minecraft.class)
abstract class MinecraftClientMixin {
	@Inject(method = "runTick", at = @At("TAIL"))
	private void cakebench$frame(boolean tick, CallbackInfo ci) {
		CakeBench.onFrame((Minecraft) (Object) this);
	}

	/** The run loop stores this in the client's profiler field each frame, so the GPU timer sees every section. */
	@Inject(method = "constructProfiler", at = @At("RETURN"), cancellable = true)
	private void cakebench$profiler(boolean active, SingleTickProfiler monitor, CallbackInfoReturnable<ProfilerFiller> cir) {
		cir.setReturnValue(GpuTimer.wrap(cir.getReturnValue()));
	}
}
