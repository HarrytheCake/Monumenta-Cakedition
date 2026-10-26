package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Text display entities (holograms). Their render state is built when the text changes, so a model translation that
 * arrives later shows the next time the hologram is loaded; dictionary translations show at once.
 */
@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class TextDisplayEntityMixin {
	@ModifyExpressionValue(method = "getLerpedRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;getText()Lnet/minecraft/text/Text;"))
	private Text caketext$text(Text text) {
		Entity self = (Entity) (Object) this;
		if (!self.getWorld().isClient) {
			return text;
		}
		return TranslatorMod.translate(text, Surfaces.NAMEPLATE, self.getPos());
	}
}
