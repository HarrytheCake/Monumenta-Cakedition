package io.github.harrythecake.cakemonu.fabric;

import io.github.harrythecake.cakemonu.AbilitySource;
import io.github.harrythecake.cakemonu.CakeMonu;
import io.github.harrythecake.cakemonu.ConfigReload;
import io.github.harrythecake.cakemonu.SettingsScreen;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Fabric client entrypoint: the icon places, the UMM bridge (when a supported UMM is installed), {@code /cakemonu}. */
public final class CakeMonuFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CakeMonu.load();
		if (UmmSupport.supported()) {
			CakeMonu.setSource(new UmmAbilities());
			HudRenderCallback.EVENT.register(UmmAbilities::drawPlaced);
		} else {
			CakeMonu.setSource(new Missing(UmmSupport.version()));
		}
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("cakemonu")
						.executes(ctx -> {
							// after the chat screen has closed
							Minecraft mc = Minecraft.getInstance();
							mc.tell(() -> mc.setScreen(new SettingsScreen(null)));
							return 1;
						})
						.then(ClientCommandManager.literal("reload").executes(ctx -> {
							ConfigReload.cakeconfighubReloadConfig();
							ctx.getSource().sendFeedback(Component.translatable("cakemonu.command.reloaded"));
							return 1;
						}))));
		if (SelfTest.ENABLED) {
			ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
		}
	}

	/** No UMM, or a UMM version CakeMonu wasn't checked against: nothing to place. */
	private record Missing(String ummVersion) implements AbilitySource {
		@Override
		public State state() {
			return ummVersion == null ? State.NO_UMM : State.UNSUPPORTED;
		}

		@Override
		public String version() {
			return ummVersion == null ? "" : ummVersion;
		}

		@Override
		public List<Ability> current() {
			return List.of();
		}

		@Override
		public boolean barEnabled() {
			return false;
		}

		@Override
		public int iconSize() {
			return 32;
		}

		@Override
		public int iconGap() {
			return 0;
		}

		@Override
		public boolean horizontal() {
			return true;
		}

		@Override
		public double[] barBox(int screenWidth, int screenHeight) {
			return null;
		}
	}
}
