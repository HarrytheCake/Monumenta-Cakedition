package io.github.harrythecake.caketext.collector.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tab list header and footer (player names in the list itself are never collected). */
@Mixin(PlayerListHud.class)
public interface PlayerListHudAccessor {
	@Accessor("header")
	Text caketext$header();

	@Accessor("footer")
	Text caketext$footer();
}
