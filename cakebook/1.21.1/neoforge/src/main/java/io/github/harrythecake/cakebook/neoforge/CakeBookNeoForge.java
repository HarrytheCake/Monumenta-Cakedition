package io.github.harrythecake.cakebook.neoforge;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.harrythecake.cakebook.CakeBook;
import io.github.harrythecake.cakebook.CakeBookClient;
import io.github.harrythecake.cakebook.MenuButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entry: config and notes, the books' reload listener, the open key, the menu buttons, the
 * {@code /cakebook} command and the Mods list's "Config" button (opens the book).
 */
@Mod(value = CakeBook.MOD_ID, dist = Dist.CLIENT)
public final class CakeBookNeoForge {
	/** Screens with a book button that follows the inventory (recipe book open/closed). */
	private static final Map<Screen, Runnable> BEFORE_RENDER = new WeakHashMap<>();

	public CakeBookNeoForge(IEventBus modBus, ModContainer container) {
		CakeBookClient.init();
		container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> CakeBookClient.bookScreen(parent));
		modBus.addListener(RegisterClientReloadListenersEvent.class,
				event -> event.registerReloadListener(CakeBookClient.RELOAD_LISTENER));
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(CakeBookClient.OPEN_KEY));
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CakeBookClient.tick(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
			Screen screen = event.getScreen();
			BEFORE_RENDER.remove(screen);
			Runnable beforeRender = MenuButtons.afterInit(screen, event::addListener);
			if (beforeRender != null) {
				BEFORE_RENDER.put(screen, beforeRender);
			}
		});
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Pre.class, event -> {
			Runnable beforeRender = BEFORE_RENDER.get(event.getScreen());
			if (beforeRender != null) {
				beforeRender.run();
			}
		});
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
				CakeBookClient.command((source, line) -> source.sendFailure(line),
						(source, line) -> source.sendSuccess(() -> line, false))));
	}
}
