package io.github.harrythecake.cakeui.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD's ports on Fabric 1.21.1 (vanilla's Gui draws in layers since 1.20.5): every vanilla piece
 * but chat (CakeChat's) and F3. Each hook asks HudPart first; a piece the layout doesn't mention (and
 * every piece without a layout) is drawn exactly as vanilla draws it. Vignette, spyglass and pumpkin
 * are the common GuiCameraOverlaysMixin's (NeoForge uses it too); the boss bars are BossHealthOverlayMixin's.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	// ---- frame ----

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		HudTiming.start();
		HudPart.beginFrame(context);
	}

	/** The HUD's back layer: after the full-screen overlays, before crosshair and hotbar. */
	@Inject(method = "renderCameraOverlays", at = @At("RETURN"))
	private void cakeui$back(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		HudPart.drawLayer(context, "back");
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		io.github.harrythecake.cakeui.PluginHud.drawAll(context, delta.getGameTimeDeltaPartialTick(false));
		HudPart.drawLateElements(context, "actionBar", "title");
		HudPart.drawLayer(context, "front");
		HudTiming.end();
	}

	// ---- pieces drawn by one call ----

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void cakeui$hotbar(Gui self, GuiGraphics context, DeltaTracker delta, Operation<Void> original) {
		HudPart.piece("hotbar", context, context.guiWidth() / 2.0, context.guiHeight() - 11, false, () -> original.call(self, context, delta));
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void cakeui$crosshairPre(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		if (HudPart.pre("crosshair", context, context.guiWidth() / 2.0, context.guiHeight() / 2.0)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("RETURN"))
	private void cakeui$crosshairPost(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		HudPart.post("crosshair", context);
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$mountHealth(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("mountHealth", context, context.guiWidth() / 2.0 + 91, context.guiHeight() - 39, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V"))
	private void cakeui$experience(Gui self, GuiGraphics context, int x, Operation<Void> original) {
		HudPart.piece("experience", context, context.guiWidth() / 2.0, context.guiHeight() - 29, false, () -> original.call(self, context, x));
	}

	/** Since 1.20.5 the level number is its own layer; it belongs to the experience port. */
	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void cakeui$levelPre(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		if (HudPart.pre("experience", context, context.guiWidth() / 2.0, context.guiHeight() - 29)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceLevel", at = @At("RETURN"))
	private void cakeui$levelPost(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		HudPart.post("experience", context);
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$heldItemName(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("heldItemName", context, context.guiWidth() / 2.0, context.guiHeight() - 59, false, () -> original.call(self, context));
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void cakeui$effectsPre(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		if (HudPart.pre("statusEffects", context, context.guiWidth(), 0)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderEffects", at = @At("RETURN"))
	private void cakeui$effectsPost(GuiGraphics context, DeltaTracker delta, CallbackInfo ci) {
		HudPart.post("statusEffects", context);
	}

	@WrapOperation(method = "renderScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V"))
	private void cakeui$scoreboard(Gui self, GuiGraphics context, Objective objective, Operation<Void> original) {
		HudPart.piece("scoreboard", context, context.guiWidth(), context.guiHeight() / 2.0, false, () -> original.call(self, context, objective));
	}

	@WrapOperation(method = "renderTabList", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"))
	private void cakeui$tabList(PlayerTabOverlay list, GuiGraphics context, int width, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
		HudPart.piece("tabList", context, context.guiWidth() / 2.0, 0, false, () -> original.call(list, context, width, scoreboard, objective));
	}

	// ---- status bars: health, armor and hunger are one call each, air is sprites drawn in a loop ----

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"))
	private void cakeui$health(Gui self, GuiGraphics context, Player player, int x, int y, int lines, int regen, float max, int last, int health,
			int absorption, boolean blink, Operation<Void> original) {
		HudPart.piece("health", context, context.guiWidth() / 2.0 - 91, context.guiHeight() - 39, true,
				() -> original.call(self, context, player, x, y, lines, regen, max, last, health, absorption, blink));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V"))
	private void cakeui$armor(GuiGraphics context, Player player, int y, int lines, int spacing, int x, Operation<Void> original) {
		HudPart.piece("armor", context, context.guiWidth() / 2.0 - 91, context.guiHeight() - 49, true,
				() -> original.call(context, player, y, lines, spacing, x));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V"))
	private void cakeui$hunger(Gui self, GuiGraphics context, Player player, int y, int x, Operation<Void> original) {
		HudPart.piece("hunger", context, context.guiWidth() / 2.0 + 91, context.guiHeight() - 39, true, () -> original.call(self, context, player, y, x));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
	private void cakeui$airSprite(GuiGraphics context, ResourceLocation sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!HudPart.active() || !sprite.getPath().startsWith("hud/air")) {
			original.call(context, sprite, x, y, w, h);
			return;
		}
		HudPart.piece("air", context, context.guiWidth() / 2.0 + 91, context.guiHeight() - 49, true, () -> original.call(context, sprite, x, y, w, h));
	}

	@Inject(method = "renderPlayerHealth", at = @At("TAIL"))
	private void cakeui$statusElements(GuiGraphics context, CallbackInfo ci) {
		HudPart.drawLateElements(context, "health", "armor", "hunger", "air");
	}

	// ---- action bar and title: drawn in place, after vanilla's own translate ----

	@WrapOperation(method = "renderOverlayMessage", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void cakeui$actionBarPlace(PoseStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("actionBar", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "renderTitle", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void cakeui$titlePlace(PoseStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("title", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "renderOverlayMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
	private int cakeui$actionBarText(GuiGraphics context, Font font, Component text, int x, int y, int width, int color, Operation<Integer> original) {
		return cakeui$text("actionBar", context, font, text, x, y, width, color, original);
	}

	@WrapOperation(method = "renderTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"))
	private int cakeui$titleText(GuiGraphics context, Font font, Component text, int x, int y, int width, int color, Operation<Integer> original) {
		return cakeui$text("title", context, font, text, x, y, width, color, original);
	}

	private static int cakeui$text(String port, GuiGraphics context, Font font, Component text, int x, int y, int width, int color, Operation<Integer> original) {
		HudPart.Port p = HudPart.active() ? HudPart.port(port) : null;
		if (p == null) {
			return original.call(context, font, text, x, y, width, color);
		}
		return p.hide ? 0 : original.call(context, font, text, x, y, width, cakeui$fade(color, p.opacity));
	}

	private static int cakeui$fade(int color, float opacity) {
		if (opacity == 1) {
			return color;
		}
		int a = (color >>> 24) == 0 ? 255 : color >>> 24;
		return (Math.round(a * opacity) << 24) | (color & 0xFFFFFF);
	}
}
