package io.github.harrythecake.cakemap.core;

import java.util.Locale;

/**
 * What a waypoint marks; each kind has its own icon ({@code textures/waypoint/<icon>.svg}).
 * Permanent kinds stay until deleted; temporary ones vanish when reached or when the player leaves
 * the server. {@code QUEST_MAIN} is a quest giver with a story, wool or major side quest (the
 * quest flowchart's coloured boxes); {@code QUEST} is one with side quests only.
 */
public enum WaypointKind {
	DUNGEON(false),
	POI(false),
	TOWN(false),
	TELEPORT(false),
	QUEST(false),
	QUEST_MAIN(false),
	CHARM_TRADER(false),
	TRADER(false),
	PLAYER(false),
	BOSS(false),
	/** Where the player died (kept: the newest {@link WaypointStore#DEATHS_KEPT} per server). */
	DEATH(false),
	QUEST_OBJECTIVE(true),
	PLAYER_TEMPORARY(true);

	public final boolean temporary;

	WaypointKind(boolean temporary) {
		this.temporary = temporary;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The icon file's base name; temporary player waypoints share the player icon (#1.8 = #2.2). */
	public String icon() {
		return this == PLAYER_TEMPORARY ? "player" : id();
	}

	public String translationKey() {
		return "cakemap.kind." + id();
	}

	public static WaypointKind parse(String s, WaypointKind fallback) {
		if (s == null) {
			return fallback;
		}
		try {
			return valueOf(s.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
