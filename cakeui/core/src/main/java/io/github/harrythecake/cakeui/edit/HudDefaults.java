package io.github.harrythecake.cakeui.edit;

import io.github.harrythecake.cakeui.edit.Placement.Box;

import java.util.List;

/**
 * Vanilla's HUD pieces: which ones the HUD editor offers, and roughly where vanilla draws each one
 * (GUI pixels), for previews of pieces that aren't on screen right now and as a first guess before
 * a piece was measured.
 */
public final class HudDefaults {
	/** The vanilla ports the editor offers, in the order of its list (full-screen overlays aren't movable). */
	public static final List<String> EDITABLE = List.of("hotbar", "health", "armor", "hunger", "air", "mountHealth", "experience",
			"heldItemName", "crosshair", "bossBars", "statusEffects", "scoreboard", "tabList", "actionBar", "title");

	private HudDefaults() {
	}

	/** Vanilla's place for a port on a {@code w} x {@code h} GUI; a 64x64 box in the middle for an unknown one. */
	public static Box box(String port, int w, int h) {
		double cx = w / 2.0;
		return switch (port) {
			case "hotbar" -> new Box(cx - 91, h - 22, 182, 22);
			case "health" -> new Box(cx - 91, h - 39, 81, 9);
			case "armor" -> new Box(cx - 91, h - 49, 81, 9);
			case "hunger", "mountHealth" -> new Box(cx + 10, h - 39, 81, 9);
			case "air" -> new Box(cx + 10, h - 49, 81, 9);
			case "experience" -> new Box(cx - 91, h - 36, 182, 12);
			case "heldItemName" -> new Box(cx - 50, h - 59, 100, 9);
			case "crosshair" -> new Box(cx - 7, h / 2.0 - 7, 15, 15);
			case "bossBars" -> new Box(cx - 91, 3, 182, 14);
			case "statusEffects" -> new Box(w - 75, 1, 74, 50);
			case "scoreboard" -> new Box(w - 102, h / 2.0 - 40, 101, 80);
			case "tabList" -> new Box(cx - 110, 10, 220, 60);
			case "actionBar" -> new Box(cx - 80, h - 72, 160, 9);
			case "title" -> new Box(cx - 100, h / 2.0 - 40, 200, 50);
			default -> new Box(cx - 32, h / 2.0 - 32, 64, 64);
		};
	}
}
