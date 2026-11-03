package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.LoadingPart;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The text lines of the loading screens that draw their own (the reconfiguring screen uses a text
 * widget, which the buttons port covers): the `text` port moves, scales, recolours or hides them,
 * and they are kept for {loading.text}.
 */
@Mixin({ConnectScreen.class, DownloadingTerrainScreen.class, LevelLoadingScreen.class, ProgressScreen.class, MessageScreen.class})
public abstract class LoadingScreensMixin extends Screen {
	protected LoadingScreensMixin(Text title) {
		super(title);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private void cakeui$text(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color, Operation<Void> original) {
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
		context.setShaderColor(1, 1, 1, p.portOpacity("text"));
		p.vanillaPiece("text", context, x, y, () -> original.call(context, textRenderer, text, x, y, shown));
		context.setShaderColor(1, 1, 1, 1);
	}
}
