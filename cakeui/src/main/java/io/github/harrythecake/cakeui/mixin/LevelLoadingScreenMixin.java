package io.github.harrythecake.cakeui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.harrythecake.cakeui.ScreenPorts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The chunk map while a singleplayer world is prepared: the loading screen's `map` port. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/world/LevelLoadingScreen;drawChunkMap(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/server/WorldGenerationProgressTracker;IIII)V"))
	private void cakeui$map(DrawContext context, WorldGenerationProgressTracker progress, int centerX, int centerY, int pixelSize, int pixelMargin,
			Operation<Void> original) {
		ScreenPorts p = ScreenPorts.LOADING;
		if (p.node() == null || p.port("map") == null) {
			original.call(context, progress, centerX, centerY, pixelSize, pixelMargin);
			return;
		}
		p.vanillaPiece("map", context, centerX, centerY, () -> original.call(context, progress, centerX, centerY, pixelSize, pixelMargin));
	}
}
