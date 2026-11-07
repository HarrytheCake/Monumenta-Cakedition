package io.github.harrythecake.cakechat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;

/** The tab list in the order the game shows it (for the guild channel, see GuildTag). */
@Mixin(PlayerTabOverlay.class)
public interface PlayerListHudAccessor {
	@Invoker("getPlayerInfos")
	List<PlayerInfo> cakechat$collectPlayerEntries();
}
