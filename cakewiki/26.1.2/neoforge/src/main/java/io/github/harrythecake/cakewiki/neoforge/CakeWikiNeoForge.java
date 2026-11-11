package io.github.harrythecake.cakewiki.neoforge;

import io.github.harrythecake.cakewiki.CakeWiki;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry: NeoForge's events call the shared {@link CakeWiki}, as Fabric API's do on Fabric. CakeWiki sends no
 * chat buttons, so how NeoForge runs client commands from chat clicks doesn't matter here.
 */
@Mod(value = CakeWiki.MOD_ID, dist = Dist.CLIENT)
public final class CakeWikiNeoForge {
	public CakeWikiNeoForge(IEventBus modBus) {
		CakeWiki.init();
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			KeyMapping.Category category = new KeyMapping.Category(CakeWiki.KEY_CATEGORY);
			event.registerCategory(category);
			event.register(CakeWiki.createKey(category));
		});
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CakeWiki.onClientTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
				event -> event.getDispatcher().register(CakeWiki.<CommandSourceStack>command()));
	}
}
