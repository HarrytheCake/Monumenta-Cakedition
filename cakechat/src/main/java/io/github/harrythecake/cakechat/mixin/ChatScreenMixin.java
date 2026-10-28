package io.github.harrythecake.cakechat.mixin;

import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mouse-wheel speed and direction ({@code scroll.linesPerNotch}, {@code scroll.invert}). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@ModifyConstant(method = "mouseScrolled", constant = @Constant(doubleValue = 7.0))
	private double cakechat$linesPerNotch(double vanilla) {
		return CakeChat.config().scroll.linesPerNotch;
	}

	@ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private double cakechat$invert(double verticalAmount) {
		return CakeChat.config().scroll.invert ? -verticalAmount : verticalAmount;
	}
}
