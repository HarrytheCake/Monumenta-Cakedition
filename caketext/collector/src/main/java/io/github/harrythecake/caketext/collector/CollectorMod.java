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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * CakeText Collector: watches the places a server puts text and records each new string with where it was seen.
 * Everything stays in {@code config/caketext/collected/}; nothing is uploaded.
 */
public final class CollectorMod implements ClientModInitializer {
	private static final int SCAN_TICKS = 20;

	private static CollectorConfig config;
	private static Store store;
	private static ScheduledExecutorService saver;
	private static ScheduledFuture<?> saveTask;
	private int ticks;

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
	public static void collect(String surface, @Nullable Text text, @Nullable Vec3d pos) {
		if (store == null || text == null || !config.enabled || !config.source(surface) || Translated.is(text)) {
			return;
		}
		try {
			MinecraftClient client = MinecraftClient.getInstance();
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

	@Override
	public void onInitializeClient() {
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

		// ALLOW runs before any mod changes the message, so this sees the server's original text
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			collect(overlay ? Surfaces.ACTION_BAR : Surfaces.CHAT, message, null);
			return true;
		});
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			for (Text line : lines) {
				collect(Surfaces.TOOLTIP, line, null);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			Text title = screen.getTitle();
			if (title != null && !(title.getContent() instanceof TranslatableTextContent)) {
				collect(Surfaces.SCREEN_TITLE, title, null);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks % SCAN_TICKS == 0 && client.world != null && client.player != null) {
				PlayerNames.refresh(client);
				scan(client);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PlayerNames.refresh(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saver.execute(() -> {
			store.flush();
			autoExportNow();
		}));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			store.flush();
			autoExportNow();
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> CollectorCommands.register(dispatcher));
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
	private static void scan(MinecraftClient client) {
		if (!config.enabled) {
			return;
		}
		if (config.source(Surfaces.SCOREBOARD)) {
			Scoreboard sb = client.world.getScoreboard();
			ScoreboardObjective objective = null;
			Team team = sb.getScoreHolderTeam(client.player.getNameForScoreboard());
			if (team != null) {
				ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
				if (slot != null) {
					objective = sb.getObjectiveForSlot(slot);
				}
			}
			if (objective == null) {
				objective = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
			}
			if (objective != null) {
				collect(Surfaces.SCOREBOARD, objective.getDisplayName(), null);
				for (ScoreboardEntry entry : sb.getScoreboardEntries(objective)) {
					if (!entry.hidden()) {
						collect(Surfaces.SCOREBOARD, Team.decorateName(sb.getScoreHolderTeam(entry.owner()), entry.name()), null);
					}
				}
			}
		}
		if (config.source(Surfaces.BOSS_BAR)) {
			for (ClientBossBar bar : List.copyOf(((BossBarHudAccessor) client.inGameHud.getBossBarHud()).caketext$bossBars().values())) {
				collect(Surfaces.BOSS_BAR, bar.getName(), null);
			}
		}
		if (config.source(Surfaces.TAB_LIST)) {
			PlayerListHudAccessor tab = (PlayerListHudAccessor) client.inGameHud.getPlayerListHud();
			collect(Surfaces.TAB_LIST, tab.caketext$header(), null);
			collect(Surfaces.TAB_LIST, tab.caketext$footer(), null);
		}
		if (config.source(Surfaces.NAMEPLATE)) {
			double range = (double) config.nameplateRange * config.nameplateRange;
			for (Entity e : client.world.getEntities()) {
				if (e instanceof PlayerEntity || e.squaredDistanceTo(client.player) > range) {
					continue;
				}
				if (e instanceof DisplayEntity.TextDisplayEntity) {
					collect(Surfaces.NAMEPLATE, ((TextDisplayAccessor) e).caketext$getText(), e.getPos());
				} else if (e.hasCustomName() && e.isCustomNameVisible()) {
					collect(Surfaces.NAMEPLATE, e.getCustomName(), e.getPos());
				}
			}
		}
	}
}
