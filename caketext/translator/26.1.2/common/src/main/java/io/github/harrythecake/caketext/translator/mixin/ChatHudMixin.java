package io.github.harrythecake.caketext.translator.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every line that enters the chat box (server messages, player chat, signed or not) passes here once.
 * Rebuilding the visible lines ({@code refresh}) uses a different overload, so lines aren't translated twice.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true)
	private Component caketext$chat(Component message, @Local(argsOnly = true) MessageSignature signature) {
		return TranslatorMod.chat(message, signature != null);
	}
}
