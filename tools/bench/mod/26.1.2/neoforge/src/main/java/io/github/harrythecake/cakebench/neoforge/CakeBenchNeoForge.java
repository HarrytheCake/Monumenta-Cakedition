package io.github.harrythecake.cakebench.neoforge;

import io.github.harrythecake.cakebench.BenchServer;
import io.github.harrythecake.cakebench.CakeBench;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** NeoForge entry (client and dedicated server): {@code /cbbot} on the server. The client part is {@link CakeBenchNeoForgeClient}. */
@Mod(CakeBench.MOD_ID)
public final class CakeBenchNeoForge {
	public CakeBenchNeoForge() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BenchServer.register(e.getDispatcher()));
	}
}
