package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.LoadingPart;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The text lines of the loading screens that draw their own (the reconfiguring screen uses a text
 * widget, which the buttons port covers): the `text` port moves, scales, recolours or hides them,
 * and they are kept for {loading.text}.
 */
@Mixin({ConnectScreen.class, ReceivingLevelScreen.class, LevelLoadingScreen.class, ProgressScreen.class})
public abstract class LoadingScreensMixin extends Screen {
	protected LoadingScreensMixin(Component title) {
		super(title);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void cakeui$text(GuiGraphics context, Font textRenderer, Component text, int x, int y, int color, Operation<Void> original) {
		ScreenPorts p = ScreenPorts.LOADING;
		if (p.node() == null) {
			original.call(context, textRenderer, text, x, y, color);
			return;
		}
		LoadingPart.line(text.getString());
		if (p.port("text") == null) {
			original.call(context, textRenderer, text, x, y, color);
			return;
		}
		int shown = p.color("text", color);
		context.setColor(1, 1, 1, p.portOpacity("text"));
		p.vanillaPiece("text", context, x, y, () -> original.call(context, textRenderer, text, x, y, shown));
		context.setColor(1, 1, 1, 1);
	}
}
