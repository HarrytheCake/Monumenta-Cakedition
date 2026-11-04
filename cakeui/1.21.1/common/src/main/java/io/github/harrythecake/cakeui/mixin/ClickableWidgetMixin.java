package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import io.github.harrythecake.cakeui.Ui;
import io.github.harrythecake.cakeui.model.Node;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws a layout's skin instead of a button's own look (for every subclass, icon buttons too);
 * the button still handles clicks, keys and its tooltip.
 */
@Mixin(AbstractWidget.class)
public abstract class ClickableWidgetMixin {
	@Shadow
	protected float alpha;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void cakeui$skin(AbstractWidget self, GuiGraphics context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		Node skin = ScreenPorts.skin(self);
		if (skin == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		Ui.renderer().drawSkin(context, self, skin, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
				self.isHoveredOrFocused(), self.active, self.getMessage().getString(), alpha);
	}
}
