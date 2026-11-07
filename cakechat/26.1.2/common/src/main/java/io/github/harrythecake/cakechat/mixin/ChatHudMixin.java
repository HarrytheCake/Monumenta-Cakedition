package io.github.harrythecake.cakechat.mixin;

import io.github.harrythecake.cakechat.CakeChat;
import io.github.harrythecake.cakechat.TabRules;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Vanilla's chat box stays the way in (every line passes the private {@code addMessage}, behind
 * {@code addClientSystemMessage}/{@code addServerSystemMessage}/{@code addPlayerMessage}) and keeps its own 100 lines;
 * drawing, hover, clicks and scrolling go to CakeChat's windows. Priority 500 (before the default 1000), so the HEAD
 * hook sees a line before other mods change it. 26.1: clicks find their text through {@code captureClickableText}
 * and hovers are shown while drawing ({@code extractRenderState}), so both go to the windows.
 */
@Mixin(value = ChatComponent.class, priority = 500)
public abstract class ChatHudMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), cancellable = true)
	private void cakechat$arriving(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag indicator, CallbackInfo ci) {
		// the server's reply to a channel switch CakeChat made: never stored, never shown
		if (signature == null && CakeChat.windows().hideServerLine(TabRules.routingText(message.getString()))) {
			ci.cancel();
			return;
		}
		CakeChat.store().arriving(message);
	}

	/** At TAIL, so the line is what other mods (at HEAD) made of it. */
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
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

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("HEAD"), cancellable = true)
	private void cakechat$render(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode mode,
			boolean changeCursorOnInsertions, CallbackInfo ci) {
		CakeChat.windows().render(graphics, ticks, mouseX, mouseY);
		ci.cancel();
	}

	/** The open chat looks for the clicked text: the windows' visible lines. */
	@Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
	private void cakechat$clickable(ActiveTextCollector collector, int screenHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
		CakeChat.windows().captureClickableText(collector);
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

	/** Page Up/Down in the chat screen scroll by this. */
	@Inject(method = "getLinesPerPage", at = @At("HEAD"), cancellable = true)
	private void cakechat$visibleLineCount(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(CakeChat.windows().visibleLineCount());
	}
}
