package io.github.harrythecake.cakeui.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A slot's place in its screen's panel, which a container layout may move on the client (`slots` port). x and y are
 * final in vanilla; {@code @Mutable} lifts that. The server never sees slot places (clicks name the slot's index),
 * and the client's hover, clicks, dragging and JEI all read these same fields, so they follow the move.
 */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void cakeui$setX(int x);

	@Mutable
	@Accessor("y")
	void cakeui$setY(int y);
}
