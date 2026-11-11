package io.github.harrythecake.cakeui.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.cakeui.ContainerPart;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The hovered slot's highlight on a container screen: NeoForge draws it in renderSlotHighlight(GuiGraphics, Slot, …)
 * (Fabric: in render, see the Fabric mixin). A layout's highlight instead of vanilla's white square.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotHighlightMixin {
	@WrapOperation(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;IIF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isHighlightable()Z"))
	private boolean cakeui$highlight(Slot slot, Operation<Boolean> original, @Local(argsOnly = true) GuiGraphics context) {
		return original.call(slot) && ContainerPart.vanillaHighlight((AbstractContainerScreen<?>) (Object) this, context, slot);
	}
}
