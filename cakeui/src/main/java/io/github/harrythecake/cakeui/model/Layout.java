package io.github.harrythecake.cakeui.model;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A loaded layout folder (`window_<name>/`): the window settings and every part found. */
public final class Layout {
	public static final int FORMAT = 1;
	public static final List<String> PARTS = List.of("game_launch", "main_menu", "loading_screen", "hud");

	public enum ScaleMode {
		FIT, FILL, STRETCH
	}

	/** A TTF/OTF font from the layout's assets, named so text elements can use it. */
	public record FontSpec(String name, String file, double size, double oversample, double shiftX, double shiftY) {
	}

	public final String name;
	public final Path root;
	public final Scope scope;
	public double designWidth;
	public double designHeight;
	public ScaleMode scaleMode = ScaleMode.FIT;
	/** Perspective distance in design pixels for parts that don't set their own. */
	public double perspective = 1000;
	public final Map<String, FontSpec> fonts = new LinkedHashMap<>();
	public final Map<String, Node> parts = new LinkedHashMap<>();
	/** Layout strings: language code → key → text. */
	public final Map<String, Map<String, String>> lang = new LinkedHashMap<>();
	public final Problems problems = new Problems();

	public Layout(String name, Path root) {
		this.name = name;
		this.root = root;
		this.scope = new Scope(null, "");
	}

	public Node part(String id) {
		return parts.get(id);
	}

	/** GUI pixels per design pixel on each axis for a screen of the given GUI size. */
	public double[] designScale(double screenWidth, double screenHeight) {
		if (designWidth <= 0 || designHeight <= 0) {
			return new double[] {1, 1};
		}
		double sx = screenWidth / designWidth;
		double sy = screenHeight / designHeight;
		return switch (scaleMode) {
			case FIT -> new double[] {Math.min(sx, sy), Math.min(sx, sy)};
			case FILL -> new double[] {Math.max(sx, sy), Math.max(sx, sy)};
			case STRETCH -> new double[] {sx, sy};
		};
	}

	/** A layout string for the language, falling back to en_us; null when neither has it. */
	public String translate(String language, String key) {
		Map<String, String> m = lang.get(language);
		if (m != null && m.containsKey(key)) {
			return m.get(key);
		}
		Map<String, String> en = lang.get("en_us");
		return en == null ? null : en.get(key);
	}
}
