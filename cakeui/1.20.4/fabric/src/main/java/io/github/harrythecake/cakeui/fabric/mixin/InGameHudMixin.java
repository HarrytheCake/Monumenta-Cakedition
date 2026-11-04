package io.github.harrythecake.cakeui.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The HUD's ports: every vanilla piece but chat (CakeChat's) and F3. Each hook asks HudPart first;
 * a piece the layout doesn't mention (and every piece without a layout) is drawn exactly as vanilla
 * draws it, so other mods' changes to it still apply.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	private static final String PUMPKIN = "textures/misc/pumpkinblur.png";

	@Shadow
	protected int screenWidth;
	@Shadow
	protected int screenHeight;

	// ---- frame ----

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(GuiGraphics context, float tickDelta, CallbackInfo ci) {
		HudTiming.start();
		HudPart.beginFrame(context);
	}

	/** The HUD's back layer: after the full-screen overlays, before the hotbar. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;", ordinal = 0))
	private void cakeui$back(GuiGraphics context, float tickDelta, CallbackInfo ci) {
		HudPart.drawLayer(context, "back");
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(GuiGraphics context, float tickDelta, CallbackInfo ci) {
		io.github.harrythecake.cakeui.PluginHud.drawAll(context, tickDelta);
		HudPart.drawLateElements(context, "actionBar", "title");
		HudPart.drawLayer(context, "front");
		HudTiming.end();
	}

	// ---- full-screen overlays ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderVignette(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;)V"))
	private void cakeui$vignette(Gui self, GuiGraphics context, Entity entity, Operation<Void> original) {
		HudPart.piece("vignette", context, screenWidth / 2.0, screenHeight / 2.0, false, () -> original.call(self, context, entity));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSpyglassOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
	private void cakeui$spyglass(Gui self, GuiGraphics context, float scale, Operation<Void> original) {
		HudPart.piece("spyglass", context, screenWidth / 2.0, screenHeight / 2.0, false, () -> original.call(self, context, scale));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;F)V"))
	private void cakeui$overlay(Gui self, GuiGraphics context, ResourceLocation texture, float opacity, Operation<Void> original) {
		if (!texture.getPath().equals(PUMPKIN)) {
			original.call(self, context, texture, opacity); // powder snow stays vanilla
			return;
		}
		HudPart.piece("pumpkin", context, screenWidth / 2.0, screenHeight / 2.0, false, () -> original.call(self, context, texture, opacity));
	}

	// ---- pieces ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderHotbar(FLnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$hotbar(Gui self, float tickDelta, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("hotbar", context, screenWidth / 2.0, screenHeight - 11, false, () -> original.call(self, tickDelta, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$crosshair(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("crosshair", context, screenWidth / 2.0, screenHeight / 2.0, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$bossBars(BossHealthOverlay bars, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("bossBars", context, screenWidth / 2.0, 12, false, () -> original.call(bars, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$mountHealth(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("mountHealth", context, screenWidth / 2.0 + 91, screenHeight - 39, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V"))
	private void cakeui$experience(Gui self, GuiGraphics context, int x, Operation<Void> original) {
		HudPart.piece("experience", context, screenWidth / 2.0, screenHeight - 29, false, () -> original.call(self, context, x));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$heldItemName(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("heldItemName", context, screenWidth / 2.0, screenHeight - 59, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderEffects(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void cakeui$statusEffects(Gui self, GuiGraphics context, Operation<Void> original) {
		HudPart.piece("statusEffects", context, screenWidth, 0, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V"))
	private void cakeui$scoreboard(Gui self, GuiGraphics context, Objective objective, Operation<Void> original) {
		HudPart.piece("scoreboard", context, screenWidth, screenHeight / 2.0, false, () -> original.call(self, context, objective));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"))
	private void cakeui$tabList(PlayerTabOverlay list, GuiGraphics context, int width, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
		HudPart.piece("tabList", context, screenWidth / 2.0, 0, false, () -> original.call(list, context, width, scoreboard, objective));
	}

	// ---- status bars: health is one call, armor / hunger / air are sprites drawn in loops ----

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"))
	private void cakeui$health(Gui self, GuiGraphics context, Player player, int x, int y, int lines, int regen, float max, int last, int health,
			int absorption, boolean blink, Operation<Void> original) {
		HudPart.piece("health", context, screenWidth / 2.0 - 91, screenHeight - 39, true,
				() -> original.call(self, context, player, x, y, lines, regen, max, last, health, absorption, blink));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
	private void cakeui$statusSprite(GuiGraphics context, ResourceLocation sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!HudPart.active()) {
			original.call(context, sprite, x, y, w, h);
			return;
		}
		String path = sprite.getPath();
		if (path.startsWith("hud/armor")) {
			HudPart.piece("armor", context, screenWidth / 2.0 - 91, screenHeight - 49, true, () -> original.call(context, sprite, x, y, w, h));
		} else if (path.startsWith("hud/food")) {
			HudPart.piece("hunger", context, screenWidth / 2.0 + 91, screenHeight - 39, true, () -> original.call(context, sprite, x, y, w, h));
		} else if (path.startsWith("hud/air")) {
			HudPart.piece("air", context, screenWidth / 2.0 + 91, screenHeight - 49, true, () -> original.call(context, sprite, x, y, w, h));
		} else {
			original.call(context, sprite, x, y, w, h);
		}
	}

	@Inject(method = "renderPlayerHealth", at = @At("TAIL"))
	private void cakeui$statusElements(GuiGraphics context, CallbackInfo ci) {
		HudPart.drawLateElements(context, "health", "armor", "hunger", "air");
	}

	// ---- action bar (ordinal 0) and title (ordinal 1; subtitle 2): drawn in place, after vanilla's own translate ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
	private void cakeui$actionBarPlace(PoseStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("actionBar", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1))
	private void cakeui$titlePlace(PoseStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("title", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;drawBackdrop(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;III)V"))
	private void cakeui$textBackground(Gui self, GuiGraphics context, Font tr, int yOffset, int width, int color, Operation<Void> original) {
		HudPart.Port p = cakeui$textPort(yOffset);
		if (p == null) {
			original.call(self, context, tr, yOffset, width, color);
		} else if (!p.hide) {
			original.call(self, context, tr, yOffset, width, cakeui$fade(color, p.opacity));
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
	private int cakeui$text(GuiGraphics context, Font tr, Component text, int x, int y, int color, Operation<Integer> original) {
		HudPart.Port p = cakeui$textPort(y);
		if (p == null) {
			return original.call(context, tr, text, x, y, color);
		}
		return p.hide ? 0 : original.call(context, tr, text, x, y, cakeui$fade(color, p.opacity));
	}

	/** Vanilla draws the action bar at y -4, the title at -10 and the subtitle at 5 (in their own matrices). */
	private static HudPart.Port cakeui$textPort(int y) {
		if (!HudPart.active()) {
			return null;
		}
		return y == -4 ? HudPart.port("actionBar") : HudPart.port("title");
	}

	private static int cakeui$fade(int color, float opacity) {
		if (opacity == 1) {
			return color;
		}
		int a = (color >>> 24) == 0 ? 255 : color >>> 24;
		return (Math.round(a * opacity) << 24) | (color & 0xFFFFFF);
	}
}
