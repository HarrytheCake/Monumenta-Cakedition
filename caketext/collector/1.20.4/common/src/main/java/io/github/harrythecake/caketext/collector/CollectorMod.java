package io.github.harrythecake.caketext.collector;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.github.harrythecake.caketext.collector.mixin.BossBarHudAccessor;
import io.github.harrythecake.caketext.collector.mixin.PlayerListHudAccessor;
import io.github.harrythecake.caketext.collector.mixin.TextDisplayAccessor;
import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.context.Surfaces;
import io.github.harrythecake.caketext.core.module.CakeTextModule;
import io.github.harrythecake.caketext.core.module.Modules;
import io.github.harrythecake.caketext.core.template.PlayerNames;
import io.github.harrythecake.caketext.core.template.TextTemplate;
import io.github.harrythecake.caketext.core.template.Translated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * CakeText Collector: watches the places a server puts text and records each new string with where it was seen.
 * Everything stays in {@code config/caketext/collected/}; nothing is uploaded.
 *
 * <p>Loader-neutral: the Fabric and NeoForge entry classes call {@link #init()} and pass their events to the
 * {@code on…} methods.
 */
public final class CollectorMod {
	public static final String MOD_ID = "caketext_collector";
	private static final int SCAN_TICKS = 20;

	private static CollectorConfig config;
	private static Store store;
	private static ScheduledExecutorService saver;
	private static ScheduledFuture<?> saveTask;
	private static int ticks;

	private CollectorMod() {
	}

	public static CollectorConfig config() {
		return config;
	}

	static Store store() {
		return store;
	}

	public static void setConfig(CollectorConfig newConfig) {
		boolean rescheduled = newConfig.saveSeconds != config.saveSeconds;
		config = newConfig;
		store.setConfig(newConfig);
		newConfig.save();
		if (rescheduled) {
			schedule();
		}
	}

	/**
	 * Records {@code text} seen on {@code surface} ({@code pos}: where the text is, or null for HUD text).
	 * Call on the client thread. Modules get this as their {@link io.github.harrythecake.caketext.core.module.TextSink}.
	 */
	public static void collect(String surface, @Nullable Component text, @Nullable Vec3 pos) {
		if (store == null || text == null || !config.enabled || !config.source(surface) || Translated.is(text)) {
			return;
		}
		try {
			Minecraft client = Minecraft.getInstance();
			String server = GameContext.server(client);
			if (server == null || config.multiplayerOnly && "singleplayer".equals(server)) {
				return;
			}
			store.open(server);
			TextTemplate t = TextTemplate.of(text);
			if (!t.hasWords() || PrivacyFilter.hasHan(t.original()) || PrivacyFilter.reject(surface, t.template(), t.original())) {
				return;
			}
			store.record(t.template(), GameContext.of(surface, pos));
		} catch (RuntimeException e) {
			CakeText.LOG.warn("Collecting from {} failed", surface, e);
		}
	}

	/** Client start: settings, store, save timer, modules' sources. */
	public static void init() {
		config = CollectorConfig.load();
		store = new Store(config);
		saver = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CakeText-collector-save");
			t.setDaemon(true);
			return t;
		});
		schedule();

		for (CakeTextModule module : Modules.active()) {
			try {
				module.registerSources(CollectorMod::collect);
			} catch (Throwable t) {
				CakeText.LOG.warn("CakeText module '{}' failed to add sources", module.id(), t);
			}
		}

	}

	/**
	 * A game (server) message, chat or action bar. The loaders call this before any mod changes the message,
	 * so it sees the server's original text. Signed player chat never comes here.
	 */
	public static void onGameMessage(Component message, boolean overlay) {
		collect(overlay ? Surfaces.ACTION_BAR : Surfaces.CHAT, message, null);
	}

	public static void onTooltip(List<Component> lines) {
		for (Component line : lines) {
			collect(Surfaces.TOOLTIP, line, null);
		}
	}

	/** After a screen's init: menu titles from the server. */
	public static void onScreenInit(Screen screen) {
		Component title = screen.getTitle();
		if (title != null && !(title.getContents() instanceof TranslatableContents)) {
			collect(Surfaces.SCREEN_TITLE, title, null);
		}
	}

	/** End of every client tick. */
	public static void onTick(Minecraft client) {
		if (++ticks % SCAN_TICKS == 0 && client.level != null && client.player != null) {
			PlayerNames.refresh(client);
			scan(client);
		}
	}

	public static void onJoin(Minecraft client) {
		PlayerNames.refresh(client);
		SelfTest.start(client);
	}

	public static void onDisconnect() {
		saver.execute(() -> {
			store.flush();
			autoExportNow();
		});
	}

	public static void onStopping() {
		store.flush();
		autoExportNow();
	}

	private static void schedule() {
		if (saveTask != null) {
			saveTask.cancel(false);
		}
		saveTask = saver.scheduleWithFixedDelay(() -> {
			try {
				store.flush();
				if (System.currentTimeMillis() - lastAutoExport >= config.autoExportMinutes * 60_000L) {
					autoExportNow();
				}
			} catch (Throwable t) {
				CakeText.LOG.warn("Saving collected strings failed", t);
			}
		}, config.saveSeconds, config.saveSeconds, TimeUnit.SECONDS);
	}

	private static volatile long lastAutoExport = System.currentTimeMillis();

	/** Rewrites the Crowdin files ({@code exports/crowdin/<server>/}) when auto-export is on and something new was collected. */
	static void autoExportNow() {
		if (config == null || store == null || !config.autoExport) {
			return;
		}
		lastAutoExport = System.currentTimeMillis();
		try {
			java.nio.file.Path path = store.autoExport(config.exportPositions);
			if (path != null) {
				CakeText.LOG.info("CakeText auto-export: {}", path);
			}
		} catch (java.io.IOException e) {
			CakeText.LOG.warn("Auto-export failed", e);
		}
	}

	/** Text that stays on screen: read it once a second. */
	private static void scan(Minecraft client) {
		if (!config.enabled) {
			return;
		}
		if (config.source(Surfaces.SCOREBOARD)) {
			Scoreboard sb = client.level.getScoreboard();
			Objective objective = null;
			PlayerTeam team = sb.getPlayersTeam(client.player.getScoreboardName());
			if (team != null) {
				DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
				if (slot != null) {
					objective = sb.getDisplayObjective(slot);
				}
			}
			if (objective == null) {
				objective = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
			}
			if (objective != null) {
				collect(Surfaces.SCOREBOARD, objective.getDisplayName(), null);
				for (PlayerScoreEntry entry : sb.listPlayerScores(objective)) {
					if (!entry.isHidden()) {
						collect(Surfaces.SCOREBOARD, PlayerTeam.formatNameForTeam(sb.getPlayersTeam(entry.owner()), entry.ownerName()), null);
					}
				}
			}
		}
		if (config.source(Surfaces.BOSS_BAR)) {
			for (LerpingBossEvent bar : List.copyOf(((BossBarHudAccessor) client.gui.getBossOverlay()).caketext$bossBars().values())) {
				collect(Surfaces.BOSS_BAR, bar.getName(), null);
			}
		}
		if (config.source(Surfaces.TAB_LIST)) {
			PlayerListHudAccessor tab = (PlayerListHudAccessor) client.gui.getTabList();
			collect(Surfaces.TAB_LIST, tab.caketext$header(), null);
			collect(Surfaces.TAB_LIST, tab.caketext$footer(), null);
		}
		if (config.source(Surfaces.NAMEPLATE)) {
			double range = (double) config.nameplateRange * config.nameplateRange;
			for (Entity e : client.level.entitiesForRendering()) {
				if (e instanceof Player || e.distanceToSqr(client.player) > range) {
					continue;
				}
				if (e instanceof Display.TextDisplay) {
					collect(Surfaces.NAMEPLATE, ((TextDisplayAccessor) e).caketext$getText(), e.position());
				} else if (e.hasCustomName() && e.isCustomNameVisible()) {
					collect(Surfaces.NAMEPLATE, e.getCustomName(), e.position());
				}
			}
		}
	}
}
