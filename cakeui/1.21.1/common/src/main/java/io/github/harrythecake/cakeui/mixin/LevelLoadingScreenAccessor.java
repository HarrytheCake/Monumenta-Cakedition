package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** World generation progress, for {loading.progress}. */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
	@Accessor("progressListener")
	StoringChunkProgressListener cakeui$progress();
}
