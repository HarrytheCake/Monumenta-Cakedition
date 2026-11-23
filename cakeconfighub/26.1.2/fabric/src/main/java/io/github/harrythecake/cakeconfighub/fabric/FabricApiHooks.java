package io.github.harrythecake.cakeconfighub.fabric;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.harrythecake.cakeconfighub.CakeConfighubClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Everything that uses Fabric API; loaded only when its modules are installed. */
final class FabricApiHooks {
	static final String KEY_MODULE = "fabric-key-mapping-api-v1";

	private FabricApiHooks() {
	}

	static void tick() {
		ClientTickEvents.END_CLIENT_TICK.register(CakeConfighubClient::endClientTick);
	}

	static void key() {
		KeyMappingHelper.registerKeyMapping(CakeConfighubClient.createOpenKey(KeyMapping.Category.register(CakeConfighubClient.KEY_CATEGORY)));
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
