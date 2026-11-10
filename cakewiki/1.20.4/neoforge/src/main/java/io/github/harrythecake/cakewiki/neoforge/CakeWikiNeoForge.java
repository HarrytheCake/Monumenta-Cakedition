package io.github.harrythecake.cakewiki.neoforge;

import io.github.harrythecake.cakewiki.CakeWiki;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge entry: NeoForge's events call the shared {@link CakeWiki}, as Fabric API's do on Fabric. CakeWiki sends no
 * chat buttons, so NeoForge 20.4 not running client commands from chat clicks doesn't matter here.
 */
@Mod(CakeWiki.MOD_ID)
public final class CakeWikiNeoForge {
	public CakeWikiNeoForge(IEventBus modBus) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		CakeWiki.init();
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(CakeWiki.openKey));
		NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) CakeWiki.onClientTick(Minecraft.getInstance());
		});
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
				event -> event.getDispatcher().register(CakeWiki.<CommandSourceStack>command()));
	}
}
