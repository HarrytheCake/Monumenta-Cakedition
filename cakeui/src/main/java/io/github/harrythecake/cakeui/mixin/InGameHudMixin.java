package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.HudPart;
import io.github.harrythecake.cakeui.HudTiming;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	private static final String PUMPKIN = "textures/misc/pumpkinblur.png";

	@Shadow
	private int scaledWidth;
	@Shadow
	private int scaledHeight;

	// ---- frame ----

	@Inject(method = "render", at = @At("HEAD"))
	private void cakeui$begin(DrawContext context, float tickDelta, CallbackInfo ci) {
		HudTiming.start();
		HudPart.beginFrame(context);
	}

	/** The HUD's back layer: after the full-screen overlays, before the hotbar. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;getCurrentGameMode()Lnet/minecraft/world/GameMode;", ordinal = 0))
	private void cakeui$back(DrawContext context, float tickDelta, CallbackInfo ci) {
		HudPart.drawLayer(context, "back");
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakeui$front(DrawContext context, float tickDelta, CallbackInfo ci) {
		HudPart.drawLateElements(context, "actionBar", "title");
		HudPart.drawLayer(context, "front");
		HudTiming.end();
	}

	// ---- full-screen overlays ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderVignetteOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/Entity;)V"))
	private void cakeui$vignette(InGameHud self, DrawContext context, Entity entity, Operation<Void> original) {
		HudPart.piece("vignette", context, scaledWidth / 2.0, scaledHeight / 2.0, false, () -> original.call(self, context, entity));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderSpyglassOverlay(Lnet/minecraft/client/gui/DrawContext;F)V"))
	private void cakeui$spyglass(InGameHud self, DrawContext context, float scale, Operation<Void> original) {
		HudPart.piece("spyglass", context, scaledWidth / 2.0, scaledHeight / 2.0, false, () -> original.call(self, context, scale));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V"))
	private void cakeui$overlay(InGameHud self, DrawContext context, Identifier texture, float opacity, Operation<Void> original) {
		if (!texture.getPath().equals(PUMPKIN)) {
			original.call(self, context, texture, opacity); // powder snow stays vanilla
			return;
		}
		HudPart.piece("pumpkin", context, scaledWidth / 2.0, scaledHeight / 2.0, false, () -> original.call(self, context, texture, opacity));
	}

	// ---- pieces ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbar(FLnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$hotbar(InGameHud self, float tickDelta, DrawContext context, Operation<Void> original) {
		HudPart.piece("hotbar", context, scaledWidth / 2.0, scaledHeight - 11, false, () -> original.call(self, tickDelta, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderCrosshair(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$crosshair(InGameHud self, DrawContext context, Operation<Void> original) {
		HudPart.piece("crosshair", context, scaledWidth / 2.0, scaledHeight / 2.0, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/BossBarHud;render(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$bossBars(BossBarHud bars, DrawContext context, Operation<Void> original) {
		HudPart.piece("bossBars", context, scaledWidth / 2.0, 12, false, () -> original.call(bars, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderMountHealth(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$mountHealth(InGameHud self, DrawContext context, Operation<Void> original) {
		HudPart.piece("mountHealth", context, scaledWidth / 2.0 + 91, scaledHeight - 39, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderExperienceBar(Lnet/minecraft/client/gui/DrawContext;I)V"))
	private void cakeui$experience(InGameHud self, DrawContext context, int x, Operation<Void> original) {
		HudPart.piece("experience", context, scaledWidth / 2.0, scaledHeight - 29, false, () -> original.call(self, context, x));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$heldItemName(InGameHud self, DrawContext context, Operation<Void> original) {
		HudPart.piece("heldItemName", context, scaledWidth / 2.0, scaledHeight - 59, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;)V"))
	private void cakeui$statusEffects(InGameHud self, DrawContext context, Operation<Void> original) {
		HudPart.piece("statusEffects", context, scaledWidth, 0, false, () -> original.call(self, context));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"))
	private void cakeui$scoreboard(InGameHud self, DrawContext context, ScoreboardObjective objective, Operation<Void> original) {
		HudPart.piece("scoreboard", context, scaledWidth, scaledHeight / 2.0, false, () -> original.call(self, context, objective));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"))
	private void cakeui$tabList(PlayerListHud list, DrawContext context, int width, Scoreboard scoreboard, ScoreboardObjective objective, Operation<Void> original) {
		HudPart.piece("tabList", context, scaledWidth / 2.0, 0, false, () -> original.call(list, context, width, scoreboard, objective));
	}

	// ---- status bars: health is one call, armor / hunger / air are sprites drawn in loops ----

	@WrapOperation(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderHealthBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V"))
	private void cakeui$health(InGameHud self, DrawContext context, PlayerEntity player, int x, int y, int lines, int regen, float max, int last, int health,
			int absorption, boolean blink, Operation<Void> original) {
		HudPart.piece("health", context, scaledWidth / 2.0 - 91, scaledHeight - 39, true,
				() -> original.call(self, context, player, x, y, lines, regen, max, last, health, absorption, blink));
	}

	@WrapOperation(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"))
	private void cakeui$statusSprite(DrawContext context, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!HudPart.active()) {
			original.call(context, sprite, x, y, w, h);
			return;
		}
		String path = sprite.getPath();
		if (path.startsWith("hud/armor")) {
			HudPart.piece("armor", context, scaledWidth / 2.0 - 91, scaledHeight - 49, true, () -> original.call(context, sprite, x, y, w, h));
		} else if (path.startsWith("hud/food")) {
			HudPart.piece("hunger", context, scaledWidth / 2.0 + 91, scaledHeight - 39, true, () -> original.call(context, sprite, x, y, w, h));
		} else if (path.startsWith("hud/air")) {
			HudPart.piece("air", context, scaledWidth / 2.0 + 91, scaledHeight - 49, true, () -> original.call(context, sprite, x, y, w, h));
		} else {
			original.call(context, sprite, x, y, w, h);
		}
	}

	@Inject(method = "renderStatusBars", at = @At("TAIL"))
	private void cakeui$statusElements(DrawContext context, CallbackInfo ci) {
		HudPart.drawLateElements(context, "health", "armor", "hunger", "air");
	}

	// ---- action bar (ordinal 0) and title (ordinal 1; subtitle 2): drawn in place, after vanilla's own translate ----

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0))
	private void cakeui$actionBarPlace(MatrixStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("actionBar", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 1))
	private void cakeui$titlePlace(MatrixStack matrices, float x, float y, float z, Operation<Void> original) {
		HudPart.translate("title", matrices, x, y, z, () -> original.call(matrices, x, y, z));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTextBackground(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;III)V"))
	private void cakeui$textBackground(InGameHud self, DrawContext context, TextRenderer tr, int yOffset, int width, int color, Operation<Void> original) {
		HudPart.Port p = cakeui$textPort(yOffset);
		if (p == null) {
			original.call(self, context, tr, yOffset, width, color);
		} else if (!p.hide) {
			original.call(self, context, tr, yOffset, width, cakeui$fade(color, p.opacity));
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"))
	private int cakeui$text(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, Operation<Integer> original) {
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
