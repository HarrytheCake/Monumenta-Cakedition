package io.github.harrythecake.caketext.translator.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Container titles (menus) come from the server; this swaps in the translation after the screen opens. */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Mutable
	@Accessor("title")
	void caketext$setTitle(Text title);
}
