package io.github.harrythecake.cakefixes.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.TeamRemoval;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** teamRemovalFix: see {@link TeamRemoval}. */
@Mixin(ClientPacketListener.class)
abstract class ClientPlayNetworkHandlerMixin {
	@WrapWithCondition(method = "handleSetPlayerTeamPacket", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/Scoreboard;removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V"))
	private boolean cakefixes$skipRemovalFromOtherTeam(Scoreboard scoreboard, String holder, PlayerTeam team) {
		return TeamRemoval.shouldRemove(CakeFixes.config().teamRemovalFix,
				scoreboard.getPlayersTeam(holder), team, team.getName());
	}
}
