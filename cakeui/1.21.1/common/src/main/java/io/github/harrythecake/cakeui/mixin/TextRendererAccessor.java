package io.github.harrythecake.cakeui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;

/** The font lookup of Minecraft's text renderer, to find its default font storage. */
@Mixin(Font.class)
public interface TextRendererAccessor {
	@Accessor("fonts")
	Function<ResourceLocation, FontSet> cakeui$storage();
}
