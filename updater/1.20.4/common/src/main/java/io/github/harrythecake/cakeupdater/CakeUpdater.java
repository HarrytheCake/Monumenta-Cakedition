package io.github.harrythecake.cakeupdater;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.harrythecake.cakeupdater.platform.Services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * The client side of Cakedition Updater, shared by both loaders: the loader entry classes (fabric / neoforge) call
 * {@link #init()}, the event hooks below and {@link #command()}.
 */
public final class CakeUpdater {
	public static final String MOD_ID = CakeUpdaterMod.MOD_ID;
	public static final Logger LOGGER = CakeUpdaterMod.LOGGER;
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
	static volatile UpdateCheck.Result urgentAtStart;
	static boolean urgentToastShown;
	private static boolean memoryChecked;

	/** The guide line (GuideNotice) is checked on the first join of each start. */
	private static boolean guideChecked;
	/** The import offer shows on the first join of each start (see MoveIn). */
	private static boolean importOffered;
	/** The folder window is open (it runs on its own thread). */
	private static volatile boolean picking;
	/** What the last picked folder has, waiting for [Import these]. */
	private record Scan(Path old, List<MoveIn.Found> found, List<ServerData> newServers) {}
	private static Scan scanned;

	private CakeUpdater() {}

	/** Once, when the client mod starts (Fabric: onInitializeClient; NeoForge: the mod's constructor). */
	public static void init() {
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
	}

	/** The client has started (Fabric: CLIENT_STARTED). An urgent update is also shown before joining: a toast on the title screen. */
	public static void onClientStarted(Minecraft client) {
		if (packwizEnabled()) {
			UpdateCheck.runAsync(config.packwiz, gameDir()).thenAccept(result -> {
				if (result != null && result.urgent()) {
					urgentAtStart = result;
					client.tell(() -> {
						if (client.screen instanceof TitleScreen) {
							showUrgentToast(client);
						}
					});
				}
			});
		}
	}

	/** After a screen's init (Fabric: ScreenEvents.AFTER_INIT). */
	public static void onScreenInit(Minecraft client, Screen screen) {
		if (screen instanceof TitleScreen) {
			showUrgentToast(client);
		}
	}

	/**
	 * Joined a world or server (Fabric: ClientPlayConnectionEvents.JOIN). Checks on every join, so an update released
	 * mid-session still shows up.
	 */
	public static void onJoin(Minecraft client) {
		if (updateBackup != null) {
			Backups.Backup made = updateBackup;
			updateBackup = null;
			client.tell(() -> announceUpdated(client, made));
		}
		if (!memoryChecked) {
			memoryChecked = true;
			client.tell(() -> checkMemory(client));
		}
		if (!guideChecked) {
			guideChecked = true;
			client.tell(() -> announceGuide(client));
		}
		if (!importOffered) {
			importOffered = true;
			client.tell(() -> announceImport(client));
		}
		if (packwizEnabled()) {
			UpdateCheck.runAsync(config.packwiz, gameDir()).thenAccept(result -> {
				if (result != null) {
					latest = result;
					client.tell(() -> announcePack(client, result));
				}
			});
		}
		List<String> enabledPacks = new ArrayList<>(client.getResourcePackRepository().getSelectedIds());
		ResourcePackUpdater.check(config.resourcePacks, client.getResourcePackDirectory(), enabledPacks)
				.thenAccept(updates -> {
					pendingPacks = updates;
					client.tell(() -> announcePacks(client, updates));
				});
		if (SelfTest.ENABLED) {
			SelfTest.start(client);
		}
	}

	/**
	 * The client-side command /cakeupdate, for either loader's client command dispatcher. The click events below run
	 * it, and it never reaches the server. Feedback goes straight to the chat window, as Fabric's sendFeedback does.
	 */
	public static <S> LiteralArgumentBuilder<S> command() {
		return LiteralArgumentBuilder.<S>literal("cakeupdate")
				.executes(ctx -> showOptions())
				.then(LiteralArgumentBuilder.<S>literal("onclose").executes(ctx -> scheduleOnClose()))
				.then(LiteralArgumentBuilder.<S>literal("now").executes(ctx -> updateNow()))
				.then(LiteralArgumentBuilder.<S>literal("cancel").executes(ctx -> cancel()))
				.then(LiteralArgumentBuilder.<S>literal("folder").executes(ctx -> openGameFolder()))
				.then(LiteralArgumentBuilder.<S>literal("backups").executes(ctx -> openBackups()))
				.then(LiteralArgumentBuilder.<S>literal("changelog").executes(ctx -> openChangelog()))
				.then(LiteralArgumentBuilder.<S>literal("quit").executes(ctx -> quit()))
				.then(LiteralArgumentBuilder.<S>literal("restore")
						.then(LiteralArgumentBuilder.<S>literal("servers")
								.then(RequiredArgumentBuilder.<S, String>argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreServers(StringArgumentType.getString(ctx, "backup")))))
						.then(LiteralArgumentBuilder.<S>literal("configs")
								.then(RequiredArgumentBuilder.<S, String>argument("backup", StringArgumentType.word())
										.executes(ctx -> restoreConfigs(StringArgumentType.getString(ctx, "backup")))))
						.then(LiteralArgumentBuilder.<S>literal("cancel").executes(ctx -> cancelRestore())))
				.then(LiteralArgumentBuilder.<S>literal("resourcepacks").executes(ctx -> updateResourcePacks()))
				.then(LiteralArgumentBuilder.<S>literal("import").executes(ctx -> pickFolder())
						.then(LiteralArgumentBuilder.<S>literal("confirm").executes(ctx -> confirmImport()))
						.then(LiteralArgumentBuilder.<S>literal("cancel").executes(ctx -> cancelImport()))
						.then(LiteralArgumentBuilder.<S>literal("off").executes(ctx -> importOff())));
	}

	/** The client is closing (Fabric: CLIENT_STOPPING). */
	public static void onStopping(Minecraft client) {
		if (config == null) {
			return;
		}
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
						client.options.languageCode, config.chatPrefix);
			} catch (Exception e) {
				LOGGER.error("Could not start the updater", e);
			}
		}
	}

	/** A command's feedback: into the chat window (and the narrator), never to the server. */
	private static void feedback(Component message) {
		Minecraft client = Minecraft.getInstance();
		client.gui.getChat().addMessage(message);
		client.getNarrator().sayNow(message);
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

	/**
	 * The Cakedition pack (the default addresses) is Fabric 1.20.4 only: on any other loader or version the check is
	 * off unless a pack creator's config points elsewhere (see {@link CakeUpdaterMod#cakeditionTarget()}).
	 */
	private static boolean packwizEnabled() {
		return config.packwiz.enabled && config.packwiz.packUrl != null && !config.packwiz.packUrl.isBlank()
				&& (!config.packwiz.cakedition() || CakeUpdaterMod.cakeditionTarget());
	}

	static void announcePack(Minecraft client, UpdateCheck.Result result) {
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
		Component available = result.localVersion() == null
				? Component.translatable("cakeupdater.pack.available", result.remoteVersion())
				: Component.translatable("cakeupdater.pack.available.versions", result.localVersion(), result.remoteVersion());
		client.player.displayClientMessage(prefix()
				.append(available.copy().withStyle(ChatFormatting.YELLOW))
				.append(" ")
				.append(button("cakeupdater.pack.click", "/cakeupdate", ChatFormatting.GREEN)), false);
	}

	/** The installed version was withdrawn: a red notice with the reason. Never forced; the same two choices follow. */
	static void announceUrgent(Minecraft client, UpdateCheck.Result result) {
		client.player.displayClientMessage(prefix()
				.append(Component.translatable("cakeupdater.pack.urgent", result.localVersion(), result.remoteVersion())
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.append(" ")
				.append(button("cakeupdater.pack.click", "/cakeupdate", ChatFormatting.GREEN)), false);
		String reason = Withdrawn.pick(result.withdrawn(), client.options.languageCode);
		if (!reason.isEmpty()) {
			client.player.displayClientMessage(Component.literal("  " + reason).withStyle(ChatFormatting.RED), false);
		}
	}

	static void showUrgentToast(Minecraft client) {
		UpdateCheck.Result result = urgentAtStart;
		if (result == null || urgentToastShown) {
			return;
		}
		urgentToastShown = true;
		client.getToasts().addToast(SystemToast.multiline(client, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable("cakeupdater.pack.urgent.toast.title").withStyle(ChatFormatting.RED),
				Component.translatable("cakeupdater.pack.urgent.toast", result.localVersion())));
	}

	private static int showOptions() {
		UpdateCheck.Result result = latest;
		if (result == null || !result.available()) {
			feedback(prefix().append(Component.translatable("cakeupdater.pack.uptodate").withStyle(ChatFormatting.GRAY)));
			return 1;
		}
		if (bootstrapMissing()) {
			return 1;
		}
		feedback(prefix().append(Component.translatable("cakeupdater.pack.choose", result.remoteVersion())
				.withStyle(ChatFormatting.YELLOW)));
		feedback(Component.literal("  ").append(button("cakeupdater.pack.onclose", "/cakeupdate onclose", ChatFormatting.GREEN)));
		feedback(Component.literal("  ").append(button("cakeupdater.pack.now", "/cakeupdate now", ChatFormatting.RED)));
		return 1;
	}

	/**
	 * The mod never downloads the installer: it runs the packwiz-installer-bootstrap.jar a packwiz install already
	 * has in the game folder. Without it (e.g. a .mrpack install), say so and point to the folder and download page.
	 */
	private static boolean bootstrapMissing() {
		if (Files.exists(gameDir().resolve(Helper.BOOTSTRAP))) {
			return false;
		}
		feedback(prefix().append(Component.translatable("cakeupdater.pack.no_installer", Helper.BOOTSTRAP)
				.withStyle(ChatFormatting.YELLOW)));
		feedback(Component.literal("  ")
				.append(button("cakeupdater.pack.folder", "/cakeupdate folder", ChatFormatting.GREEN))
				.append(" ")
				.append(link("cakeupdater.pack.download_page", Helper.BOOTSTRAP_PAGE)));
		return true;
	}

	private static int openGameFolder() {
		Util.getPlatform().openFile(gameDir().toFile());
		return 1;
	}

	private static int scheduleOnClose() {
		if (bootstrapMissing()) {
			return 1;
		}
		updateOnClose = true;
		feedback(prefix()
				.append(Component.translatable("cakeupdater.pack.scheduled").withStyle(ChatFormatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.cancel", "/cakeupdate cancel", ChatFormatting.GRAY)));
		return 1;
	}

	private static int cancel() {
		updateOnClose = false;
		feedback(prefix().append(Component.translatable("cakeupdater.pack.cancelled").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int updateNow() {
		if (bootstrapMissing()) {
			return 1;
		}
		updateOnClose = true;
		feedback(prefix().append(Component.translatable("cakeupdater.pack.closing").withStyle(ChatFormatting.YELLOW)));
		closeGame(Minecraft.getInstance());
		return 1;
	}

	private static int quit() {
		closeGame(Minecraft.getInstance());
		return 1;
	}

	/** Leaves the world cleanly and quits. */
	static void closeGame(Minecraft client) {
		// Queue it: disconnecting from inside the command handler would tear down the connection mid-packet.
		client.tell(() -> {
			if (client.level != null) {
				boolean singleplayer = client.isLocalServer();
				client.level.disconnect();
				if (singleplayer) {
					client.disconnect(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
				} else {
					client.disconnect();
				}
			}
			client.stop();
		});
	}

	// --- Backups (server list and configs) ---

	static void announceUpdated(Minecraft client, Backups.Backup made) {
		if (client.player == null) {
			return;
		}
		Component updated = made.from().equals(made.to())
				? Component.translatable("cakeupdater.updated.same", made.to())
				: Component.translatable("cakeupdater.updated", made.from(), made.to());
		client.player.displayClientMessage(prefix().append(updated.copy().withStyle(ChatFormatting.YELLOW)), false);
		announcedUpdate = made;
		List<Changelog.Entry> changes = Changelog.between(gameDir(), made.from(), made.to(), client.options.languageCode);
		List<String> items = changes.stream().flatMap(e -> e.items().stream()).toList();
		for (String item : items.subList(0, Math.min(CHAT_ITEMS, items.size()))) {
			client.player.displayClientMessage(Component.literal("  • " + item).withStyle(ChatFormatting.WHITE), false);
		}
		if (items.size() > CHAT_ITEMS) {
			client.player.displayClientMessage(Component.literal("  ")
					.append(Component.translatable("cakeupdater.changelog.more", items.size() - CHAT_ITEMS).withStyle(ChatFormatting.GRAY)), false);
		}
		MutableComponent buttons = Component.literal("  ");
		if (!changes.isEmpty()) {
			buttons.append(button("cakeupdater.changelog.open", "/cakeupdate changelog", ChatFormatting.AQUA)).append(" ");
		}
		if (made.hasServers()) {
			buttons.append(button("cakeupdater.restore.servers", "/cakeupdate restore servers " + made.name(), ChatFormatting.GREEN)).append(" ");
		}
		if (made.hasFiles()) {
			buttons.append(button("cakeupdater.restore.configs", "/cakeupdate restore configs " + made.name(), ChatFormatting.GREEN)).append(" ");
		}
		buttons.append(button("cakeupdater.backups.open", "/cakeupdate backups", ChatFormatting.AQUA));
		client.player.displayClientMessage(buttons, false);
	}

	/** The versions the update announced this start brought, else every changelog the pack ships. */
	private static int openChangelog() {
		Minecraft client = Minecraft.getInstance();
		Backups.Backup made = announcedUpdate;
		List<Changelog.Entry> entries = made != null
				? Changelog.between(gameDir(), made.from(), made.to(), client.options.languageCode)
				: Changelog.between(gameDir(), null, null, client.options.languageCode);
		client.tell(() -> client.setScreen(new ChangelogScreen(null, entries)));
		return 1;
	}

	// --- Memory ---

	private static void checkMemory(Minecraft client) {
		Config.MemorySettings memory = config.memory;
		if (!memory.enabled || client.player == null) {
			return;
		}
		long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
		MemoryCheck.Problem problem = MemoryCheck.warnOnce(gameDir(), maxMb, memory.minGb, memory.maxGb);
		if (problem == null) {
			return;
		}
		Component warning = problem == MemoryCheck.Problem.LOW
				? Component.translatable("cakeupdater.memory.low", MemoryCheck.gb(maxMb), memory.minGb)
				: Component.translatable("cakeupdater.memory.high", MemoryCheck.gb(maxMb), memory.maxGb);
		MutableComponent message = prefix().append(warning.copy().withStyle(ChatFormatting.YELLOW));
		if (memory.guideUrl != null && !memory.guideUrl.isBlank()) {
			message.append(" ").append(link("cakeupdater.memory.guide", memory.guideUrl));
		}
		client.player.displayClientMessage(message, false);
	}

	private static int openBackups() {
		Minecraft client = Minecraft.getInstance();
		// next tick: the chat screen closes after this command returns
		client.tell(() -> client.setScreen(new BackupsScreen(null)));
		return 1;
	}

	private static int restoreServers(String name) {
		try {
			backups.restoreServers(name, config.backups.keep);
			feedback(prefix().append(Component.translatable("cakeupdater.restore.servers.done", BackupsScreen.date(name))
					.withStyle(ChatFormatting.GREEN)));
		} catch (Exception e) {
			LOGGER.error("Server list restore failed", e);
			feedback(prefix().append(Component.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage()))
					.withStyle(ChatFormatting.RED)));
		}
		return 1;
	}

	private static int restoreConfigs(String name) {
		try {
			scheduleConfigRestore(name);
			feedback(prefix().append(Component.translatable("cakeupdater.restore.configs.scheduled", BackupsScreen.date(name))
					.withStyle(ChatFormatting.YELLOW)));
			feedback(Component.literal("  ")
					.append(button("cakeupdater.quit", "/cakeupdate quit", ChatFormatting.RED))
					.append(" ")
					.append(button("cakeupdater.restore.cancel", "/cakeupdate restore cancel", ChatFormatting.GRAY)));
		} catch (Exception e) {
			LOGGER.error("Config restore failed", e);
			feedback(prefix().append(Component.translatable("cakeupdater.restore.failed", String.valueOf(e.getMessage()))
					.withStyle(ChatFormatting.RED)));
		}
		return 1;
	}

	private static int cancelRestore() {
		try {
			backups.cancelConfigRestore();
		} catch (Exception e) {
			LOGGER.warn("Could not cancel the config restore: {}", e.toString());
		}
		feedback(prefix().append(Component.translatable("cakeupdater.restore.cancelled").withStyle(ChatFormatting.GRAY)));
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
	private static void announceGuide(Minecraft client) {
		if (client.player == null) {
			return;
		}
		GuideNotice.Kind kind = GuideNotice.check(gameDir(), UpdateCheck.readLocalVersion(gameDir()),
				Services.PLATFORM.isModLoaded("cakebook"));
		if (kind == null) {
			return;
		}
		Component text = Component.translatable(kind == GuideNotice.Kind.WELCOME ? "cakeupdater.guide.welcome" : "cakeupdater.guide.updated");
		client.player.displayClientMessage(prefix().append(text.copy().withStyle(ChatFormatting.YELLOW)).append(" ")
				.append(button("cakeupdater.guide.open", "/cakebook open " + GuideNotice.PAGE, ChatFormatting.AQUA)), false);
	}

	// --- Import from another install (MoveIn) ---

	/** First join of each start: the result of a finished import, else the offer (until turned off). */
	private static void announceImport(Minecraft client) {
		if (client.player == null) {
			return;
		}
		MoveIn.Done done = MoveIn.takeDone(gameDir());
		if (done != null) {
			client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.done", done.files(),
					MoveIn.label(Path.of(done.source())), BackupsScreen.date(done.backup())).withStyle(ChatFormatting.GREEN))
					.append(" ").append(button("cakeupdater.backups.open", "/cakeupdate backups", ChatFormatting.AQUA)), false);
			return;
		}
		if (MoveIn.offerOff(gameDir()) || MoveIn.pending(gameDir()) != null) {
			return;
		}
		client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.offer").withStyle(ChatFormatting.YELLOW)), false);
		client.player.displayClientMessage(Component.literal("  ")
				.append(button("cakeupdater.import.offer.pick", "/cakeupdate import", ChatFormatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.import.offer.off", "/cakeupdate import off", ChatFormatting.GRAY)), false);
	}

	/** Opens the system's folder window on its own thread (it blocks until the player chooses). */
	private static int pickFolder() {
		if (picking) {
			feedback(prefix().append(Component.translatable("cakeupdater.import.picking.busy").withStyle(ChatFormatting.GRAY)));
			return 1;
		}
		picking = true;
		Minecraft client = Minecraft.getInstance();
		feedback(prefix().append(Component.translatable("cakeupdater.import.picking").withStyle(ChatFormatting.YELLOW)));
		String title = Component.translatable("cakeupdater.import.picker.title").getString();
		// a launcher's instances folder, where the old install most likely is
		Path start = gameDir().toAbsolutePath().getParent();
		if (start != null && start.getParent() != null) {
			start = start.getParent();
		}
		String startPath = start == null ? "" : start.toString() + java.io.File.separator;
		// Next frame: the chat screen that ran this command closes right after it, and would close our screen too.
		// In play the game hides and locks the cursor and Windows keeps the game disabled while the folder window is
		// open, so the player couldn't reach that window (nor Esc). A screen frees the cursor; fullscreen is left so
		// the window can't hide behind the game, and comes back after.
		client.tell(() -> {
			boolean wasFullscreen = client.getWindow().isFullscreen();
			if (wasFullscreen) {
				client.getWindow().toggleFullScreen();
			}
			client.setScreen(new ImportPickScreen());
			Thread thread = new Thread(() -> {
				String chosen = null;
				try {
					chosen = TinyFileDialogs.tinyfd_selectFolderDialog(title, startPath);
				} catch (Throwable t) {
					LOGGER.error("Could not open the folder window", t);
				}
				String result = chosen;
				client.tell(() -> {
					picking = false;
					ImportPickScreen.finished(client);
					if (wasFullscreen && !client.getWindow().isFullscreen()) {
						client.getWindow().toggleFullScreen();
					}
					picked(client, result);
				});
			}, "CakeUpdater import folder");
			thread.setDaemon(true);
			thread.start();
		});
		return 1;
	}

	private static void picked(Minecraft client, String chosen) {
		if (client.player == null) {
			return;
		}
		if (chosen == null || chosen.isBlank()) {
			client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.nofolder").withStyle(ChatFormatting.GRAY)), false);
			return;
		}
		Path old = MoveIn.gameFolder(Path.of(chosen));
		if (MoveIn.sameFolder(old, gameDir())) {
			client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.self").withStyle(ChatFormatting.RED)), false);
			return;
		}
		List<MoveIn.Found> found = new ArrayList<>(MoveIn.scan(old, Services.PLATFORM.isModLoaded("cakechat")));
		List<ServerData> newServers = List.of();
		if (found.removeIf(f -> f.kind() == MoveIn.Kind.SERVERS)) {
			newServers = newServers(client, old.resolve(MoveIn.SERVERS));
		}
		String label = MoveIn.label(old);
		if (found.isEmpty() && newServers.isEmpty()) {
			client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.nothing", label).withStyle(ChatFormatting.YELLOW)), false);
			return;
		}
		scanned = new Scan(old, found, newServers);
		client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.import.found", label).withStyle(ChatFormatting.YELLOW)), false);
		for (MoveIn.Found f : found) {
			Component item = switch (f.kind()) {
				case XAERO -> Component.translatable("cakeupdater.import.item.xaero", f.path(), f.files(),
						String.format(Locale.ROOT, "%.1f", f.bytes() / 1048576.0));
				default -> Component.translatable("cakeupdater.import.item." + f.kind().name().toLowerCase(Locale.ROOT));
			};
			client.player.displayClientMessage(Component.literal("  • ").append(item).withStyle(ChatFormatting.WHITE), false);
		}
		if (!newServers.isEmpty()) {
			client.player.displayClientMessage(Component.literal("  • ").append(Component.translatable("cakeupdater.import.item.servers", newServers.size()))
					.withStyle(ChatFormatting.WHITE), false);
		}
		client.player.displayClientMessage(Component.literal("  ")
				.append(button("cakeupdater.import.confirm", "/cakeupdate import confirm", ChatFormatting.GREEN))
				.append(" ")
				.append(button("cakeupdater.import.cancel", "/cakeupdate import cancel", ChatFormatting.GRAY)), false);
	}

	/** The old server list's entries this one doesn't have (by address, ignoring case). */
	private static List<ServerData> newServers(Minecraft client, Path oldServers) {
		List<ServerData> added = new ArrayList<>();
		try {
			CompoundTag root = NbtIo.read(oldServers);
			if (root == null) {
				return added;
			}
			ServerList ours = new ServerList(client);
			ours.load();
			List<String> known = new ArrayList<>();
			for (int i = 0; i < ours.size(); i++) {
				known.add(ours.get(i).ip.toLowerCase(Locale.ROOT));
			}
			ListTag servers = root.getList("servers", Tag.TAG_COMPOUND);
			for (int i = 0; i < servers.size(); i++) {
				ServerData info = ServerData.read(servers.getCompound(i));
				String address = info.ip == null ? "" : info.ip.toLowerCase(Locale.ROOT);
				if (!address.isBlank() && !known.contains(address) && ours.get(info.ip) == null) {
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
	private static int confirmImport() {
		Scan scan = scanned;
		scanned = null;
		Minecraft client = Minecraft.getInstance();
		if (scan == null) {
			feedback(prefix().append(Component.translatable("cakeupdater.import.choose_first").withStyle(ChatFormatting.GRAY))
					.append(" ").append(button("cakeupdater.import.offer.pick", "/cakeupdate import", ChatFormatting.GREEN)));
			return 1;
		}
		try {
			String label = MoveIn.label(scan.old());
			String backup = backups.newImportBackup(label);
			if (!scan.newServers().isEmpty()) {
				backups.saveBeforeReplace(backup, MoveIn.SERVERS);
				ServerList ours = new ServerList(client);
				ours.load();
				for (ServerData info : scan.newServers()) {
					ours.add(info, false);
				}
				ours.save();
				feedback(prefix().append(Component.translatable("cakeupdater.import.servers.added", scan.newServers().size())
						.withStyle(ChatFormatting.GREEN)));
			}
			List<String> copy = new ArrayList<>();
			com.google.gson.JsonObject cakechat = null;
			Map<String, String> keys = new LinkedHashMap<>();
			for (MoveIn.Found f : scan.found()) {
				if (f.kind() == MoveIn.Kind.CHATPLUS) {
					com.google.gson.JsonObject setup = ChatPlusImport.read(scan.old().resolve(f.path())).orElse(null);
					if (setup != null) {
						ChatPlusImport.Result r = ChatPlusImport.convert(setup, client.getWindow().getGuiScaledHeight());
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
			feedback(prefix().append(Component.translatable("cakeupdater.import.scheduled").withStyle(ChatFormatting.YELLOW)));
			feedback(Component.literal("  ")
					.append(button("cakeupdater.import.quit", "/cakeupdate quit", ChatFormatting.RED))
					.append(" ")
					.append(button("cakeupdater.import.cancel", "/cakeupdate import cancel", ChatFormatting.GRAY)));
		} catch (Exception e) {
			LOGGER.error("Import failed", e);
			feedback(prefix().append(Component.translatable("cakeupdater.import.failed", String.valueOf(e.getMessage()))
					.withStyle(ChatFormatting.RED)));
		}
		return 1;
	}

	private static int cancelImport() {
		scanned = null;
		try {
			MoveIn.cancel(gameDir());
		} catch (Exception e) {
			LOGGER.warn("Could not cancel the import: {}", e.toString());
		}
		feedback(prefix().append(Component.translatable("cakeupdater.import.cancelled").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int importOff() {
		try {
			MoveIn.turnOfferOff(gameDir());
		} catch (Exception e) {
			LOGGER.warn("Could not turn the import offer off: {}", e.toString());
		}
		feedback(prefix().append(Component.translatable("cakeupdater.import.off").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	// --- Resource packs (Modrinth) ---

	private static void announcePacks(Minecraft client, List<ResourcePackUpdater.Pending> updates) {
		String key = updates.toString();
		if (updates.isEmpty() || key.equals(notifiedPacks) || client.player == null) {
			return;
		}
		notifiedPacks = key;
		for (ResourcePackUpdater.Pending update : updates) {
			client.player.displayClientMessage(prefix()
					.append(Component.translatable("cakeupdater.resourcepack.available",
							update.title(), update.fromVersion(), update.toVersion()).withStyle(ChatFormatting.YELLOW))
					.append(" ")
					.append(button("cakeupdater.resourcepack.click", "/cakeupdate resourcepacks", ChatFormatting.GREEN)), false);
		}
	}

	private static int updateResourcePacks() {
		List<ResourcePackUpdater.Pending> updates = pendingPacks;
		if (updates.isEmpty()) {
			feedback(prefix().append(Component.translatable("cakeupdater.resourcepack.none").withStyle(ChatFormatting.GRAY)));
			return 1;
		}
		if (applyingPacks) {
			return 1;
		}
		applyingPacks = true;
		Minecraft client = Minecraft.getInstance();
		for (ResourcePackUpdater.Pending update : updates) {
			feedback(prefix().append(Component.translatable("cakeupdater.resourcepack.downloading",
					update.title(), update.toVersion()).withStyle(ChatFormatting.GRAY)));
		}
		ResourcePackUpdater.apply(client, updates).whenComplete((v, error) -> client.tell(() -> {
			applyingPacks = false;
			if (client.player == null) {
				return;
			}
			if (error != null) {
				LOGGER.error("Resource pack update failed", error);
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.resourcepack.failed",
						String.valueOf(cause.getMessage())).withStyle(ChatFormatting.RED)), false);
				return;
			}
			pendingPacks = List.of();
			for (ResourcePackUpdater.Pending update : updates) {
				client.player.displayClientMessage(prefix().append(Component.translatable("cakeupdater.resourcepack.done",
						update.title(), update.toVersion()).withStyle(ChatFormatting.GREEN)), false);
			}
		}));
		return 1;
	}

	// --- Text helpers ---

	private static MutableComponent prefix() {
		return Component.literal("[" + config.chatPrefix + "] ").withStyle(ChatFormatting.GOLD);
	}

	/** A clickable label; its tooltip is the translation key + ".hover". */
	static MutableComponent button(String key, String command, ChatFormatting color) {
		return Component.translatable(key).withStyle(style -> style
				.withColor(color)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(key + ".hover"))));
	}

	/** A link that opens a web page (Minecraft asks the player to confirm first). */
	private static MutableComponent link(String key, String url) {
		return Component.translatable(key).withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
	}

	static Path gameDir() {
		return CakeUpdaterMod.gameDir();
	}
}
