package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player's inventory (`hud/subwindow_inventory/`): panel, player model and title ports, and the
 * front layer (under item tooltips). Slots and items never move. With no layout every hook calls
 * vanilla.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
	protected InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	private static boolean cakeui$active() {
		return ScreenPorts.INVENTORY.node() != null;
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.INVENTORY.beginFrame(this, mouseX, mouseY);
		}
	}

	@WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V"))
	private void cakeui$panel(DrawContext context, Identifier texture, int x, int y, int u, int v, int w, int h, Operation<Void> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("panel") == null) {
			original.call(context, texture, x, y, u, v, w, h);
			return;
		}
		if (p.vanilla("panel")) {
			context.setShaderColor(1, 1, 1, p.portOpacity("panel"));
			original.call(context, texture, x, y, u, v, w, h);
			context.setShaderColor(1, 1, 1, 1);
		}
		p.drawPortElementsIn("panel", context, x, y, w, h);
	}

	@WrapOperation(method = "drawBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V"))
	private void cakeui$player(DrawContext context, int x1, int y1, int x2, int y2, int size, float f, float mouseX, float mouseY, LivingEntity entity,
			Operation<Void> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("player") == null) {
			original.call(context, x1, y1, x2, y2, size, f, mouseX, mouseY, entity);
			return;
		}
		if (!p.vanilla("player")) {
			return;
		}
		// moved through the arguments, not the matrix: drawEntity's scissor box ignores the matrix
		double[] o = p.offset("player");
		double cx = (x1 + x2) / 2.0 + o[0];
		double cy = (y1 + y2) / 2.0 + o[1];
		double hw = (x2 - x1) / 2.0 * o[2];
		double hh = (y2 - y1) / 2.0 * o[2];
		original.call(context, (int) Math.round(cx - hw), (int) Math.round(cy - hh), (int) Math.round(cx + hw), (int) Math.round(cy + hh),
				(int) Math.round(size * o[2]), f, mouseX, mouseY, entity);
	}

	@WrapOperation(method = "drawForeground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"))
	private int cakeui$title(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
		ScreenPorts p = ScreenPorts.INVENTORY;
		if (!cakeui$active() || p.port("title") == null) {
			return original.call(context, textRenderer, text, x, y, color, shadow);
		}
		Text shown = p.port("title").has("text") ? Text.literal(p.text("title", text.getString())) : text;
		int shownColor = p.color("title", color);
		int[] out = {0};
		p.vanillaPiece("title", context, x, y, () -> out[0] = original.call(context, textRenderer, shown, x, y, shownColor, shadow));
		return out[0];
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/ingame/InventoryScreen;drawMouseoverTooltip(Lnet/minecraft/client/gui/DrawContext;II)V"))
	private void cakeui$front(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cakeui$active()) {
			ScreenPorts.INVENTORY.drawPortElementsIn("title", context, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
			ScreenPorts.INVENTORY.drawElements(this, context, "front", 1);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$ids(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenPorts.drawButtonIds(this, context);
	}
}
