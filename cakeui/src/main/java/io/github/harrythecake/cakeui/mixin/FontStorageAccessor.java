package io.github.harrythecake.cakeui.mixin;

import net.minecraft.client.font.Font;
import net.minecraft.client.font.FontStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Minecraft's fonts in a storage, borrowed as fallbacks for layout fonts. */
@Mixin(FontStorage.class)
public interface FontStorageAccessor {
	@Accessor("fonts")
	List<Font> cakeui$fonts();
}
