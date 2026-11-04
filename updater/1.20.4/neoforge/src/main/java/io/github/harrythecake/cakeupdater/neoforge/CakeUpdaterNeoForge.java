package io.github.harrythecake.cakeupdater.neoforge;

import io.github.harrythecake.cakeupdater.BackupsScreen;
import io.github.harrythecake.cakeupdater.CakeUpdater;
import io.github.harrythecake.cakeupdater.CakeUpdaterPreLaunch;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * NeoForge entry: NeoForge's events call the shared {@link CakeUpdater}, as Fabric API's do on Fabric.
 * Pre-launch: {@link EarlyStart} (mixin config plugin), and again here in case it didn't run. Client started: client
 * setup. Stopping: GameShuttingDownEvent (when the game is asked to stop, a moment before Fabric's CLIENT_STOPPING;
 * the world is still loaded in both). Mods list → Config opens the backups.
 */
@Mod(CakeUpdater.MOD_ID)
public final class CakeUpdaterNeoForge {
	public CakeUpdaterNeoForge(IEventBus modBus) {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		CakeUpdaterPreLaunch.run(FMLPaths.GAMEDIR.get());
		CakeUpdater.init();
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new BackupsScreen(parent)));
		modBus.addListener(FMLClientSetupEvent.class, event -> CakeUpdater.onClientStarted(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
				event -> CakeUpdater.onScreenInit(Minecraft.getInstance(), event.getScreen()));
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
				event -> CakeUpdater.onJoin(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
				event -> event.getDispatcher().register(CakeUpdater.<CommandSourceStack>command()));
		NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class,
				event -> CakeUpdater.onStopping(Minecraft.getInstance()));
	}

	/** /cakeupdate and its subcommands (without the slash). */
	public static boolean isOurCommand(String command) {
		return command.equals("cakeupdate") || command.startsWith("cakeupdate ");
	}
}
