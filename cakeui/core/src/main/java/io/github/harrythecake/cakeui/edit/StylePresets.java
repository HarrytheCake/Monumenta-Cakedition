package io.github.harrythecake.cakeui.edit;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Ready-made looks for HUD ports (E4, ADR 0010 Q23/Q32): choosing one hides the vanilla piece and adds
 * one ordinary element (a group of bars and texts, key {@code +style-<port>}) where it was, which the
 * player can then move and edit like any other. Numbers are language-free (colour tells which is which).
 * Each shows only when vanilla would show its piece (survival, armor &gt; 0, under water, riding).
 */
public final class StylePresets {
	/** Styles per port; "vanilla" (the first) means no preset. */
	public static final Map<String, List<String>> STYLES = Map.of(
			"health", List.of("vanilla", "bar", "number", "barNumber"),
			"hunger", List.of("vanilla", "bar", "number", "barNumber"),
			"armor", List.of("vanilla", "bar", "number", "barNumber"),
			"air", List.of("vanilla", "bar", "number", "barNumber"),
			"mountHealth", List.of("vanilla", "bar", "number", "barNumber"),
			"experience", List.of("vanilla", "slim", "level"));

	private StylePresets() {
	}

	public static String key(String port) {
		return HudEdits.ADDED + "style-" + port;
	}

	/** The port a preset element belongs to, or null for other added elements. */
	public static String portOf(String key) {
		String prefix = HudEdits.ADDED + "style-";
		return key.startsWith(prefix) ? key.substring(prefix.length()) : null;
	}

	private record Look(String value, String max, String color, String extra, String extraColor, String text, String textExtra, String visible) {
	}

	private static final Map<String, Look> LOOKS = Map.of(
			"health", new Look("player.health", "player.maxHealth", "#e04040", "player.absorption", "#f0d040",
					"{fmt(player.health, 0)}/{fmt(player.maxHealth, 0)}", "+{fmt(player.absorption, 0)}", "player.survival"),
			"hunger", new Look("player.food", "20", "#d08030", "player.saturation", "#f0e060",
					"{player.food}", "{fmt(player.saturation, 1)}", "player.survival && !(mount.maxHealth > 0)"),
			"armor", new Look("player.armor", "20", "#b0b8c0", "player.toughness", "#60d0e0",
					"{player.armor}", "+{fmt(player.toughness, 0)}", "player.survival && player.armor > 0"),
			"air", new Look("player.air", "player.maxAir", "#60a0ff", null, null,
					"{fmt(max(0, player.air) / 20, 0)}s", null, "player.survival && player.air < player.maxAir"),
			"mountHealth", new Look("mount.health", "mount.maxHealth", "#e07060", null, null,
					"{fmt(mount.health, 0)}/{fmt(mount.maxHealth, 0)}", null, "mount.maxHealth > 0"));

	/** The element a style adds for a port. */
	public static JsonObject element(String port, String style) {
		if (port.equals("experience")) {
			return experience(style);
		}
		Look l = LOOKS.get(port);
		if (l == null) {
			throw new IllegalArgumentException(port);
		}
		JsonObject group = group("column", "81px", style.equals("barNumber") ? "18px" : style.equals("bar") ? "8px" : "9px");
		group.addProperty("visible", "= " + l.visible);
		JsonObject kids = new JsonObject();
		if (style.equals("number") || style.equals("barNumber")) {
			JsonObject row = group("row", "81px", "9px");
			JsonObject texts = new JsonObject();
			texts.add("value", text(l.text, l.color, null));
			if (l.textExtra != null) {
				texts.add("extra", text(" " + l.textExtra, l.extraColor, l.extra + " > 0"));
			}
			row.add("elements", texts);
			kids.add("numbers", row);
		}
		if (style.equals("bar") || style.equals("barNumber")) {
			kids.add("bar", bar("min(1, max(0, " + l.value + " / max(1, " + l.max + ")))", l.color, "5px", null));
			if (l.extra != null) {
				kids.add("extraBar", bar("min(1, " + l.extra + " / max(1, " + l.max + "))", l.extraColor, "2px", l.extra + " > 0"));
			}
		}
		group.add("elements", kids);
		return group;
	}

	private static JsonObject experience(String style) {
		if (style.equals("slim")) {
			JsonObject b = bar("player.xp", "#80ff20", "2px", "player.survival");
			b.addProperty("width", "182px");
			return b;
		}
		JsonObject t = text("{player.level}", "#80ff20", "player.survival && player.level > 0");
		t.addProperty("size", "9px");
		return t;
	}

	private static JsonObject group(String arrange, String w, String h) {
		JsonObject g = new JsonObject();
		g.addProperty("type", "group");
		g.addProperty("arrange", arrange);
		g.addProperty("gap", "1px");
		g.addProperty("width", w);
		g.addProperty("height", h);
		return g;
	}

	private static JsonObject text(String template, String color, String visible) {
		JsonObject t = new JsonObject();
		t.addProperty("type", "text");
		t.addProperty("text", template);
		t.addProperty("size", "9px");
		t.addProperty("color", color);
		t.addProperty("shadow", true);
		if (visible != null) {
			t.addProperty("visible", "= " + visible);
		}
		return t;
	}

	private static JsonObject bar(String value, String color, String height, String visible) {
		JsonObject b = new JsonObject();
		b.addProperty("type", "bar");
		b.addProperty("width", "81px");
		b.addProperty("height", height);
		b.addProperty("value", value);
		b.addProperty("color", color);
		b.addProperty("background", "#00000080");
		if (visible != null) {
			b.addProperty("visible", "= " + visible);
		}
		return b;
	}
}
