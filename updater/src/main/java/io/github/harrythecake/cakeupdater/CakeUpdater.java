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

import com.mojang.brigadier.arguments.StringArgumentType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
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

	private static Backups backups;
	/** The backup made at this start because the pack changed since the last one; announced once, on join. */
	private static volatile Backups.Backup updateBackup;
	private static boolean restoreHookAdded;

	@Override
	public void onInitializeClient() {
		config = Config.load();
		backups = new Backups(gameDir());
		if (config.backups.enabled) {
			try {
				updateBackup = backups.detectUpdate(UpdateCheck.installedIdentity(gameDir()),
						UpdateCheck.readLocalVersion(gameDir()), config.backups.keep);
			} catch (Exception e) {
				LOGGER.error("Could not compare the modpack with the last start", e);
			}
		}

		// Check on every join, so an update released mid-session still shows up.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (updateBackup != null) {
				Backups.Backup made = updateBackup;
				updateBackup = null;
				client.send(() -> announceUpdated(client, made));
			}
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
						.then(literal("backups").executes(ctx -> openBackups()))
						.then(literal("quit").executes(ctx -> quit(ctx.getSource())))
						.then(literal("restore")
								.then(literal("servers").then(argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreServers(ctx.getSource(), StringArgumentType.getString(ctx, "backup")))))
								.then(literal("configs").then(argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreConfigs(ctx.getSource(), StringArgumentType.getString(ctx, "backup")))))
								.then(literal("cancel").executes(ctx -> cancelRestore(ctx.getSource()))))
						.then(literal("resourcepacks").executes(ctx -> updateResourcePacks(ctx.getSource())))));

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// before the updater starts: this snapshot is what a backup holds if the pack changes before the next start
			if (config.backups.enabled) {
				try {
					backups.snapshot();
				} catch (Exception e) {
					LOGGER.error("Could not save the server list and configs", e);
				}
			}
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
		closeGame(source.getClient());
		return 1;
	}

	private static int quit(FabricClientCommandSource source) {
		closeGame(source.getClient());
		return 1;
	}

	/** Leaves the world cleanly and quits. */
	static void closeGame(MinecraftClient client) {
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
	}

	// --- Backups (server list and configs) ---

	private static void announceUpdated(MinecraftClient client, Backups.Backup made) {
		if (client.player == null) {
			return;
		}
		Text updated = made.from().equals(made.to())
				? Text.translatable("cakeupdater.updated.same", made.to())
				: Text.translatable("cakeupdater.updated", made.from(), made.to());
		client.player.sendMessage(prefix().append(updated.copy().formatted(Formatting.YELLOW)), false);
		MutableText buttons = Text.literal("  ");
		if (made.hasServers()) {
			buttons.append(button("cakeupdater.restore.servers", "/cakeupdate restore servers " + made.name(), Formatting.GREEN)).append(" ");
		}
		if (made.hasConfigs()) {
			buttons.append(button("cakeupdater.restore.configs", "/cakeupdate restore configs " + made.name(), Formatting.GREEN)).append(" ");
		}
		buttons.append(button("cakeupdater.backups.open", "/cakeupdate backups", Formatting.AQUA));
		client.player.sendMessage(buttons, false);
	}

	private static int openBackups() {
		MinecraftClient client = MinecraftClient.getInstance();
		// next tick: the chat screen closes after this command returns
		client.send(() -> client.setScreen(new BackupsScreen(null)));
		return 1;
	}

	private static int restoreServers(FabricClientCommandSource source, String name) {
		try {
			backups.restoreServers(name, config.backups.keep);
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.restore.servers.done", BackupsScreen.date(name))
					.formatted(Formatting.GREEN)));
		} catch (Exception e) {
			LOGGER.error("Server list restore failed", e);
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage()))
					.formatted(Formatting.RED)));
		}
		return 1;
	}

	private static int restoreConfigs(FabricClientCommandSource source, String name) {
		try {
			scheduleConfigRestore(name);
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.restore.configs.scheduled", BackupsScreen.date(name))
					.formatted(Formatting.YELLOW)));
			source.sendFeedback(Text.literal("  ")
					.append(button("cakeupdater.quit", "/cakeupdate quit", Formatting.RED))
					.append(" ")
					.append(button("cakeupdater.restore.cancel", "/cakeupdate restore cancel", Formatting.GRAY)));
		} catch (Exception e) {
			LOGGER.error("Config restore failed", e);
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage()))
					.formatted(Formatting.RED)));
		}
		return 1;
	}

	private static int cancelRestore(FabricClientCommandSource source) {
		try {
			backups.cancelConfigRestore();
		} catch (Exception e) {
			LOGGER.warn("Could not cancel the config restore: {}", e.toString());
		}
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.restore.cancelled").formatted(Formatting.GRAY)));
		return 1;
	}

	static Backups backups() {
		return backups;
	}

	static int keep() {
		return config.backups.keep;
	}

	/**
	 * Configs are copied back once the game has closed, after every mod has saved its own files (a mod would
	 * otherwise write its in-memory settings over the restored file). If the hook can't run, the next start does it.
	 */
	static void scheduleConfigRestore(String name) throws java.io.IOException {
		backups.scheduleConfigRestore(name);
		if (!restoreHookAdded) {
			restoreHookAdded = true;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					int restored = backups.applyPendingConfigRestore(config.backups.keep);
					if (restored >= 0) {
						LOGGER.info("Restored {} config files from a backup", restored);
					}
				} catch (Exception e) {
					LOGGER.error("Config restore failed; it is tried again at the next start", e);
				}
			}, "CakeUpdater restore"));
		}
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
