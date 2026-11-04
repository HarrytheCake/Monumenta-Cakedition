package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player's inventory (`hud/subwindow_inventory/`): background (the dim), panel, player model and
 * title ports, and the front layer (under item tooltips, which 26.1 draws deferred). Slots and items
 * never move. With no layout every hook calls vanilla. 26.1 has no shader colour, so the panel's
 * opacity is the texture colour's alpha.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
	protected InventoryScreenMixin(InventoryMenu menu, RecipeBookComponent<?> recipeBook, Inventory inventory, Component title) {
		super(menu, recipeBook, inventory, title);
	}

	private static boolean cakeui$active() {
		return ScreenPorts.INVENTORY.node() != null;
	}

	/** The dim behind the inventory: the background port, its elements and the back layer. */
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void cakeui$background(InventoryScreen self, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, Operation<Void> original) {
		if (!cakeui$active()) {
			original.call(self, context, mouseX, mouseY, delta);
			return;
		}
		ScreenPorts.INVENTORY.drawBackground(this, context, () -> original.call(self, context, mouseX, mouseY, delta));
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void cakeui$panel(GuiGraphicsExtractor context, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int tw, int th,
			Operation<Void> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("panel") == null) {
			original.call(context, pipeline, texture, x, y, u, v, w, h, tw, th);
			return;
		}
		if (p.vanilla("panel")) {
			int alpha = Math.round(p.portOpacity("panel") * 255);
			context.blit(pipeline, texture, x, y, u, v, w, h, tw, th, (alpha << 24) | 0xFFFFFF);
		}
		p.drawPortElementsIn("panel", context, x, y, w, h);
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
	private void cakeui$player(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int size, float f, float mouseX, float mouseY, LivingEntity entity,
			Operation<Void> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("player") == null) {
			original.call(context, x1, y1, x2, y2, size, f, mouseX, mouseY, entity);
			return;
		}
		if (!p.vanilla("player")) {
			return;
		}
		// moved through the arguments, not the matrix: the entity box ignores the matrix
		double[] o = p.offset("player");
		double cx = (x1 + x2) / 2.0 + o[0];
		double cy = (y1 + y2) / 2.0 + o[1];
		double hw = (x2 - x1) / 2.0 * o[2];
		double hh = (y2 - y1) / 2.0 * o[2];
		original.call(context, (int) Math.round(cx - hw), (int) Math.round(cy - hh), (int) Math.round(cx + hw), (int) Math.round(cy + hh),
				(int) Math.round(size * o[2]), f, mouseX, mouseY, entity);
	}

	@WrapOperation(method = "extractLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
	private void cakeui$title(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Void> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("title") == null) {
			original.call(context, font, text, x, y, color, shadow);
			return;
		}
		Component shown = p.port("title").has("text") ? Component.literal(p.text("title", text.getString())) : text;
		int c = p.color("title", color);
		int shownColor = (Math.round((c >>> 24) * p.portOpacity("title")) << 24) | (c & 0xFFFFFF);
		p.vanillaPiece("title", context, x, y, () -> original.call(context, font, shown, x, y, shownColor, shadow));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cakeui$front(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.INVENTORY.drawPortElementsIn("title", context, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
			ScreenPorts.INVENTORY.drawElements(this, context, "front", 1);
		}
		ScreenPorts.drawButtonIds(this, context);
	}
}
