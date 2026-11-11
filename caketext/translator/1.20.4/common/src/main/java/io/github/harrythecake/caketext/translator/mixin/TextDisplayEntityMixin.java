package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Text display entities (holograms). Their render state is built when the text changes, so a model translation that
 * arrives later shows the next time the hologram is loaded; dictionary translations show at once.
 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayEntityMixin {
	@ModifyExpressionValue(method = "createInterpolatedTextRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Display$TextDisplay;getText()Lnet/minecraft/network/chat/Component;"))
	private Component caketext$text(Component text) {
		Entity self = (Entity) (Object) this;
		if (!self.level().isClientSide) {
			return text;
		}
		return TranslatorMod.translate(text, Surfaces.NAMEPLATE, self.position());
	}
}
