package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Boss bar names, at draw time (the bar's own name stays English, so the collector still sees the original). */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarHudMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
	private Component caketext$name(Component name) {
		return TranslatorMod.translate(name, Surfaces.BOSS_BAR);
	}
}
