package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The chunk map while a singleplayer world is prepared: the loading screen's `map` port. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;extractChunksForRendering(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
	private void cakeui$map(GuiGraphicsExtractor context, int centerX, int centerY, int size, int margin, ChunkLoadStatusView view, Operation<Void> original) {
		ScreenPorts p = ScreenPorts.LOADING;
		if (p.node() == null || p.port("map") == null) {
			original.call(context, centerX, centerY, size, margin, view);
			return;
		}
		p.vanillaPiece("map", context, centerX, centerY, () -> original.call(context, centerX, centerY, size, margin, view));
	}
}
