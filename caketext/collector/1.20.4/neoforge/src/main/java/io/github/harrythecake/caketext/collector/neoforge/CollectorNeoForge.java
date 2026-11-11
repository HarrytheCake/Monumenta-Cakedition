package io.github.harrythecake.caketext.collector.neoforge;

import io.github.harrythecake.caketext.collector.CollectorCommands;
import io.github.harrythecake.caketext.collector.CollectorMod;
import io.github.harrythecake.caketext.collector.ui.CollectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** NeoForge entry: NeoForge events → {@link CollectorMod}; the settings screen under Mods → Config. */
@Mod(CollectorMod.MOD_ID)
public final class CollectorNeoForge {
	public CollectorNeoForge() {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		CollectorMod.init();
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new CollectorScreen(parent)));
		// HIGHEST: before any mod changes the message, so this sees the server's original text
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ClientChatReceivedEvent.System.class,
				e -> CollectorMod.onGameMessage(e.getMessage(), e.isOverlay()));
		NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, e -> CollectorMod.onTooltip(e.getToolTip()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> CollectorMod.onScreenInit(e.getScreen()));
		NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, e -> {
			if (e.phase == TickEvent.Phase.END) {
				CollectorMod.onTick(Minecraft.getInstance());
			}
		});
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> CollectorMod.onJoin(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> CollectorMod.onDisconnect());
		NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> CollectorMod.onStopping());
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e ->
				CollectorCommands.register(e.getDispatcher(), CollectorNeoForge::feedback));
	}

	private static void feedback(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}
}
