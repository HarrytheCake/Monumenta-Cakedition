package io.github.harrythecake.cakeupdater;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CakeUpdater implements ClientModInitializer {
	public static final String MOD_ID = "cakeupdater";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Config config;
	private static volatile UpdateCheck.Result latest;
	private static String notifiedHash;
	private static boolean updateOnClose;

	private static volatile List<ResourcePackUpdater.Pending> pendingPacks = List.of();
	private static String notifiedPacks;
	private static boolean applyingPacks;

	@Override
	public void onInitializeClient() {
		config = Config.load();

		// Check on every join, so an update released mid-session still shows up.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.packwiz.enabled && config.packwiz.packUrl != null && !config.packwiz.packUrl.isBlank()) {
				UpdateCheck.runAsync(config.packwiz.packUrl, gameDir()).thenAccept(result -> {
					if (result != null) {
						latest = result;
						client.send(() -> announcePack(client, result));
					}
				});
			}
			List<String> enabledPacks = new ArrayList<>(client.getResourcePackManager().getEnabledNames());
			ResourcePackUpdater.check(config.resourcePacks, client.getResourcePackDir(), enabledPacks)
					.thenAccept(updates -> {
						pendingPacks = updates;
						client.send(() -> announcePacks(client, updates));
					});
		});

		// Client-side command: the click events below run it, and it never reaches the server.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				literal("cakeupdate")
						.executes(ctx -> showOptions(ctx.getSource()))
						.then(literal("onclose").executes(ctx -> scheduleOnClose(ctx.getSource())))
						.then(literal("now").executes(ctx -> updateNow(ctx.getSource())))
						.then(literal("cancel").executes(ctx -> cancel(ctx.getSource())))
						.then(literal("folder").executes(ctx -> openGameFolder()))
						.then(literal("resourcepacks").executes(ctx -> updateResourcePacks(ctx.getSource())))));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (updateOnClose) {
				try {
					HelperLauncher.launch(gameDir(), config.packwiz.packUrl, client.options.language, config.chatPrefix);
				} catch (Exception e) {
					LOGGER.error("Could not start the updater", e);
				}
			}
		});
	}

	// --- Modpack (packwiz) ---

	private static void announcePack(MinecraftClient client, UpdateCheck.Result result) {
		if (!result.available() || updateOnClose || result.remoteHash().equals(notifiedHash) || client.player == null) {
			return;
		}
		notifiedHash = result.remoteHash();
		Text available = result.localVersion() == null
				? Text.translatable("cakeupdater.pack.available", result.remoteVersion())
				: Text.translatable("cakeupdater.pack.available.versions", result.localVersion(), result.remoteVersion());
		client.player.sendMessage(prefix()
				.append(available.copy().formatted(Formatting.YELLOW))
				.append(" ")
				.append(button("cakeupdater.pack.click", "/cakeupdate", Formatting.GREEN)), false);
	}

	private static int showOptions(FabricClientCommandSource source) {
		UpdateCheck.Result result = latest;
		if (result == null || !result.available()) {
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.pack.uptodate").formatted(Formatting.GRAY)));
			return 1;
		}
		if (bootstrapMissing(source)) {
			return 1;
		}
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.pack.choose", result.remoteVersion())
				.formatted(Formatting.YELLOW)));
		source.sendFeedback(Text.literal("  ").append(button("cakeupdater.pack.onclose", "/cakeupdate onclose", Formatting.GREEN)));
		source.sendFeedback(Text.literal("  ").append(button("cakeupdater.pack.now", "/cakeupdate now", Formatting.RED)));
		return 1;
	}

	/**
	 * The mod never downloads the installer: it runs the packwiz-installer-bootstrap.jar a packwiz install already
	 * has in the game folder. Without it (e.g. a .mrpack install), say so and point to the folder and download page.
	 */
	private static boolean bootstrapMissing(FabricClientCommandSource source) {
		if (Files.exists(gameDir().resolve(Helper.BOOTSTRAP))) {
			return false;
		}
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.pack.no_installer", Helper.BOOTSTRAP)
				.formatted(Formatting.YELLOW)));
		source.sendFeedback(Text.literal("  ")
				.append(button("cakeupdater.pack.folder", "/cakeupdate folder", Formatting.GREEN))
				.append(" ")
				.append(link("cakeupdater.pack.download_page", Helper.BOOTSTRAP_PAGE)));
		return true;
	}

	private static int openGameFolder() {
		Util.getOperatingSystem().open(gameDir().toFile());
		return 1;
	}

	private static int scheduleOnClose(FabricClientCommandSource source) {
		if (bootstrapMissing(source)) {
			return 1;
		}
		updateOnClose = true;
		source.sendFeedback(prefix()
				.append(Text.translatable("cakeupdater.pack.scheduled").formatted(Formatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.cancel", "/cakeupdate cancel", Formatting.GRAY)));
		return 1;
	}

	private static int cancel(FabricClientCommandSource source) {
		updateOnClose = false;
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.pack.cancelled").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int updateNow(FabricClientCommandSource source) {
		if (bootstrapMissing(source)) {
			return 1;
		}
		updateOnClose = true;
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.pack.closing").formatted(Formatting.YELLOW)));
		MinecraftClient client = source.getClient();
		// Queue it: disconnecting from inside the command handler would tear down the connection mid-packet.
		client.send(() -> {
			if (client.world != null) {
				boolean singleplayer = client.isInSingleplayer();
				client.world.disconnect();
				if (singleplayer) {
					client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
				} else {
					client.disconnect();
				}
			}
			client.scheduleStop();
		});
		return 1;
	}

	// --- Resource packs (Modrinth) ---

	private static void announcePacks(MinecraftClient client, List<ResourcePackUpdater.Pending> updates) {
		String key = updates.toString();
		if (updates.isEmpty() || key.equals(notifiedPacks) || client.player == null) {
			return;
		}
		notifiedPacks = key;
		for (ResourcePackUpdater.Pending update : updates) {
			client.player.sendMessage(prefix()
					.append(Text.translatable("cakeupdater.resourcepack.available",
							update.title(), update.fromVersion(), update.toVersion()).formatted(Formatting.YELLOW))
					.append(" ")
					.append(button("cakeupdater.resourcepack.click", "/cakeupdate resourcepacks", Formatting.GREEN)), false);
		}
	}

	private static int updateResourcePacks(FabricClientCommandSource source) {
		List<ResourcePackUpdater.Pending> updates = pendingPacks;
		if (updates.isEmpty()) {
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.resourcepack.none").formatted(Formatting.GRAY)));
			return 1;
		}
		if (applyingPacks) {
			return 1;
		}
		applyingPacks = true;
		MinecraftClient client = source.getClient();
		for (ResourcePackUpdater.Pending update : updates) {
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.resourcepack.downloading",
					update.title(), update.toVersion()).formatted(Formatting.GRAY)));
		}
		ResourcePackUpdater.apply(client, updates).whenComplete((v, error) -> client.send(() -> {
			applyingPacks = false;
			if (client.player == null) {
				return;
			}
			if (error != null) {
				LOGGER.error("Resource pack update failed", error);
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.resourcepack.failed",
						String.valueOf(cause.getMessage())).formatted(Formatting.RED)), false);
				return;
			}
			pendingPacks = List.of();
			for (ResourcePackUpdater.Pending update : updates) {
				client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.resourcepack.done",
						update.title(), update.toVersion()).formatted(Formatting.GREEN)), false);
			}
		}));
		return 1;
	}

	// --- Text helpers ---

	private static MutableText prefix() {
		return Text.literal("[" + config.chatPrefix + "] ").formatted(Formatting.GOLD);
	}

	/** A clickable label; its tooltip is the translation key + ".hover". */
	private static MutableText button(String key, String command, Formatting color) {
		return Text.translatable(key).styled(style -> style
				.withColor(color)
				.withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(key + ".hover"))));
	}

	/** A link that opens a web page (Minecraft asks the player to confirm first). */
	private static MutableText link(String key, String url) {
		return Text.translatable(key).styled(style -> style
				.withColor(Formatting.AQUA)
				.withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(url))));
	}

	static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}
}
