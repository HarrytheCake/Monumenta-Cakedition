package io.github.harrythecake.cakewiki.mixin;

import io.github.harrythecake.cakewiki.CakeWiki;
import io.github.harrythecake.cakewiki.ItemHover;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
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
@Mixin(AbstractContainerScreen.class)
abstract class HandledScreenMixin implements ItemHover {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "keyPressed", cancellable = true, at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/KeyMapping;matches(II)Z"))
	private void cakewiki$lookUpItem(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (CakeWiki.openKey.matches(keyCode, scanCode) && hoveredSlot != null && hoveredSlot.hasItem()) {
			CakeWiki.lookupItem((AbstractContainerScreen<?>) (Object) this, hoveredSlot.getItem());
			cir.setReturnValue(true);
		}
	}

	@Override
	public void cakewiki$hover(Slot slot) {
		hoveredSlot = slot;
	}
}
