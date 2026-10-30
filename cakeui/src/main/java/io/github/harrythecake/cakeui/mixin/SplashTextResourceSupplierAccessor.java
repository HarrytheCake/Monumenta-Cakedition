package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Vanilla's splash list, mixed into a layout's own when the splash port says mixVanilla. */
@Mixin(SplashTextResourceSupplier.class)
public interface SplashTextResourceSupplierAccessor {
	@Accessor("splashTexts")
	List<String> cakeui$splashTexts();
}
