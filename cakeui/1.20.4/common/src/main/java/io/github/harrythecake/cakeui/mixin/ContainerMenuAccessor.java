package io.github.harrythecake.cakeui.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A menu's synced numbers (its ContainerData and DataSlots, in the order it added them): `menu.data.<n>`. */
@Mixin(AbstractContainerMenu.class)
public interface ContainerMenuAccessor {
	@Accessor("dataSlots")
	List<DataSlot> cakeui$dataSlots();
}
