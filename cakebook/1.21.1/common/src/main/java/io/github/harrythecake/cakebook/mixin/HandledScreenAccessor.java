package io.github.harrythecake.cakebook.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the inventory is drawn, so the book button can sit next to it (it moves when the recipe book opens). */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
	@Accessor("leftPos")
	int cakebook$x();

	@Accessor("topPos")
	int cakebook$y();

	@Accessor("imageWidth")
	int cakebook$backgroundWidth();
}
