package io.github.harrythecake.cakebook.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The note editor inserts links and locations at the cursor, which the widget itself doesn't offer. */
@Mixin(MultiLineEditBox.class)
public interface EditBoxWidgetAccessor {
	@Accessor("textField")
	MultilineTextField cakebook$editBox();
}
