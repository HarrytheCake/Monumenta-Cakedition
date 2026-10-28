package io.github.harrythecake.cakebook.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the inventory is drawn, so the book button can sit next to it (it moves when the recipe book opens). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int cakebook$x();

	@Accessor("y")
	int cakebook$y();

	@Accessor("backgroundWidth")
	int cakebook$backgroundWidth();
}
