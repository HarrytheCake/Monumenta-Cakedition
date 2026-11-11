package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A container screen's panel box (leftPos, topPos, imageWidth, imageHeight) and the slot under the mouse. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int cakeui$left();

	@Accessor("leftPos")
	void cakeui$setLeft(int x);

	@Accessor("topPos")
	int cakeui$top();

	@Accessor("topPos")
	void cakeui$setTop(int y);

	@Accessor("imageWidth")
	int cakeui$imageWidth();

	@Accessor("imageWidth")
	void cakeui$setImageWidth(int w);

	@Accessor("imageHeight")
	int cakeui$imageHeight();

	@Accessor("imageHeight")
	void cakeui$setImageHeight(int h);

	@Nullable
	@Accessor("hoveredSlot")
	Slot cakeui$hoveredSlot();
}
