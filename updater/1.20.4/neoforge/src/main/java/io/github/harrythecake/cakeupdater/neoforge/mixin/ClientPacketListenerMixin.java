package io.github.harrythecake.cakeupdater.neoforge.mixin;

import io.github.harrythecake.cakeupdater.neoforge.CakeUpdaterNeoForge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge 20.4 runs client commands typed in chat (sendCommand) but not those from chat click events
 * (sendUnsignedCommand): those went to the server. 21.1+ does both. Only /cakeupdate is caught here, so other mods'
 * click events behave as NeoForge made them.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
	private void cakeupdater$clientCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		if (CakeUpdaterNeoForge.isOurCommand(command) && ClientCommandHandler.runCommand(command)) {
			cir.setReturnValue(true);
		}
	}
}
