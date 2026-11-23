package io.github.harrythecake.cakeconfighub.fabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.harrythecake.cakeconfighub.CakeConfighubClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/** Everything that uses Fabric API; loaded only when its modules are installed. */
final class FabricApiHooks {
	static final String KEY_MODULE = "fabric-key-binding-api-v1";

	private FabricApiHooks() {
	}

	static void tick() {
		ClientTickEvents.END_CLIENT_TICK.register(CakeConfighubClient::endClientTick);
	}

	static void key() {
		KeyBindingHelper.registerKeyBinding(CakeConfighubClient.OPEN_KEY);
	}

	/** /cakeconfighub opens the hub, /cakeconfighub reload re-reads the maps. Client-side, never sent to the server. */
	static void command() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				LiteralArgumentBuilder.<FabricClientCommandSource>literal("cakeconfighub").executes(ctx -> {
					CakeConfighubClient.openNextTick();
					return 1;
				}).then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload").executes(ctx -> {
					ctx.getSource().sendFeedback(CakeConfighubClient.reloadMaps());
					return 1;
				}))));
	}
}
