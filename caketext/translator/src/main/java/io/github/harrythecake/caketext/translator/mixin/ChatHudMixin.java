package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every line that enters the chat box (server messages, player chat, signed or not) passes here once.
 * Rebuilding the visible lines ({@code refresh}) uses a different overload, so lines aren't translated twice.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), argsOnly = true)
	private Text caketext$chat(Text message, @Local(argsOnly = true) MessageSignatureData signature) {
		return TranslatorMod.chat(message, signature != null);
	}
}
