package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Name tags over entities (NPC names, armor-stand holograms). Player names are never touched. Since 1.21.2 the
 * name tag is read into the render state ({@code extractRenderState}), so it's translated there.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
	private Component caketext$label(Component text, @Local(argsOnly = true) Entity entity) {
		if (text == null || entity instanceof Player) {
			return text;
		}
		return TranslatorMod.translate(text, Surfaces.NAMEPLATE, entity.position());
	}
}
