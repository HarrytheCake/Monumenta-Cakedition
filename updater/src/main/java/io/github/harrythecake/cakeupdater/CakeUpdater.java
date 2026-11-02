package io.github.harrythecake.cakeupdater;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CakeUpdater implements ClientModInitializer {
	public static final String MOD_ID = "cakeupdater";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Changelog items shown in chat after an update; the rest are behind [Changelog]. */
	private static final int CHAT_ITEMS = 5;

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
	/** The update this start announced; /cakeupdate changelog shows its versions. */
	private static Backups.Backup announcedUpdate;
	private static boolean restoreHookAdded;

	/** An urgent update found at start, shown as a toast on the title screen (once per start). */
	private static volatile UpdateCheck.Result urgentAtStart;
	private static boolean urgentToastShown;
	private static boolean memoryChecked;

	/** The guide line (GuideNotice) is checked on the first join of each start. */
	private static boolean guideChecked;
	/** The import offer shows on the first join of each start (see MoveIn). */
	private static boolean importOffered;
	/** The folder window is open (it runs on its own thread). */
	private static volatile boolean picking;
	/** What the last picked folder has, waiting for [Import these]. */
	private record Scan(Path old, List<MoveIn.Found> found, List<ServerInfo> newServers) {}
	private static Scan scanned;

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

		// An urgent update is also shown before joining: a toast on the title screen.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (packwizEnabled()) {
				UpdateCheck.runAsync(config.packwiz, gameDir()).thenAccept(result -> {
					if (result != null && result.urgent()) {
						urgentAtStart = result;
						client.send(() -> {
							if (client.currentScreen instanceof TitleScreen) {
								showUrgentToast(client);
							}
						});
					}
				});
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				showUrgentToast(client);
			}
		});

		// Check on every join, so an update released mid-session still shows up.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (updateBackup != null) {
				Backups.Backup made = updateBackup;
				updateBackup = null;
				client.send(() -> announceUpdated(client, made));
			}
			if (!memoryChecked) {
				memoryChecked = true;
				client.send(() -> checkMemory(client));
			}
			if (!guideChecked) {
				guideChecked = true;
				client.send(() -> announceGuide(client));
			}
			if (!importOffered) {
				importOffered = true;
				client.send(() -> announceImport(client));
			}
			if (packwizEnabled()) {
				UpdateCheck.runAsync(config.packwiz, gameDir()).thenAccept(result -> {
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
						.then(literal("changelog").executes(ctx -> openChangelog()))
						.then(literal("quit").executes(ctx -> quit(ctx.getSource())))
						.then(literal("restore")
								.then(literal("servers").then(argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreServers(ctx.getSource(), StringArgumentType.getString(ctx, "backup")))))
								.then(literal("configs").then(argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreConfigs(ctx.getSource(), StringArgumentType.getString(ctx, "backup")))))
								.then(literal("cancel").executes(ctx -> cancelRestore(ctx.getSource()))))
						.then(literal("resourcepacks").executes(ctx -> updateResourcePacks(ctx.getSource())))
						.then(literal("import").executes(ctx -> pickFolder(ctx.getSource()))
								.then(literal("confirm").executes(ctx -> confirmImport(ctx.getSource())))
								.then(literal("cancel").executes(ctx -> cancelImport(ctx.getSource())))
								.then(literal("off").executes(ctx -> importOff(ctx.getSource()))))));

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
					Adoption.adopt(gameDir());
				} catch (Exception e) {
					LOGGER.error("Could not prepare the pack file install for its first update", e);
				}
				try {
					HelperLauncher.launch(gameDir(), latest != null ? latest.mrpack() : null, packUrlsFirst(latest),
							client.options.language, config.chatPrefix);
				} catch (Exception e) {
					LOGGER.error("Could not start the updater", e);
				}
			}
		});
	}

	// --- Modpack (packwiz) ---

	/** The pack.toml addresses, the one that answered the last check first (it worked a moment ago). */
	private static List<String> packUrlsFirst(UpdateCheck.Result result) {
		List<String> urls = new ArrayList<>(config.packwiz.packUrls());
		if (result != null && result.packUrl() != null && urls.remove(result.packUrl())) {
			urls.add(0, result.packUrl());
		}
		return urls;
	}

	private static boolean packwizEnabled() {
		return config.packwiz.enabled && config.packwiz.packUrl != null && !config.packwiz.packUrl.isBlank();
	}

	private static void announcePack(MinecraftClient client, UpdateCheck.Result result) {
		if (!result.available() || updateOnClose || client.player == null) {
			return;
		}
		if (result.urgent()) {
			// repeated on every join until the player updates
			announceUrgent(client, result);
			return;
		}
		if (result.remoteHash().equals(notifiedHash)) {
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

	/** The installed version was withdrawn: a red notice with the reason. Never forced; the same two choices follow. */
	private static void announceUrgent(MinecraftClient client, UpdateCheck.Result result) {
		client.player.sendMessage(prefix()
				.append(Text.translatable("cakeupdater.pack.urgent", result.localVersion(), result.remoteVersion())
						.formatted(Formatting.RED, Formatting.BOLD))
				.append(" ")
				.append(button("cakeupdater.pack.click", "/cakeupdate", Formatting.GREEN)), false);
		String reason = Withdrawn.pick(result.withdrawn(), client.options.language);
		if (!reason.isEmpty()) {
			client.player.sendMessage(Text.literal("  " + reason).formatted(Formatting.RED), false);
		}
	}

	private static void showUrgentToast(MinecraftClient client) {
		UpdateCheck.Result result = urgentAtStart;
		if (result == null || urgentToastShown) {
			return;
		}
		urgentToastShown = true;
		client.getToastManager().add(SystemToast.create(client, SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("cakeupdater.pack.urgent.toast.title").formatted(Formatting.RED),
				Text.translatable("cakeupdater.pack.urgent.toast", result.localVersion())));
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
		announcedUpdate = made;
		List<Changelog.Entry> changes = Changelog.between(gameDir(), made.from(), made.to(), client.options.language);
		List<String> items = changes.stream().flatMap(e -> e.items().stream()).toList();
		for (String item : items.subList(0, Math.min(CHAT_ITEMS, items.size()))) {
			client.player.sendMessage(Text.literal("  • " + item).formatted(Formatting.WHITE), false);
		}
		if (items.size() > CHAT_ITEMS) {
			client.player.sendMessage(Text.literal("  ")
					.append(Text.translatable("cakeupdater.changelog.more", items.size() - CHAT_ITEMS).formatted(Formatting.GRAY)), false);
		}
		MutableText buttons = Text.literal("  ");
		if (!changes.isEmpty()) {
			buttons.append(button("cakeupdater.changelog.open", "/cakeupdate changelog", Formatting.AQUA)).append(" ");
		}
		if (made.hasServers()) {
			buttons.append(button("cakeupdater.restore.servers", "/cakeupdate restore servers " + made.name(), Formatting.GREEN)).append(" ");
		}
		if (made.hasFiles()) {
			buttons.append(button("cakeupdater.restore.configs", "/cakeupdate restore configs " + made.name(), Formatting.GREEN)).append(" ");
		}
		buttons.append(button("cakeupdater.backups.open", "/cakeupdate backups", Formatting.AQUA));
		client.player.sendMessage(buttons, false);
	}

	/** The versions the update announced this start brought, else every changelog the pack ships. */
	private static int openChangelog() {
		MinecraftClient client = MinecraftClient.getInstance();
		Backups.Backup made = announcedUpdate;
		List<Changelog.Entry> entries = made != null
				? Changelog.between(gameDir(), made.from(), made.to(), client.options.language)
				: Changelog.between(gameDir(), null, null, client.options.language);
		client.send(() -> client.setScreen(new ChangelogScreen(null, entries)));
		return 1;
	}

	// --- Memory ---

	private static void checkMemory(MinecraftClient client) {
		Config.MemorySettings memory = config.memory;
		if (!memory.enabled || client.player == null) {
			return;
		}
		long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
		MemoryCheck.Problem problem = MemoryCheck.warnOnce(gameDir(), maxMb, memory.minGb, memory.maxGb);
		if (problem == null) {
			return;
		}
		Text warning = problem == MemoryCheck.Problem.LOW
				? Text.translatable("cakeupdater.memory.low", MemoryCheck.gb(maxMb), memory.minGb)
				: Text.translatable("cakeupdater.memory.high", MemoryCheck.gb(maxMb), memory.maxGb);
		MutableText message = prefix().append(warning.copy().formatted(Formatting.YELLOW));
		if (memory.guideUrl != null && !memory.guideUrl.isBlank()) {
			message.append(" ").append(link("cakeupdater.memory.guide", memory.guideUrl));
		}
		client.player.sendMessage(message, false);
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
		addCloseHook();
	}

	/** One shutdown hook for a pending config restore and a staged import (see MoveIn), in that order. */
	private static void addCloseHook() {
		if (restoreHookAdded) {
			return;
		}
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
			try {
				int imported = MoveIn.apply(gameDir(), backups, config.backups.keep);
				if (imported >= 0) {
					LOGGER.info("Imported {} files from another install", imported);
				}
			} catch (Exception e) {
				LOGGER.error("Import failed; it is tried again at the next start", e);
			}
		}, "CakeUpdater restore"));
	}

	// --- Pack guide (GuideNotice) ---

	/** First launch, or the first join after a pack update: a line with a button opening the guide's own-mods chapter. */
	private static void announceGuide(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		GuideNotice.Kind kind = GuideNotice.check(gameDir(), UpdateCheck.readLocalVersion(gameDir()),
				FabricLoader.getInstance().isModLoaded("cakebook"));
		if (kind == null) {
			return;
		}
		Text text = Text.translatable(kind == GuideNotice.Kind.WELCOME ? "cakeupdater.guide.welcome" : "cakeupdater.guide.updated");
		client.player.sendMessage(prefix().append(text.copy().formatted(Formatting.YELLOW)).append(" ")
				.append(button("cakeupdater.guide.open", "/cakebook open " + GuideNotice.PAGE, Formatting.AQUA)), false);
	}

	// --- Import from another install (MoveIn) ---

	/** First join of each start: the result of a finished import, else the offer (until turned off). */
	private static void announceImport(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		MoveIn.Done done = MoveIn.takeDone(gameDir());
		if (done != null) {
			client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.done", done.files(),
					MoveIn.label(Path.of(done.source())), BackupsScreen.date(done.backup())).formatted(Formatting.GREEN))
					.append(" ").append(button("cakeupdater.backups.open", "/cakeupdate backups", Formatting.AQUA)), false);
			return;
		}
		if (MoveIn.offerOff(gameDir()) || MoveIn.pending(gameDir()) != null) {
			return;
		}
		client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.offer").formatted(Formatting.YELLOW)), false);
		client.player.sendMessage(Text.literal("  ")
				.append(button("cakeupdater.import.offer.pick", "/cakeupdate import", Formatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.import.offer.off", "/cakeupdate import off", Formatting.GRAY)), false);
	}

	/** Opens the system's folder window on its own thread (it blocks until the player chooses). */
	private static int pickFolder(FabricClientCommandSource source) {
		if (picking) {
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.picking.busy").formatted(Formatting.GRAY)));
			return 1;
		}
		picking = true;
		MinecraftClient client = source.getClient();
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.picking").formatted(Formatting.YELLOW)));
		String title = Text.translatable("cakeupdater.import.picker.title").getString();
		// a launcher's instances folder, where the old install most likely is
		Path start = gameDir().toAbsolutePath().getParent();
		if (start != null && start.getParent() != null) {
			start = start.getParent();
		}
		String startPath = start == null ? "" : start.toString() + java.io.File.separator;
		Thread thread = new Thread(() -> {
			String chosen = null;
			try {
				chosen = TinyFileDialogs.tinyfd_selectFolderDialog(title, startPath);
			} catch (Throwable t) {
				LOGGER.error("Could not open the folder window", t);
			}
			String result = chosen;
			client.send(() -> {
				picking = false;
				picked(client, result);
			});
		}, "CakeUpdater import folder");
		thread.setDaemon(true);
		thread.start();
		return 1;
	}

	private static void picked(MinecraftClient client, String chosen) {
		if (client.player == null) {
			return;
		}
		if (chosen == null || chosen.isBlank()) {
			client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.nofolder").formatted(Formatting.GRAY)), false);
			return;
		}
		Path old = MoveIn.gameFolder(Path.of(chosen));
		if (MoveIn.sameFolder(old, gameDir())) {
			client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.self").formatted(Formatting.RED)), false);
			return;
		}
		List<MoveIn.Found> found = new ArrayList<>(MoveIn.scan(old, FabricLoader.getInstance().isModLoaded("cakechat")));
		List<ServerInfo> newServers = List.of();
		if (found.removeIf(f -> f.kind() == MoveIn.Kind.SERVERS)) {
			newServers = newServers(client, old.resolve(MoveIn.SERVERS));
		}
		String label = MoveIn.label(old);
		if (found.isEmpty() && newServers.isEmpty()) {
			client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.nothing", label).formatted(Formatting.YELLOW)), false);
			return;
		}
		scanned = new Scan(old, found, newServers);
		client.player.sendMessage(prefix().append(Text.translatable("cakeupdater.import.found", label).formatted(Formatting.YELLOW)), false);
		for (MoveIn.Found f : found) {
			Text item = switch (f.kind()) {
				case XAERO -> Text.translatable("cakeupdater.import.item.xaero", f.path(), f.files(),
						String.format(Locale.ROOT, "%.1f", f.bytes() / 1048576.0));
				default -> Text.translatable("cakeupdater.import.item." + f.kind().name().toLowerCase(Locale.ROOT));
			};
			client.player.sendMessage(Text.literal("  • ").append(item).formatted(Formatting.WHITE), false);
		}
		if (!newServers.isEmpty()) {
			client.player.sendMessage(Text.literal("  • ").append(Text.translatable("cakeupdater.import.item.servers", newServers.size()))
					.formatted(Formatting.WHITE), false);
		}
		client.player.sendMessage(Text.literal("  ")
				.append(button("cakeupdater.import.confirm", "/cakeupdate import confirm", Formatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.import.cancel", "/cakeupdate import cancel", Formatting.GRAY)), false);
	}

	/** The old server list's entries this one doesn't have (by address, ignoring case). */
	private static List<ServerInfo> newServers(MinecraftClient client, Path oldServers) {
		List<ServerInfo> added = new ArrayList<>();
		try {
			NbtCompound root = NbtIo.read(oldServers);
			if (root == null) {
				return added;
			}
			ServerList ours = new ServerList(client);
			ours.loadFile();
			List<String> known = new ArrayList<>();
			for (int i = 0; i < ours.size(); i++) {
				known.add(ours.get(i).address.toLowerCase(Locale.ROOT));
			}
			NbtList servers = root.getList("servers", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < servers.size(); i++) {
				ServerInfo info = ServerInfo.fromNbt(servers.getCompound(i));
				String address = info.address == null ? "" : info.address.toLowerCase(Locale.ROOT);
				if (!address.isBlank() && !known.contains(address) && ours.get(info.address) == null) {
					known.add(address);
					added.add(info);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read the old server list {}: {}", oldServers, e.toString());
		}
		return added;
	}

	/** Adds the new servers now, stages the rest for the close and turns the offer off. */
	private static int confirmImport(FabricClientCommandSource source) {
		Scan scan = scanned;
		scanned = null;
		MinecraftClient client = source.getClient();
		if (scan == null) {
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.choose_first").formatted(Formatting.GRAY))
					.append(" ").append(button("cakeupdater.import.offer.pick", "/cakeupdate import", Formatting.GREEN)));
			return 1;
		}
		try {
			String label = MoveIn.label(scan.old());
			String backup = backups.newImportBackup(label);
			if (!scan.newServers().isEmpty()) {
				backups.saveBeforeReplace(backup, MoveIn.SERVERS);
				ServerList ours = new ServerList(client);
				ours.loadFile();
				for (ServerInfo info : scan.newServers()) {
					ours.add(info, false);
				}
				ours.saveFile();
				source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.servers.added", scan.newServers().size())
						.formatted(Formatting.GREEN)));
			}
			List<String> copy = new ArrayList<>();
			com.google.gson.JsonObject cakechat = null;
			Map<String, String> keys = new LinkedHashMap<>();
			for (MoveIn.Found f : scan.found()) {
				if (f.kind() == MoveIn.Kind.CHATPLUS) {
					com.google.gson.JsonObject setup = ChatPlusImport.read(scan.old().resolve(f.path())).orElse(null);
					if (setup != null) {
						ChatPlusImport.Result r = ChatPlusImport.convert(setup, client.getWindow().getScaledHeight());
						cakechat = r.patch();
						if (r.peekKey() != null) {
							keys.put(ChatPlusImport.PEEK_OPTION, r.peekKey());
						}
						if (r.hideKey() != null) {
							keys.put(ChatPlusImport.HIDE_OPTION, r.hideKey());
						}
						LOGGER.info("ChatPlus for CakeChat: {} window(s), {} tab(s); formatted patterns: {}; skipped: {}",
								r.windows(), r.tabs(), r.formattedTabs(), r.skippedTabs());
					}
				} else {
					copy.add(f.path());
				}
			}
			MoveIn.turnOfferOff(gameDir());
			if (copy.isEmpty() && cakechat == null) {
				return 1;
			}
			MoveIn.stage(gameDir(), new MoveIn.Pending(scan.old().toAbsolutePath().normalize().toString(), backup, copy, cakechat, keys));
			addCloseHook();
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.scheduled").formatted(Formatting.YELLOW)));
			source.sendFeedback(Text.literal("  ")
					.append(button("cakeupdater.import.quit", "/cakeupdate quit", Formatting.RED))
					.append(" ")
					.append(button("cakeupdater.import.cancel", "/cakeupdate import cancel", Formatting.GRAY)));
		} catch (Exception e) {
			LOGGER.error("Import failed", e);
			source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.failed", String.valueOf(e.getMessage()))
					.formatted(Formatting.RED)));
		}
		return 1;
	}

	private static int cancelImport(FabricClientCommandSource source) {
		scanned = null;
		try {
			MoveIn.cancel(gameDir());
		} catch (Exception e) {
			LOGGER.warn("Could not cancel the import: {}", e.toString());
		}
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.cancelled").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int importOff(FabricClientCommandSource source) {
		try {
			MoveIn.turnOfferOff(gameDir());
		} catch (Exception e) {
			LOGGER.warn("Could not turn the import offer off: {}", e.toString());
		}
		source.sendFeedback(prefix().append(Text.translatable("cakeupdater.import.off").formatted(Formatting.GRAY)));
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
