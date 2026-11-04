package io.github.harrythecake.cakeui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;

/** Minecraft's fonts in a storage, borrowed as fallbacks for layout fonts. */
@Mixin(FontSet.class)
public interface FontStorageAccessor {
	@Accessor("providers")
	List<GlyphProvider> cakeui$fonts();
}
