package io.github.harrythecake.cakeui.edit;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Crosshair styles (E4, ADR 0010 Q36): stored as the crosshair port's {@code element}
 * ({@code type: "crosshair"}). They replace vanilla's crosshair sprite only, so the attack-cooldown
 * indicator stays. Sizes in GUI pixels; {@code invert} draws like vanilla (inverting what is behind).
 */
public final class CrosshairStyles {
	public static final String TYPE = "crosshair";
	/** "vanilla" (first) = no style. */
	public static final List<String> SHAPES = List.of("vanilla", "dot", "cross", "circle", "image");

	private CrosshairStyles() {
	}

	public static JsonObject defaults(String shape, String imageFile) {
		JsonObject o = new JsonObject();
		o.addProperty("type", TYPE);
		o.addProperty("shape", shape);
		o.addProperty("color", "#ffffff");
		o.addProperty("invert", true);
		switch (shape) {
			case "dot" -> o.addProperty("size", 2);
			case "cross" -> {
				o.addProperty("size", 5);
				o.addProperty("gap", 1);
				o.addProperty("thickness", 1);
			}
			case "circle" -> {
				o.addProperty("size", 4);
				o.addProperty("thickness", 1);
			}
			case "image" -> {
				o.addProperty("size", 15);
				o.addProperty("src", imageFile == null ? "" : imageFile);
				o.addProperty("invert", false);
			}
			default -> throw new IllegalArgumentException(shape);
		}
		return o;
	}

	public static boolean is(JsonObject element) {
		return element != null && element.has("type") && TYPE.equals(element.get("type").getAsString());
	}

	public static String shape(JsonObject element) {
		return is(element) && element.has("shape") ? element.get("shape").getAsString() : "vanilla";
	}

	public static double num(JsonObject e, String key, double def) {
		try {
			return e.has(key) ? Double.parseDouble(e.get(key).getAsString().replace("px", "").trim()) : def;
		} catch (NumberFormatException | UnsupportedOperationException ex) {
			return def;
		}
	}

	/** The rectangles of a dot, cross or circle around (cx, cy): {x1, y1, x2, y2} each. */
	public static List<float[]> rects(JsonObject e, float cx, float cy) {
		String shape = shape(e);
		float size = (float) Math.max(0.5, num(e, "size", 3));
		float t = (float) Math.max(0.5, num(e, "thickness", 1));
		float gap = (float) Math.max(0, num(e, "gap", 1));
		java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
		switch (shape) {
			case "dot" -> out.add(new float[] {cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2});
			case "cross" -> {
				out.add(new float[] {cx - gap - size, cy - t / 2, cx - gap, cy + t / 2});
				out.add(new float[] {cx + gap, cy - t / 2, cx + gap + size, cy + t / 2});
				out.add(new float[] {cx - t / 2, cy - gap - size, cx + t / 2, cy - gap});
				out.add(new float[] {cx - t / 2, cy + gap, cx + t / 2, cy + gap + size});
			}
			case "circle" -> {
				int n = Math.max(16, Math.round(size * 8));
				for (int i = 0; i < n; i++) {
					double a = 2 * Math.PI * i / n;
					float px = (float) (cx + Math.cos(a) * size);
					float py = (float) (cy + Math.sin(a) * size);
					out.add(new float[] {px - t / 2, py - t / 2, px + t / 2, py + t / 2});
				}
			}
			default -> {
			}
		}
		return out;
	}
}
