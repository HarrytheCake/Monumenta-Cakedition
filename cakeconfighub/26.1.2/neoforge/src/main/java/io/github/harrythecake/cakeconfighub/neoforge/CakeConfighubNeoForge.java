package io.github.harrythecake.cakeconfighub.neoforge;

import io.github.harrythecake.cakeconfighub.CakeConfighub;
import io.github.harrythecake.cakeconfighub.CakeConfighubClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry (client only): the mod list's Config button, the key and /cakeconfighub open the hub;
 * /cakeconfighub reload re-reads the maps. No pack defaults yet (no early hook on NeoForge).
 */
@Mod(value = CakeConfighub.MOD_ID, dist = Dist.CLIENT)
public final class CakeConfighubNeoForge {
	public CakeConfighubNeoForge(IEventBus modBus, ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> CakeConfighubClient.hubScreen(parent));
		KeyMapping.Category category = new KeyMapping.Category(CakeConfighubClient.KEY_CATEGORY);
		KeyMapping openKey = CakeConfighubClient.createOpenKey(category);
		modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
			e.registerCategory(category);
			e.register(openKey);
		});
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> CakeConfighubClient.endClientTick(Minecraft.getInstance()));
		// client-side commands (never sent to the server)
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
				Commands.literal("cakeconfighub").executes(ctx -> {
					CakeConfighubClient.openNextTick();
					return 1;
				}).then(Commands.literal("reload").executes(ctx -> {
					Component message = CakeConfighubClient.reloadMaps();
					ctx.getSource().sendSuccess(() -> message, false);
					return 1;
				}))));
	}
}
