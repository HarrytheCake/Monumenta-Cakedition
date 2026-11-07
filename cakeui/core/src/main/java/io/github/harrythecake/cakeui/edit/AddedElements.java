package io.github.harrythecake.cakeui.edit;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Elements the HUD editor adds (E3, ADR 0010): the types offered and what a new one starts as. Each is
 * an ordinary element in CakeUI's language, so text templates ({fps}), expressions and the "visible"
 * condition work as in a layout. Sizes are GUI pixels, as everywhere on the HUD.
 */
public final class AddedElements {
	public static final List<String> TYPES = List.of("text", "rect", "bar", "image");

	private AddedElements() {
	}

	/** A new key for an added element of this type ("+text-3f9a"), not yet in {@code taken}. */
	public static String newKey(String type, Map<String, ?> taken) {
		String key;
		do {
			key = HudEdits.ADDED + type + "-" + UUID.randomUUID().toString().substring(0, 4);
		} while (taken.containsKey(key));
		return key;
	}

	/** What a new element of this type looks like (image: {@code imageFile}, or none yet). */
	public static JsonObject defaults(String type, String imageFile) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		switch (type) {
			case "text" -> {
				o.addProperty("text", "{fps} fps");
				o.addProperty("size", "9px");
				o.addProperty("color", "#ffffff");
				o.addProperty("shadow", true);
			}
			case "rect" -> {
				o.addProperty("width", "40px");
				o.addProperty("height", "20px");
				o.addProperty("color", "#00000080");
			}
			case "bar" -> {
				o.addProperty("width", "80px");
				o.addProperty("height", "5px");
				o.addProperty("value", "player.health / player.maxHealth");
				o.addProperty("color", "#e04040");
				o.addProperty("background", "#00000080");
			}
			case "image" -> {
				o.addProperty("width", "32px");
				o.addProperty("height", "32px");
				o.addProperty("src", imageFile == null ? "" : imageFile);
			}
			default -> throw new IllegalArgumentException(type);
		}
		return o;
	}

	/** The size a new element is placed with before it was measured (GUI pixels). */
	public static double[] guessSize(JsonObject element) {
		return switch (element.has("type") ? element.get("type").getAsString() : "") {
			case "text" -> new double[] {40, 9};
			case "rect" -> new double[] {40, 20};
			case "bar" -> new double[] {80, 5};
			default -> new double[] {32, 32};
		};
	}

	/** The element's type, or "" for a damaged definition. */
	public static String type(JsonObject element) {
		return element != null && element.has("type") ? element.get("type").getAsString() : "";
	}
}
