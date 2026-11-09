package io.github.harrythecake.cakemap.neoforge;

import io.github.harrythecake.cakemap.CakeMap;
import io.github.harrythecake.cakemap.CakeMapCommands;
import io.github.harrythecake.cakemap.ChatLinks;
import io.github.harrythecake.cakemap.InWorldWaypoints;
import io.github.harrythecake.cakemap.Scanner;
import io.github.harrythecake.cakemap.SettingsScreen;
import io.github.harrythecake.cakemap.StaticMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * NeoForge entry: CakeMap's hooks on NeoForge's events. The CakeUI plugin is listed in
 * META-INF/services (CakeUI's NeoForgePlugins loads it); settings open from the mod list's Config.
 */
@Mod(value = CakeMap.MOD_ID, dist = Dist.CLIENT)
public final class CakeMapNeoForge {
	public CakeMapNeoForge(IEventBus modBus, ModContainer container) {
		CakeMap.init();
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
		modBus.addListener(RegisterKeyMappingsEvent.class, e -> CakeMap.KEYS.values().forEach(e::register));
		modBus.addListener(RegisterClientReloadListenersEvent.class, e -> e.registerReloadListener(new StaticMaps()));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> CakeMap.endClientTick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
			if (e.getLevel().isClientSide()) {
				Scanner.queue(e.getChunk().getPos());
			}
		});
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Minecraft.getInstance().execute(CakeMap::endSession));
		NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, e -> CakeMap.endSession());
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
			if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
				InWorldWaypoints.capture(e.getModelViewMatrix(), e.getProjectionMatrix(), e.getCamera().getPosition());
			}
		});
		NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class, e -> e.setMessage(ChatLinks.modify(e.getMessage(), e.isOverlay())));
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
				CakeMapCommands.<CommandSourceStack>build((s, c) -> s.sendSuccess(() -> c, false), CommandSourceStack::sendFailure)));
	}
}
