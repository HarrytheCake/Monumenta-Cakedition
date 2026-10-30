package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.TitleMenu;
import io.github.harrythecake.cakeui.Ui;
import io.github.harrythecake.cakeui.model.Node;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws a layout's skin instead of a button's own look (for every subclass, icon buttons too);
 * the button still handles clicks, keys and its tooltip.
 */
@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
	@Shadow
	protected float alpha;

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ClickableWidget;renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
	private void cakeui$skin(ClickableWidget self, DrawContext context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		Node skin = TitleMenu.skin(self);
		if (skin == null) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		Ui.renderer().drawSkin(context, self, skin, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
				self.isSelected(), self.active, self.getMessage().getString(), alpha);
	}
}
