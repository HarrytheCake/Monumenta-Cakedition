package io.github.harrythecake.cakechat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakechat.CakeChat;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drags and releases in the open chat (moving/resizing windows, selecting a tab), taken where {@code Mouse} hands
 * them to the screen. {@code ChatScreen} doesn't override {@code mouseDragged}/{@code mouseReleased}, and adding them
 * there clashes with other mods that add the same methods (UMM's HUD editor, through njols-ui-framework): Mixin keeps
 * only one. CakeChat only claims a drag it started in {@code mouseClicked}, so everything else reaches the screen.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	/** The lambda in {@code onCursorPos} that calls {@code Screen.mouseDragged}. */
	@WrapOperation(method = "method_1602", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseDragged(DDIDD)Z"))
	private boolean cakechat$mouseDragged(Screen screen, double mouseX, double mouseY, int button, double deltaX, double deltaY,
			Operation<Boolean> original) {
		if (screen instanceof ChatScreen && CakeChat.windows().mouseDragged(mouseX, mouseY, button)) {
			return true;
		}
		return original.call(screen, mouseX, mouseY, button, deltaX, deltaY);
	}

	/** The lambda in {@code onMouseButton} that calls {@code Screen.mouseReleased}. */
	@WrapOperation(method = "method_1605", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screen/Screen;mouseReleased(DDI)Z"))
	private static boolean cakechat$mouseReleased(Screen screen, double mouseX, double mouseY, int button, Operation<Boolean> original) {
		if (screen instanceof ChatScreen && CakeChat.windows().mouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		return original.call(screen, mouseX, mouseY, button);
	}
}
