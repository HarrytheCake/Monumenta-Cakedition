package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Name tags over entities (NPC names, armor-stand holograms). Player names are never touched. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
	private Text caketext$label(Text text, @Local(argsOnly = true) Entity entity) {
		if (entity instanceof PlayerEntity) {
			return text;
		}
		return TranslatorMod.translate(text, Surfaces.NAMEPLATE, entity.getPos());
	}
}
