package io.github.harrythecake.cakemonu.fabric.mixin;

import ch.njol.unofficialmonumentamod.AbilityHandler;
import ch.njol.unofficialmonumentamod.hud.AbilitiesHud;
import io.github.harrythecake.cakemonu.fabric.UmmAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * UMM's ability bar leaves out the icons that have their own place, and while CakeMonu draws one icon on its
 * own it shows only that one ({@link UmmAbilities#visibility}). UMM's names aren't Minecraft's: remap = false.
 */
@Mixin(value = AbilitiesHud.class, remap = false)
public abstract class AbilitiesHudMixin {
	@Inject(method = "isAbilityVisible", at = @At("HEAD"), cancellable = true)
	private void cakemonu$visible(AbilityHandler.AbilityInfo info, boolean forSpaceCalculation, CallbackInfoReturnable<Boolean> cir) {
		Boolean v = UmmAbilities.visibility(info.getOrderId(), forSpaceCalculation);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}

	/** Every icon has its own place: UMM's (empty) bar is off, so it takes no clicks and no room. */
	@Inject(method = "isEnabled", at = @At("RETURN"), cancellable = true)
	private void cakemonu$enabled(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && UmmAbilities.allPlaced()) {
			cir.setReturnValue(false);
		}
	}
}
