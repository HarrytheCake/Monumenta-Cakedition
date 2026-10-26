package io.github.harrythecake.caketext.core.context;

import java.util.List;

/** Where text appears. Modules may add their own ids (e.g. {@code umm_hud}). */
public final class Surfaces {
	public static final String CHAT = "chat";
	public static final String ACTION_BAR = "action_bar";
	public static final String TITLE = "title";
	public static final String SUBTITLE = "subtitle";
	public static final String SCOREBOARD = "scoreboard";
	public static final String BOSS_BAR = "boss_bar";
	public static final String TAB_LIST = "tab_list";
	public static final String TOOLTIP = "tooltip";
	public static final String SCREEN_TITLE = "screen_title";
	/** Custom names over entities and text display entities (holograms). */
	public static final String NAMEPLATE = "nameplate";

	/** What players say. Never collected; the translator handles it separately (local model only, never saved). */
	public static final String PLAYER_CHAT = "player_chat";

	public static final List<String> BUILT_IN = List.of(CHAT, ACTION_BAR, TITLE, SUBTITLE, SCOREBOARD, BOSS_BAR,
			TAB_LIST, TOOLTIP, SCREEN_TITLE, NAMEPLATE);

	private Surfaces() {
	}

	/** Text that stays in one place, so its exact block is kept. */
	public static boolean isPlaced(String surface) {
		return NAMEPLATE.equals(surface);
	}
}
