package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The chunk map while a singleplayer world is prepared: the loading screen's `map` port. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"))
	private void cakeui$map(GuiGraphics context, StoringChunkProgressListener progress, int centerX, int centerY, int pixelSize, int pixelMargin,
			Operation<Void> original) {
		ScreenPorts p = ScreenPorts.LOADING;
		if (p.node() == null || p.port("map") == null) {
			original.call(context, progress, centerX, centerY, pixelSize, pixelMargin);
			return;
		}
		p.vanillaPiece("map", context, centerX, centerY, () -> original.call(context, progress, centerX, centerY, pixelSize, pixelMargin));
	}
}
