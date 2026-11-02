package io.github.harrythecake.cakechat.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The tab list in the order the game shows it (for the guild channel, see GuildTag). */
@Mixin(PlayerListHud.class)
public interface PlayerListHudAccessor {
	@Invoker("collectPlayerEntries")
	List<PlayerListEntry> cakechat$collectPlayerEntries();
}
