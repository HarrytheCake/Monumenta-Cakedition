package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Titles, subtitles and the scoreboard sidebar. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
	private Text caketext$title(Text title) {
		return TranslatorMod.translate(title, Surfaces.TITLE);
	}

	@ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
	private Text caketext$subtitle(Text subtitle) {
		return TranslatorMod.translate(subtitle, Surfaces.SUBTITLE);
	}

	@ModifyExpressionValue(method = "renderScoreboardSidebar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/scoreboard/ScoreboardObjective;getDisplayName()Lnet/minecraft/text/Text;"))
	private Text caketext$sidebarTitle(Text title) {
		return TranslatorMod.translate(title, Surfaces.SCOREBOARD);
	}

	/** {@code method_55439} is the lambda that builds each sidebar row (team prefix + name + suffix). */
	@ModifyExpressionValue(method = "method_55439", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/scoreboard/Team;decorateName(Lnet/minecraft/scoreboard/AbstractTeam;Lnet/minecraft/text/Text;)Lnet/minecraft/text/MutableText;"))
	private MutableText caketext$sidebarRow(MutableText row) {
		Text t = TranslatorMod.translate(row, Surfaces.SCOREBOARD);
		return t == row ? row : t.copy();
	}
}
