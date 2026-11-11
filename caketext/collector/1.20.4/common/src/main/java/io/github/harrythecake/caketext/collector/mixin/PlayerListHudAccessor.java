package io.github.harrythecake.caketext.collector.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tab list header and footer (player names in the list itself are never collected). */
@Mixin(PlayerTabOverlay.class)
public interface PlayerListHudAccessor {
	@Accessor("header")
	Component caketext$header();

	@Accessor("footer")
	Component caketext$footer();
}
