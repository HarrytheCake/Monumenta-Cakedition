package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A screen's drawn widgets in the order it added them, for the buttons port (ScreenPorts.buttons). */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("renderables")
	List<Renderable> cakeui$renderables();
}
