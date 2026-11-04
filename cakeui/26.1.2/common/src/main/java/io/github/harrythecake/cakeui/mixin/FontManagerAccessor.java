package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Minecraft's loaded font sets (26.1), to borrow the default one's providers as fallbacks. */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
	@Accessor("fontSets")
	Map<Identifier, FontSet> cakeui$fontSets();
}
