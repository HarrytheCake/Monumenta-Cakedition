package io.github.harrythecake.cakechat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client entrypoint: loads the config and registers {@code /cakechat}. */
public final class CakeChat implements ClientModInitializer {
	public static final String MOD_ID = "cakechat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
	private static ChatConfig config = new ChatConfig();

	@Override
	public void onInitializeClient() {
		config = ChatConfig.load(CONFIG_FILE);
		if (!Files.exists(CONFIG_FILE)) {
			saveConfig();
		}

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal(MOD_ID).executes(context -> {
					MinecraftClient client = MinecraftClient.getInstance();
					// next tick: the chat screen closes after this command returns
					client.send(() -> client.setScreen(SettingsScreen.create(null)));
					return 1;
				})));
	}

	public static ChatConfig config() {
		return config;
	}

	public static void saveConfig() {
		try {
			config.save(CONFIG_FILE);
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}: {}", CONFIG_FILE, e.toString());
		}
	}
}
