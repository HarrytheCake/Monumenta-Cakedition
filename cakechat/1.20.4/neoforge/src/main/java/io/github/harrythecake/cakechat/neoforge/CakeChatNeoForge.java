package io.github.harrythecake.cakechat.neoforge;

import io.github.harrythecake.cakechat.CakeChat;
import io.github.harrythecake.cakechat.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge entry: config, the peek and hide keys, ticks, joins, the {@code /cakechat} command and the config screen
 * (Mods list → Config). Drags and releases in the open chat come through NeoForge's screen events, which fire where
 * Fabric's {@code MouseMixin} wraps the screen call (before the screen gets them).
 */
@Mod(CakeChat.MOD_ID)
public final class CakeChatNeoForge {
	public CakeChatNeoForge(IEventBus modBus) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		CakeChat.init();
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> SettingsScreen.create(parent)));
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			event.register(CakeChat.PEEK);
			event.register(CakeChat.HIDE);
		});
		NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, event -> {
			if (event.phase == TickEvent.Phase.END) {
				CakeChat.clientTick(Minecraft.getInstance());
			}
		});
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> CakeChat.joined(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
				CakeChat.command(Commands::literal, (source, line) -> source.sendSuccess(() -> line, false))));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseDragged.Pre.class, event -> {
			if (event.getScreen() instanceof ChatScreen && CakeChat.windows().mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton())) {
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonReleased.Pre.class, event -> {
			if (event.getScreen() instanceof ChatScreen && CakeChat.windows().mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
				event.setCanceled(true);
			}
		});
	}
}
