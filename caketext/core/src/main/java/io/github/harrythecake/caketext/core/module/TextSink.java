package io.github.harrythecake.caketext.core.module;

import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Receives text a module saw. Call on the client thread. */
@FunctionalInterface
public interface TextSink {
	/**
	 * @param surface where it was shown: one of {@link io.github.harrythecake.caketext.core.context.Surfaces}
	 *                or the module's own id, e.g. {@code umm_hud}
	 * @param pos     where the text is in the world, or null for HUD/screen text (the player's position is used)
	 */
	void accept(String surface, Text text, @Nullable Vec3d pos);
}
