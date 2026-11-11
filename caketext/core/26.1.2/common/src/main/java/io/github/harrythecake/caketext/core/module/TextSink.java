package io.github.harrythecake.caketext.core.module;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Receives text a module saw. Call on the client thread. */
@FunctionalInterface
public interface TextSink {
	/**
	 * @param surface where it was shown: one of {@link io.github.harrythecake.caketext.core.context.Surfaces}
	 *                or the module's own id, e.g. {@code umm_hud}
	 * @param pos     where the text is in the world, or null for HUD/screen text (the player's position is used)
	 */
	void accept(String surface, Component text, @Nullable Vec3 pos);
}
