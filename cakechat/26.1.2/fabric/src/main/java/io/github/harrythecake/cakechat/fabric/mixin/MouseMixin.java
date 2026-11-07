package io.github.harrythecake.cakechat.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drags and releases in the open chat (moving/resizing windows, selecting a tab), taken where {@code MouseHandler} hands
 * them to the screen. {@code ChatScreen} doesn't override {@code mouseDragged}/{@code mouseReleased}, and adding them
 * there clashes with other mods that add the same methods: Mixin keeps only one. CakeChat only claims a drag it
 * started in {@code mouseClicked}, so everything else reaches the screen. 26.1 calls the screen directly (no lambdas).
 */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
	@WrapOperation(method = "handleAccumulatedMovement", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
	private boolean cakechat$mouseDragged(Screen screen, MouseButtonEvent event, double deltaX, double deltaY, Operation<Boolean> original) {
		if (screen instanceof ChatScreen && CakeChat.windows().mouseDragged(event.x(), event.y(), event.button())) {
			return true;
		}
		return original.call(screen, event, deltaX, deltaY);
	}

	@WrapOperation(method = "onButton", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
	private boolean cakechat$mouseReleased(Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
		if (screen instanceof ChatScreen && CakeChat.windows().mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return original.call(screen, event);
	}
}
