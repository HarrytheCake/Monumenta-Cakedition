package io.github.harrythecake.cakeui.mixin;

import net.minecraft.server.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** World generation progress, for {loading.progress}. */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
	@Accessor("progressProvider")
	WorldGenerationProgressTracker cakeui$progress();
}
