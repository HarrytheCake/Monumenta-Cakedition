package io.github.harrythecake.cakechat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The open chat: mouse-wheel speed and direction, clicks/drags on tabs, tab bars and resize grips, and the selected
 * tab's prefix in front of a sent chat message, the find bar (Ctrl+F) and the copy menu (right-click a message).
 * Drags and releases are in {@link MouseMixin}.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected TextFieldWidget chatField;

	protected ChatScreenMixin(Text title) {
		super(title);
	}

	/** The find bar's widgets. The input line may now lose focus to the find field (vanilla locks it). */
	@Inject(method = "init", at = @At("TAIL"))
	private void cakechat$init(CallbackInfo ci) {
		chatField.setFocusUnlocked(true);
		CakeChat.windows().findBar().init(this, this::addDrawableChild);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void cakechat$removed(CallbackInfo ci) {
		CakeChat.windows().chatClosed();
	}

	/** Ctrl+F opens the find bar (closes it from its field); in its field, Enter goes back to the input line and Esc closes it. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void cakechat$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (keyCode == GLFW.GLFW_KEY_F && hasControlDown() && !hasShiftDown() && !hasAltDown()) {
			if (CakeChat.windows().findBar().fieldFocused(this)) {
				CakeChat.windows().findBar().close(this);
				setFocused(chatField);
			} else {
				CakeChat.windows().findBar().open(this);
			}
			cir.setReturnValue(true);
			return;
		}
		if (CakeChat.windows().copyMenu().isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
			CakeChat.windows().copyMenu().close();
			cir.setReturnValue(true);
			return;
		}
		if (!CakeChat.windows().findBar().fieldFocused(this)) {
			return;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> {
				CakeChat.windows().findBar().close(this);
				setFocused(chatField);
				cir.setReturnValue(true);
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				setFocused(chatField);
				cir.setReturnValue(true);
			}
			// the input line's sent-message history: not while typing in the find field
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> cir.setReturnValue(true);
			default -> {
			}
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void cakechat$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		CakeChat.windows().findBar().render(context);
		CakeChat.windows().copyMenu().render(context, mouseX, mouseY);
	}

	@ModifyConstant(method = "mouseScrolled", constant = @Constant(doubleValue = 7.0))
	private double cakechat$linesPerNotch(double vanilla) {
		return CakeChat.config().scroll.linesPerNotch;
	}

	@ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private double cakechat$invert(double verticalAmount) {
		return CakeChat.config().scroll.invert ? -verticalAmount : verticalAmount;
	}

	/** Before the command suggestions and the input line, like vanilla's own chat clicks. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cakechat$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (CakeChat.windows().mouseClicked(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}
		// a click on the input line takes the typing back from the find field
		if (mouseY >= height - 14 && CakeChat.windows().findBar().fieldFocused(this)) {
			setFocused(chatField);
		}
	}

	/** A command you typed: your own /join or /leave ends CakeChat's channel switch (see Channels). */
	@WrapOperation(method = "sendMessage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatCommand(Ljava/lang/String;)V"))
	private void cakechat$typedCommand(ClientPlayNetworkHandler handler, String command, Operation<Void> original) {
		CakeChat.windows().typedCommand(command);
		original.call(handler, command);
	}

	/**
	 * A chat message (not a command you typed) sent from a tab with a prefix: "/g " makes it the command
	 * {@code /g <message>}, any other prefix is put in front of the message. Your sent-message history keeps what you typed.
	 */
	@WrapOperation(method = "sendMessage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"))
	private void cakechat$prefix(ClientPlayNetworkHandler handler, String message, Operation<Void> original) {
		String prefix = CakeChat.windows().prefix();
		if (prefix.isEmpty()) {
			original.call(handler, message);
		} else if (prefix.startsWith("/")) {
			handler.sendChatCommand((prefix + message).substring(1));
		} else {
			original.call(handler, prefix + message);
		}
	}
}
