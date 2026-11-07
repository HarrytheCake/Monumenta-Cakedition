package io.github.harrythecake.cakechat.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaving a world or server clears the HUD ({@code InGameHud.clear}); with {@code history.keepOnDisconnect} the chat
 * (and the sent-message history) stays. F3+D calls {@code ChatHud.clear} directly, so it still clears.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	@WrapWithCondition(method = "onDisconnected", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;clearMessages(Z)V"))
	private boolean cakechat$keepChat(ChatComponent chat, boolean clearHistory) {
		return !CakeChat.config().history.keepOnDisconnect;
	}
}
