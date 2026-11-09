package io.github.harrythecake.cakemap.neoforge.mixin;

import io.github.harrythecake.cakemap.CakeMapCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge 20.4 runs client commands typed in chat (sendCommand) but not those from chat click events
 * (sendUnsignedCommand): those would go to the server. 21.1+ does both. Only /cakemap is caught here (the
 * chat [+] and the import offer), so other mods' click events behave as NeoForge made them.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
	private void cakemap$clientCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		if (CakeMapCommands.isOurs(command) && ClientCommandHandler.runCommand(command)) {
			cir.setReturnValue(true);
		}
	}
}
