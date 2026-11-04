package io.github.harrythecake.cakefixes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.CakeFixesHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * appleSkinBlendQueryFix: see {@link io.github.harrythecake.cakefixes.BlendQuery}. Pseudo: skipped when AppleSkin
 * isn't installed. require = 0: if an AppleSkin update renames enableAlpha, the fix just stops applying.
 * remap = false: AppleSkin's and LWJGL's names aren't Minecraft names.
 */
@Pseudo
@Mixin(targets = "squeek.appleskin.client.HUDOverlayHandler", remap = false)
abstract class AppleSkinHudOverlayMixin {
	@WrapOperation(method = "enableAlpha", require = 0, remap = false,
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glIsEnabled(I)Z", remap = false))
	private boolean cakefixes$trackedBlendState(int cap, Operation<Boolean> original) {
		return CakeFixesHooks.isEnabled(CakeFixes.config().appleSkinBlendQueryFix, cap, c -> original.call(c));
	}
}
