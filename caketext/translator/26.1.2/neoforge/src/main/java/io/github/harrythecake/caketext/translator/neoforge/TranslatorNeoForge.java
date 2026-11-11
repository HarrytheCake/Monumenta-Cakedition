package io.github.harrythecake.caketext.translator.neoforge;

import io.github.harrythecake.caketext.translator.TranslatorCommands;
import io.github.harrythecake.caketext.translator.TranslatorMod;
import io.github.harrythecake.caketext.translator.ui.TranslatorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** NeoForge entry: NeoForge events → {@link TranslatorMod}; the settings screen under Mods → Config. */
@Mod(value = TranslatorMod.MOD_ID, dist = Dist.CLIENT)
public final class TranslatorNeoForge {
	public TranslatorNeoForge(ModContainer container) {
		TranslatorMod.init();
		container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new TranslatorScreen(parent));
		// chat lines are handled where they enter the chat box (ChatHudMixin); the action bar is here.
		// LOW: after listeners that only read the message (the collector reads it at HIGHEST)
		NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, ClientChatReceivedEvent.System.class,
				e -> e.setMessage(TranslatorMod.onGameMessage(e.getMessage(), e.isOverlay())));
		// LOWEST: tooltips are translated after every other listener
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ItemTooltipEvent.class,
				e -> TranslatorMod.onTooltip(e.getItemStack(), e.getToolTip()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> TranslatorMod.onScreenInit(e.getScreen()));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> TranslatorMod.onTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> TranslatorMod.onJoin(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> TranslatorMod.onDisconnect());
		NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> TranslatorMod.onDisconnect());
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e ->
				TranslatorCommands.register(e.getDispatcher(), TranslatorNeoForge::feedback));
	}

	private static void feedback(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}
}
