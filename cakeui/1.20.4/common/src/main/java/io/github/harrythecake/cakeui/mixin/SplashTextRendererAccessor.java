package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The text of vanilla's chosen splash, for the menu.splash value. */
@Mixin(SplashRenderer.class)
public interface SplashTextRendererAccessor {
	@Accessor("splash")
	String cakeui$text();
}
