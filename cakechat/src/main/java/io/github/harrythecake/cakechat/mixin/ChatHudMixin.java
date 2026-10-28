package io.github.harrythecake.cakechat.mixin;

import io.github.harrythecake.cakechat.CakeChat;
import io.github.harrythecake.cakechat.TabRules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
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
 * drawing, hover, clicks and scrolling go to CakeChat's windows. Priority 500 (before the default 1000), so the HEAD
 * hook sees a line before CakeText Translator's {@code @ModifyVariable} at HEAD changes it.
 */
@Mixin(value = ChatHud.class, priority = 500)
public abstract class ChatHudMixin {
	@Shadow
	@Final
	private List<ChatHudLine> messages;

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), cancellable = true)
	private void cakechat$arriving(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
		// the server's reply to a channel switch CakeChat made: never stored, never shown
		if (signature == null && CakeChat.windows().hideServerLine(TabRules.routingText(message.getString()))) {
			ci.cancel();
			return;
		}
		CakeChat.store().arriving(message);
	}

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
		CakeChat.windows().invalidate();
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void cakechat$render(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
		CakeChat.windows().render(context, currentTick, mouseX, mouseY);
		ci.cancel();
	}

	@Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
	private void cakechat$scroll(int scroll, CallbackInfo ci) {
		CakeChat.windows().scroll(scroll);
		ci.cancel();
	}

	@Inject(method = "resetScroll", at = @At("HEAD"), cancellable = true)
	private void cakechat$resetScroll(CallbackInfo ci) {
		CakeChat.windows().resetScroll();
		ci.cancel();
	}

	/** The delayed-message bar is CakeChat's (under the main window's tab bar); ChatScreenMixin handles its click. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cakechat$mouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}

	/** Page Up/Down in the chat screen scroll by this. */
	@Inject(method = "getVisibleLineCount", at = @At("HEAD"), cancellable = true)
	private void cakechat$visibleLineCount(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(CakeChat.windows().visibleLineCount());
	}

	@Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$styleAt(double x, double y, CallbackInfoReturnable<Style> cir) {
		cir.setReturnValue(CakeChat.windows().styleAt(x, y));
	}

	@Inject(method = "getIndicatorAt", at = @At("HEAD"), cancellable = true)
	private void cakechat$indicatorAt(double mouseX, double mouseY, CallbackInfoReturnable<MessageIndicator> cir) {
		cir.setReturnValue(CakeChat.windows().indicatorAt(mouseX, mouseY));
	}
}
