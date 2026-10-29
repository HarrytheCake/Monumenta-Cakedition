package io.github.harrythecake.cakechat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Client entrypoint: loads the config, registers {@code /cakechat} (settings) and {@code /cakechat import} (ChatPlus),
 * the peek key (Left Alt) and the hide key (unbound), and offers the ChatPlus import once after the first start.
 */
public final class CakeChat implements ClientModInitializer {
	public static final String MOD_ID = "cakechat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json");
	private static final Path CHATPLUS_DIR = FabricLoader.getInstance().getConfigDir().resolve("chatplus");
	private static KeyBinding peekKey;
	private static KeyBinding hideKey;
	private static ChatConfig config = new ChatConfig();
	private static ChatWindows windows;
	private static ChatStore store;

	@Override
	public void onInitializeClient() {
		config = ChatConfig.load(CONFIG_FILE);
		if (!Files.exists(CONFIG_FILE)) {
			// first start: no CakeChat setup yet, so a ChatPlus one is worth offering
			config.chatPlusOffer = chatPlusSetup().filter(ChatPlusImport::worthOffering).isPresent();
			saveConfig();
		}

		KeyBinding peek = peekKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakechat.peek", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.cakechat"));
		KeyBinding hide = hideKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cakechat.hide", InputUtil.UNKNOWN_KEY.getCode(), "key.categories.cakechat"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			windows().setPeeking(peek.isPressed() && client.currentScreen == null);
			while (hide.wasPressed()) {
				windows().toggleHidden(hide.getBoundKeyLocalizedText());
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerInfo entry = client.getCurrentServerEntry();
			windows().joined(entry == null ? "" : entry.address);
			if (config.chatPlusOffer) {
				config.chatPlusOffer = false;
				saveConfigFile();
				client.execute(() -> client.inGameHud.getChatHud().addMessage(Text.translatable("cakechat.import.offer",
						Text.translatable("cakechat.import.offer.button").styled(style -> style.withColor(Formatting.GREEN)
								.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + MOD_ID + " import"))
								.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("cakechat.import.offer.hover")))))
						.formatted(Formatting.GRAY)));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal(MOD_ID).executes(context -> {
					MinecraftClient client = MinecraftClient.getInstance();
					// next tick: the chat screen closes after this command returns
					client.send(() -> client.setScreen(SettingsScreen.create(null)));
					return 1;
				}).then(ClientCommandManager.literal("import").executes(context -> {
					importChatPlus();
					return 1;
				}))));
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

	/** The newest ChatPlus config, if there is one that can be read. */
	static Optional<JsonObject> chatPlusSetup() {
		return ChatPlusImport.newestFile(CHATPLUS_DIR).flatMap(ChatPlusImport::read);
	}

	/** Replaces CakeChat's windows and tabs (and the settings ChatPlus has) with the player's ChatPlus setup; says how it went. */
	static void importChatPlus() {
		MinecraftClient client = MinecraftClient.getInstance();
		Optional<JsonObject> setup = chatPlusSetup();
		if (setup.isEmpty()) {
			toast(client, Text.translatable("cakechat.import.none"), null);
			return;
		}
		ChatPlusImport.Result result = ChatPlusImport.apply(setup.get(), config, client.getWindow().getScaledHeight());
		config.chatPlusOffer = false;
		saveConfig();
		if (result.peekKey() != null) {
			peekKey.setBoundKey(InputUtil.fromTranslationKey(result.peekKey()));
		}
		if (result.hideKey() != null) {
			hideKey.setBoundKey(InputUtil.fromTranslationKey(result.hideKey()));
		}
		if (result.peekKey() != null || result.hideKey() != null) {
			KeyBinding.updateKeysByCode();
			client.options.write();
		}
		LOGGER.info("Imported from ChatPlus: {} window(s), {} tab(s); formatted patterns: {}; skipped: {}",
				result.windows(), result.tabs(), result.formattedTabs(), result.skippedTabs());
		toast(client, Text.translatable("cakechat.import.done"), Text.translatable("cakechat.import.done.count", result.windows(), result.tabs()));
		if (!result.formattedTabs().isEmpty()) {
			toast(client, Text.translatable("cakechat.import.formatted"), Text.literal(String.join(", ", result.formattedTabs())));
		}
		if (!result.skippedTabs().isEmpty()) {
			toast(client, Text.translatable("cakechat.import.skipped"), Text.literal(String.join(", ", result.skippedTabs())));
		}
	}

	private static void toast(MinecraftClient client, Text title, Text description) {
		SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
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
