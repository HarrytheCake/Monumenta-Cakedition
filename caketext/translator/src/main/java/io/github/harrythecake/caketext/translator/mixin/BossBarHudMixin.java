package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Boss bar names, at draw time (the bar's own name stays English, so the collector still sees the original). */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/ClientBossBar;getName()Lnet/minecraft/text/Text;"))
	private Text caketext$name(Text name) {
		return TranslatorMod.translate(name, Surfaces.BOSS_BAR);
	}
}
