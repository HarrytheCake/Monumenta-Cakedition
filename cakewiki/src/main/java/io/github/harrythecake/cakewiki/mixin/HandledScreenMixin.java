package io.github.harrythecake.cakewiki.mixin;

import io.github.harrythecake.cakewiki.CakeWiki;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The open key over an item opens its wiki page. Hooked at the inventory-key check, which vanilla reaches
 * only after the screen's own widgets (search boxes, the anvil's name field) turned the key down, so typing
 * a K never triggers it.
 */
@Mixin(HandledScreen.class)
abstract class HandledScreenMixin {
	@Shadow
	protected Slot focusedSlot;

	@Inject(method = "keyPressed", cancellable = true, at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/option/KeyBinding;matchesKey(II)Z"))
	private void cakewiki$lookUpItem(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (CakeWiki.openKey != null && CakeWiki.openKey.matchesKey(keyCode, scanCode)
				&& focusedSlot != null && focusedSlot.hasStack()) {
			CakeWiki.lookupItem((HandledScreen<?>) (Object) this, focusedSlot.getStack());
			cir.setReturnValue(true);
		}
	}
}
