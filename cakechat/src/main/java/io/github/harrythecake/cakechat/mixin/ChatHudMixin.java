package io.github.harrythecake.cakechat.mixin;

import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Vanilla's chat box stays the way in (every line passes {@code addMessage}) and keeps its own 100 lines;
 * drawing, hover, clicks and scrolling go to CakeChat's {@code ChatView}.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@Shadow
	@Final
	private List<ChatHudLine> messages;

	/** At TAIL, so the line is what other mods (CakeText Translator, at HEAD) made of it. */
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("TAIL"))
	private void cakechat$added(CallbackInfo ci) {
		CakeChat.store().onAdded(messages);
	}

	@Inject(method = "refresh", at = @At("TAIL"))
	private void cakechat$refreshed(CallbackInfo ci) {
		CakeChat.store().onRefresh(messages);
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void cakechat$cleared(boolean clearHistory, CallbackInfo ci) {
		CakeChat.store().clear();
	}

	/** Chat settings changed (width, scale, spacing…): wrap again. */
	@Inject(method = "reset", at = @At("TAIL"))
	private void cakechat$reset(CallbackInfo ci) {
		CakeChat.view().invalidate();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void cakechat$render(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
		CakeChat.view().render(context, currentTick, mouseX, mouseY);
		ci.cancel();
	}

	@Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
	private void cakechat$scroll(int scroll, CallbackInfo ci) {
		CakeChat.view().scroll(scroll);
		ci.cancel();
	}

	@Inject(method = "resetScroll", at = @At("HEAD"), cancellable = true)
	private void cakechat$resetScroll(CallbackInfo ci) {
		CakeChat.view().resetScroll();
		ci.cancel();
	}

	@Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$styleAt(double x, double y, CallbackInfoReturnable<Style> cir) {
		cir.setReturnValue(CakeChat.view().styleAt(x, y));
	}

	@Inject(method = "getIndicatorAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$indicatorAt(double mouseX, double mouseY, CallbackInfoReturnable<MessageIndicator> cir) {
		cir.setReturnValue(CakeChat.view().indicatorAt(mouseX, mouseY));
	}
}
