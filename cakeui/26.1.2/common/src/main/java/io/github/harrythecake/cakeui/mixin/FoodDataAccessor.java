package io.github.harrythecake.cakeui.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.1: FoodData no longer has a getter for exhaustion (the player.exhaustion game value). */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
	@Accessor("exhaustionLevel")
	float cakeui$exhaustion();
}
