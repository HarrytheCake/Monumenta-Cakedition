package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Titles, subtitles and the scoreboard sidebar. */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	@ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
	private Component caketext$title(Component title) {
		return TranslatorMod.translate(title, Surfaces.TITLE);
	}

	@ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
	private Component caketext$subtitle(Component subtitle) {
		return TranslatorMod.translate(subtitle, Surfaces.SUBTITLE);
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
	private Component caketext$sidebarTitle(Component title) {
		return TranslatorMod.translate(title, Surfaces.SCOREBOARD);
	}

	/**
	 * The lambda that builds each sidebar row (team prefix + name + suffix): {@code method_55439} on Fabric
	 * (intermediary; Mojang's mappings don't name lambdas), {@code lambda$displayScoreboardSidebar$2} on NeoForge.
	 */
	@ModifyExpressionValue(method = {"method_55439", "lambda$displayScoreboardSidebar$2"}, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent caketext$sidebarRow(MutableComponent row) {
		Component t = TranslatorMod.translate(row, Surfaces.SCOREBOARD);
		return t == row ? row : t.copy();
	}
}
