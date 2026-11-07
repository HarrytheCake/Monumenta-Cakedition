package io.github.harrythecake.cakechat.mixin;

import io.github.harrythecake.cakechat.CakeChat;
import io.github.harrythecake.cakechat.TabRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;

/**
 * Vanilla's chat box stays the way in (every line passes {@code addMessage}) and keeps its own 100 lines;
 * drawing, hover, clicks and scrolling go to CakeChat's windows. Priority 500 (before the default 1000), so the HEAD
 * hook sees a line before CakeText Translator's {@code @ModifyVariable} at HEAD changes it.
 */
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class ChatHudMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void cakechat$arriving(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
		// the server's reply to a channel switch CakeChat made: never stored, never shown
		if (signature == null && CakeChat.windows().hideServerLine(TabRules.routingText(message.getString()))) {
			ci.cancel();
			return;
		}
		CakeChat.store().arriving(message);
	}

	/** At TAIL, so the line is what other mods (CakeText Translator, at HEAD) made of it. */
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("TAIL"))
	private void cakechat$added(CallbackInfo ci) {
		CakeChat.store().onAdded(allMessages);
	}

	@Inject(method = "refreshTrimmedMessages", at = @At("TAIL"))
	private void cakechat$refreshed(CallbackInfo ci) {
		CakeChat.store().onRefresh(allMessages);
	}

	@Inject(method = "clearMessages", at = @At("HEAD"))
	private void cakechat$cleared(boolean clearHistory, CallbackInfo ci) {
		CakeChat.store().clear();
	}

	/** Chat settings changed (width, scale, spacing…): wrap again. */
	@Inject(method = "rescaleChat", at = @At("TAIL"))
	private void cakechat$reset(CallbackInfo ci) {
		CakeChat.windows().invalidate();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void cakechat$render(GuiGraphics context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
		CakeChat.windows().render(context, currentTick, mouseX, mouseY);
		ci.cancel();
	}

	@Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
	private void cakechat$scroll(int scroll, CallbackInfo ci) {
		CakeChat.windows().scroll(scroll);
		ci.cancel();
	}

	@Inject(method = "resetChatScroll", at = @At("HEAD"), cancellable = true)
	private void cakechat$resetScroll(CallbackInfo ci) {
		CakeChat.windows().resetScroll();
		ci.cancel();
	}

	/** The delayed-message bar is CakeChat's (under the main window's tab bar); ChatScreenMixin handles its click. */
	@Inject(method = "handleChatQueueClicked", at = @At("HEAD"), cancellable = true)
	private void cakechat$mouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	/** Page Up/Down in the chat screen scroll by this. */
	@Inject(method = "getLinesPerPage", at = @At("HEAD"), cancellable = true)
	private void cakechat$visibleLineCount(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(CakeChat.windows().visibleLineCount());
	}

	@Inject(method = "getClickedComponentStyleAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$styleAt(double x, double y, CallbackInfoReturnable<Style> cir) {
		cir.setReturnValue(CakeChat.windows().styleAt(x, y));
	}

	@Inject(method = "getMessageTagAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$indicatorAt(double mouseX, double mouseY, CallbackInfoReturnable<GuiMessageTag> cir) {
		cir.setReturnValue(CakeChat.windows().indicatorAt(mouseX, mouseY));
	}
}
