package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft's font manager (26.1), to find its default font set for layout fonts' fallbacks. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("fontManager")
	FontManager cakeui$fontManager();
}
