package io.github.harrythecake.cakebench.neoforge;

import io.github.harrythecake.cakebench.CakeBench;
import io.github.harrythecake.cakebench.Play;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge client entry: the events {@link CakeBench} needs and {@code /cakebench} as a client command. */
@Mod(value = CakeBench.MOD_ID, dist = Dist.CLIENT)
public final class CakeBenchNeoForgeClient {
	public CakeBenchNeoForgeClient() {
		NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> Play.register(e.getDispatcher()));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> CakeBench.onDisconnect(Minecraft.getInstance()));
		CakeBench.init();
		NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> CakeBench.onScreenInit(Minecraft.getInstance(), e.getScreen()));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> CakeBench.onJoin());
	}
}
