package io.github.harrythecake.cakebench.neoforge;

import io.github.harrythecake.cakebench.BenchServer;
import io.github.harrythecake.cakebench.CakeBench;
import io.github.harrythecake.cakebench.Play;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge entry (client and dedicated server): {@code /cbbot} on the server; on the client the events
 * {@link CakeBench} needs and {@code /cakebench} as a client command.
 */
@Mod(CakeBench.MOD_ID)
public final class CakeBenchNeoForge {
	public CakeBenchNeoForge() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BenchServer.register(e.getDispatcher()));
		if (FMLEnvironment.dist == Dist.CLIENT) {
			Client.init();
		}
	}

	/** Only loaded on the client (it names client classes). */
	private static final class Client {
		static void init() {
			NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> Play.register(e.getDispatcher()));
			NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> CakeBench.onDisconnect(Minecraft.getInstance()));
			CakeBench.init();
			NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> CakeBench.onScreenInit(Minecraft.getInstance(), e.getScreen()));
			NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> CakeBench.onJoin());
		}
	}
}
