package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Name tags over entities (NPC names, armor-stand holograms). Player names are never touched. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
	private Component caketext$label(Component text, @Local(argsOnly = true) Entity entity) {
		if (entity instanceof Player) {
			return text;
		}
		return TranslatorMod.translate(text, Surfaces.NAMEPLATE, entity.position());
	}
}
