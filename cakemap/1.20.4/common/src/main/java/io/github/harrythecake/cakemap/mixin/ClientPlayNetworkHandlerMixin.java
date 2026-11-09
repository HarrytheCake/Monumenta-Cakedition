package io.github.harrythecake.cakemap.mixin;

import io.github.harrythecake.cakemap.DeathWaypoints;
import io.github.harrythecake.cakemap.QuestCompass;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The compass target (Monumenta's quest compass sets it) becomes a temporary waypoint; the death
 * packet adds a death waypoint. TAIL: by then the handler has moved to the render thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "handleSetSpawn", at = @At("TAIL"))
	private void cakemap$compass(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
		QuestCompass.target(packet.getPos());
	}

	@Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
	private void cakemap$death(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		DeathWaypoints.deathPacket(packet.getPlayerId());
	}
}
