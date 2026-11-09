package io.github.harrythecake.cakemap.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.cakemap.InWorldWaypoints;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The camera of this frame's world view, for the in-world waypoint markers: the projection as the
 * world is drawn with it (view bobbing included), the view rotation and the camera position. On
 * 1.20.4 / 1.21.1 the loaders' after-level events give the same; 26.1 has no common one.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V",
			ordinal = 0))
	private void cakemap$camera(DeltaTracker deltaTracker, CallbackInfo ci, @Local CameraRenderState cameraState, @Local(ordinal = 0) Matrix4f projection) {
		InWorldWaypoints.capture(cameraState.viewRotationMatrix, projection, cameraState.pos);
	}
}
