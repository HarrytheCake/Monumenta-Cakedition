package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.screen.ProgressScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A progress screen's percentage, for {loading.progress}. */
@Mixin(ProgressScreen.class)
public interface ProgressScreenAccessor {
	@Accessor("progress")
	int cakeui$progress();
}
