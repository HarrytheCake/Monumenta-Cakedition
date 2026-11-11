package io.github.harrythecake.cakecosmetic;

import io.github.harrythecake.cakecosmetic.platform.Services;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What can be picked: the mode, the unlock rules, this install's unlocks and the player's choices.
 * Advancements count the moment the game sees them, only on Monumenta (any server in dev mode).
 * Render thread.
 */
final class Unlocks {
	private static Path dir;
	private static Settings settings = new Settings();
	private static Rules rules = new Rules();
	private static UnlockState state = new UnlockState();
	private static Choices choices = new Choices();
	/** Dev mode: show the gallery as a player who has unlocked nothing (not saved). */
	private static boolean previewNewPlayer;
	/** The current connection's advancement progress (the game's own map), or empty. */
	private static Map<AdvancementHolder, AdvancementProgress> live = Map.of();

	private Unlocks() {
	}

	static void load() {
		dir = Services.PLATFORM.configDir().resolve(CakeCosmetic.MOD_ID);
		settings = Settings.load(dir);
		rules = Rules.load(dir);
		state = UnlockState.load(dir);
		choices = Choices.load(dir);
	}

	/** Reads config.json5 and rules.json5 again (when the gallery opens, so edits by hand apply). */
	static void reloadFiles() {
		settings = Settings.load(dir);
		rules = Rules.load(dir);
	}

	static Settings settings() {
		return settings;
	}

	static Rules rules() {
		return rules;
	}

	static Choices choices() {
		return choices;
	}

	static void saveChoices() {
		choices.save(dir);
		MenuBackground.invalidate();
		LoadingBackground.invalidate();
	}

	static boolean previewNewPlayer() {
		return previewNewPlayer;
	}

	static void setPreviewNewPlayer(boolean on) {
		previewNewPlayer = on;
		MenuBackground.invalidate();
		LoadingBackground.invalidate();
	}

	/** True when the player may pick this background now. */
	static boolean pickable(String id) {
		if (Choices.VANILLA.equals(id)) {
			return true;
		}
		Rules.Rule rule = rules.get(id);
		if (rule == null || rule.isEmpty()) {
			return true;
		}
		if (previewNewPlayer) {
			return false;
		}
		return settings.mode == Settings.Mode.ALL || state.backgrounds.contains(id);
	}

	/** True when the background has a rule this install has met (shown as unlocked even in all mode). */
	static boolean unlockedByRule(String id) {
		return !previewNewPlayer && state.backgrounds.contains(id);
	}

	/** Every background from the resource packs the player may pick now, in gallery order. */
	static List<String> pickableBackgrounds() {
		List<String> out = new ArrayList<>();
		for (Backgrounds.Background b : Backgrounds.list()) {
			if (pickable(b.id())) {
				out.add(b.id());
			}
		}
		return out;
	}

	/** Whether advancements seen now count: on Monumenta, or anywhere in dev mode. */
	static boolean countsHere() {
		if (settings.devMode) {
			return true;
		}
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("playmonumenta.com");
	}

	/** Earned advancement ids of the current connection. */
	static Set<String> earnedNow() {
		Set<String> out = new HashSet<>();
		for (Map.Entry<AdvancementHolder, AdvancementProgress> e : live.entrySet()) {
			if (e.getValue().isDone()) {
				out.add(e.getKey().id().toString());
			}
		}
		return out;
	}

	/** After the game applied an advancement packet (ClientAdvancementManagerMixin). */
	static void onAdvancements(Map<AdvancementHolder, AdvancementProgress> progress) {
		live = progress;
		check(true);
	}

	/** When the connection ends. */
	static void onDisconnect() {
		live = Map.of();
	}

	/** Unlocks what the advancements seen now allow; toasts only in advancement mode. */
	static void check(boolean toast) {
		if (!countsHere() || live.isEmpty()) {
			return;
		}
		Set<String> relevant = new HashSet<>(earnedNow());
		relevant.retainAll(rules.advancementIds());
		boolean changed = state.advancements.addAll(relevant);
		List<String> fresh = rules.newlyUnlocked(state.advancements, state.backgrounds);
		if (fresh.isEmpty() && !changed) {
			return;
		}
		state.backgrounds.addAll(fresh);
		state.save(dir);
		if (!fresh.isEmpty()) {
			CakeCosmetic.LOG.info("CakeCosmetic: unlocked {}", fresh);
			MenuBackground.invalidate();
			if (toast && settings.mode == Settings.Mode.ADVANCEMENT) {
				UnlockToast.show(fresh);
			}
		}
	}

	/** Dev mode: forget every unlock of this install. */
	static void forget() {
		state = new UnlockState();
		state.save(dir);
		MenuBackground.invalidate();
		LoadingBackground.invalidate();
	}

	/** Dev mode: save a background's rule, then unlock at once what the current advancements allow. */
	static void saveRule(String background, Rules.Rule rule) throws IOException {
		rules.put(background, rule);
		rules.save(dir);
		check(false);
		MenuBackground.invalidate();
		LoadingBackground.invalidate();
	}

	/** The game's advancements of this connection: id → (title, earned), for the rule editor. */
	static Map<String, AdvancementInfo> knownAdvancements() {
		Map<String, AdvancementInfo> out = new java.util.TreeMap<>();
		for (Map.Entry<AdvancementHolder, AdvancementProgress> e : live.entrySet()) {
			add(out, e.getKey(), e.getValue().isDone());
		}
		var handler = Minecraft.getInstance().getConnection();
		if (handler != null) {
			for (var placed : handler.getAdvancements().getTree().nodes()) {
				AdvancementHolder entry = placed.holder();
				if (!out.containsKey(entry.id().toString())) {
					add(out, entry, false);
				}
			}
		}
		return Collections.unmodifiableMap(out);
	}

	private static void add(Map<String, AdvancementInfo> out, AdvancementHolder entry, boolean done) {
		String title = entry.value().display().map(d -> d.getTitle().getString()).orElse("");
		boolean hidden = entry.value().display().map(d -> d.isHidden()).orElse(true);
		out.put(entry.id().toString(), new AdvancementInfo(entry.id().toString(), title, done, hidden));
	}

	/** An advancement as the rule editor lists it. */
	record AdvancementInfo(String id, String title, boolean earned, boolean hidden) {
	}
}
