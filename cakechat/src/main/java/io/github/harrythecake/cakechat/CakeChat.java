package io.github.harrythecake.cakechat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.language.I18n;
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
	private static ChatWindows windows;
	private static ChatStore store;

	@Override
	public void onInitializeClient() {
		config = ChatConfig.load(CONFIG_FILE);
		if (!Files.exists(CONFIG_FILE)) {
			saveConfig();
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerInfo entry = client.getCurrentServerEntry();
			windows().joined(entry == null ? "" : entry.address);
		});

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

	/** The chat windows; created with the store on first use, once the client exists. */
	public static ChatWindows windows() {
		init();
		return windows;
	}

	public static ChatStore store() {
		init();
		return store;
	}

	private static void init() {
		if (windows == null) {
			windows = new ChatWindows(MinecraftClient.getInstance());
			store = new ChatStore(windows);
		}
	}

	/** A tab's name as shown: the default tabs' names are translation keys. */
	public static String tabName(ChatConfig.Tab tab) {
		return tab.name.startsWith(ChatConfig.Tab.KEY_PREFIX) ? I18n.translate(tab.name) : tab.name;
	}

	/** The settings screen saved: windows, tabs and rules may have changed. */
	public static void saveConfig() {
		if (windows != null) {
			windows.rebuild();
		}
		saveConfigFile();
	}

	/** Only the file: a window moved or resized, a tab selected. */
	public static void saveConfigFile() {
		try {
			config.save(CONFIG_FILE);
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}: {}", CONFIG_FILE, e.toString());
		}
	}
}
