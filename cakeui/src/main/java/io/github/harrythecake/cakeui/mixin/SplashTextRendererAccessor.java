package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.screen.SplashTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The text of vanilla's chosen splash, for the menu.splash value. */
@Mixin(SplashTextRenderer.class)
public interface SplashTextRendererAccessor {
	@Accessor("text")
	String cakeui$text();
}
