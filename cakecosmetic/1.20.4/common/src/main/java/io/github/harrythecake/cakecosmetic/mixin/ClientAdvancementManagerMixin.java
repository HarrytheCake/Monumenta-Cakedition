package io.github.harrythecake.cakecosmetic.mixin;

import io.github.harrythecake.cakecosmetic.CakeCosmeticHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;

/** After the game applies an advancement packet (render thread), CakeCosmetic checks its unlock rules. Read only. */
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementManagerMixin {
	@Shadow
	@Final
	private Map<AdvancementHolder, AdvancementProgress> progress;

	@Inject(method = "update", at = @At("TAIL"))
	private void cakecosmetic$afterAdvancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
		CakeCosmeticHooks.advancements(progress);
	}
}
