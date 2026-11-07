package io.github.harrythecake.cakechat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.harrythecake.cakechat.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The mod, shared by both loaders: loads the config, the {@code /cakechat} (settings) and {@code /cakechat tablist}
 * commands, the peek key (Left Alt) and the hide key (unbound). The loader parts register the keys and the command and
 * call {@link #clientTick} and {@link #joined}. A ChatPlus setup is imported by Cakedition Updater (1.7.0+), not here.
 */
public final class CakeChat {
	public static final String MOD_ID = "cakechat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The Controls category (26.1: a registered id, shown as {@code key.category.cakechat.main}). */
	public static final Identifier KEY_CATEGORY = Identifier.fromNamespaceAndPath(MOD_ID, "main");
	public static KeyMapping PEEK;
	public static KeyMapping HIDE;

	private static Path configFile;
	private static ChatConfig config = new ChatConfig();
	private static ChatWindows windows;
	private static ChatStore store;

	private CakeChat() {
	}

	/** Client start (Fabric's client entrypoint, NeoForge's mod constructor). */
	public static void init() {
		configFile = Services.PLATFORM.configDir().resolve(MOD_ID).resolve("config.json");
		config = ChatConfig.load(configFile);
		if (!Files.exists(configFile)) {
			saveConfig();
		}
	}

	/** The peek and hide keys in the category the loader registered (see {@link #KEY_CATEGORY}). */
	public static void createKeys(KeyMapping.Category category) {
		PEEK = new KeyMapping("key.cakechat.peek", GLFW.GLFW_KEY_LEFT_ALT, category);
		HIDE = new KeyMapping("key.cakechat.hide", InputConstants.UNKNOWN.getValue(), category);
	}

	/** End of every client tick. */
	public static void clientTick(Minecraft client) {
		CakeUIBridge.init();
		if (PEEK == null) {
			return;
		}
		windows().setPeeking(PEEK.isDown() && client.screen == null);
		while (HIDE.consumeClick()) {
			windows().toggleHidden(HIDE.getTranslatedKeyMessage());
		}
		if (SelfTest.ON) {
			SelfTest.tick(client);
		}
	}

	/** Joined a world or server. */
	public static void joined(Minecraft client) {
		ServerData entry = client.getCurrentServer();
		windows().joined(entry == null ? "" : entry.ip);
	}

	/**
	 * {@code /cakechat} (settings), {@code /cakechat edit} (window moving and resizing on/off) and
	 * {@code /cakechat tablist} as a client command, for either loader's command source:
	 * {@code literal} makes a literal node, {@code feedback} shows a line in the player's own chat.
	 */
	public static <S> LiteralArgumentBuilder<S> command(Function<String, LiteralArgumentBuilder<S>> literal, BiConsumer<S, Component> feedback) {
		return literal.apply(MOD_ID).executes(context -> {
			Minecraft client = Minecraft.getInstance();
			// next tick: the chat screen closes after this command returns
			client.schedule(() -> client.setScreen(SettingsScreen.create(null)));
			return 1;
		}).then(literal.apply("edit").executes(context -> {
			// client-side only: never sent to the server; the new state goes to the player's own chat
			boolean on = windows().toggleWindowEditing();
			feedback.accept(context.getSource(), Component.translatable(on ? "cakechat.edit.on" : "cakechat.edit.off")
					.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			return 1;
		})).then(literal.apply("tablist").executes(context -> {
			// what CakeChat reads for the {guild} channel, shown only here (never logged or sent)
			List<String> lines = windows().tabListLines();
			Minecraft client = Minecraft.getInstance();
			String guild = GuildTag.find(lines, client.getUser().getName());
			feedback.accept(context.getSource(), Component.translatable("cakechat.tablist.head", lines.size(),
					guild == null ? "-" : guild).withStyle(ChatFormatting.YELLOW));
			for (int i = 0; i < lines.size(); i++) {
				feedback.accept(context.getSource(), Component.literal(i + ": " + GuildTag.escape(lines.get(i))).withStyle(ChatFormatting.GRAY));
			}
			return 1;
		}));
	}

	public static ChatConfig config() {
		return config;
	}

	/** The chat windows; created with the store on first use, once the client exists. */
	public static ChatWindows windows() {
		createWindows();
		return windows;
	}

	public static ChatStore store() {
		createWindows();
		return store;
	}

	private static void createWindows() {
		if (windows == null) {
			windows = new ChatWindows(Minecraft.getInstance());
			store = new ChatStore(windows);
		}
	}

	/** Registries for Component JSON (item hovers need them): the world's, else the built-in ones. */
	public static HolderLookup.Provider registries() {
		Minecraft client = Minecraft.getInstance();
		return client.level != null ? client.level.registryAccess() : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
	}

	private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

	/** Minecraft's JSON text for a message (26.1 has no Component.Serializer: the codec with the registries). */
	public static String toJson(Component text) {
		return JSON.toJson(ComponentSerialization.CODEC.encodeStart(registries().createSerializationContext(JsonOps.INSTANCE), text).getOrThrow());
	}

	/** A tab's name as shown: the default tabs' names are translation keys. */
	public static String tabName(ChatConfig.Tab tab) {
		return tab.name.startsWith(ChatConfig.Tab.KEY_PREFIX) ? I18n.get(tab.name) : tab.name;
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
			config.save(configFile);
		} catch (IOException e) {
			LOGGER.warn("Couldn't save {}: {}", configFile, e.toString());
		}
	}
}
