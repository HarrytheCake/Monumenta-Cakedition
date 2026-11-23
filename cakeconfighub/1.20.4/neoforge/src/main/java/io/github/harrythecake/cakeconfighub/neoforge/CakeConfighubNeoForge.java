package io.github.harrythecake.cakeconfighub.neoforge;

import io.github.harrythecake.cakeconfighub.CakeConfighub;
import io.github.harrythecake.cakeconfighub.CakeConfighubClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge entry (client only): the mod list's Config button and the key open the hub. No /cakeconfighub here
 * (NeoForge 20.4 would need a client-command mixin) and no pack defaults yet (no early hook on NeoForge).
 */
@Mod(CakeConfighub.MOD_ID)
public final class CakeConfighubNeoForge {
	public CakeConfighubNeoForge(IEventBus modBus) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> CakeConfighubClient.hubScreen(parent)));
		modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(CakeConfighubClient.OPEN_KEY));
		NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, e -> {
			if (e.phase == TickEvent.Phase.END) {
				CakeConfighubClient.endClientTick(Minecraft.getInstance());
			}
		});
	}
}
