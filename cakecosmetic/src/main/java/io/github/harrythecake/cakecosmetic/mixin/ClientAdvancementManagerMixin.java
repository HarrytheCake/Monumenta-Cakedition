package io.github.harrythecake.cakecosmetic.mixin;

import io.github.harrythecake.cakecosmetic.CakeCosmeticHooks;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** After the game applies an advancement packet (render thread), CakeCosmetic checks its unlock rules. Read only. */
@Mixin(ClientAdvancementManager.class)
public abstract class ClientAdvancementManagerMixin {
	@Shadow
	@Final
	private Map<AdvancementEntry, AdvancementProgress> advancementProgresses;

	@Inject(method = "onAdvancements", at = @At("TAIL"))
	private void cakecosmetic$afterAdvancements(AdvancementUpdateS2CPacket packet, CallbackInfo ci) {
		CakeCosmeticHooks.advancements(advancementProgresses);
	}
}
