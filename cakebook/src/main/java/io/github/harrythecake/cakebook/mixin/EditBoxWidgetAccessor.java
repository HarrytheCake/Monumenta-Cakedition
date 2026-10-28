package io.github.harrythecake.cakebook.mixin;

import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The note editor inserts links and locations at the cursor, which the widget itself doesn't offer. */
@Mixin(EditBoxWidget.class)
public interface EditBoxWidgetAccessor {
	@Accessor("editBox")
	EditBox cakebook$editBox();
}
