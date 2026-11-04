package io.github.harrythecake.cakeui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.client.resources.SplashManager;

/** Vanilla's splash list, mixed into a layout's own when the splash port says mixVanilla. */
@Mixin(SplashManager.class)
public interface SplashTextResourceSupplierAccessor {
	@Accessor("splashes")
	List<net.minecraft.network.chat.Component> cakeui$splashTexts();
}
