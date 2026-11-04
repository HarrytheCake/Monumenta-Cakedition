package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** World loading progress, for {loading.progress} and {loading.kind} (26.1: one screen for world and terrain). */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
	@Accessor("loadTracker")
	LevelLoadTracker cakeui$tracker();
}
