package io.github.harrythecake.cakefixes.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.harrythecake.cakefixes.CakeFixes;
import io.github.harrythecake.cakefixes.TeamRemoval;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** teamRemovalFix: see {@link TeamRemoval}. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
	@WrapWithCondition(method = "onTeam", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/scoreboard/Scoreboard;removeScoreHolderFromTeam(Ljava/lang/String;Lnet/minecraft/scoreboard/Team;)V"))
	private boolean cakefixes$skipRemovalFromOtherTeam(Scoreboard scoreboard, String holder, Team team) {
		return TeamRemoval.shouldRemove(CakeFixes.config().teamRemovalFix,
				scoreboard.getScoreHolderTeam(holder), team, team.getName());
	}
}
