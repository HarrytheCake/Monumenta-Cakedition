package io.github.harrythecake.cakeui.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which properties exist, what kind of value each takes, and which element types accept them. */
public final class PropKinds {
	public enum Kind {
		/** A length: a plain number is design pixels (r); text may use units and expressions. */
		LENGTH,
		NUMBER,
		COLOR,
		BOOL,
		/** Text with {expressions}. */
		TEXT,
		/** A word or id (anchor, src, easing); may also be a template. */
		WORD
	}

	public static final List<String> ELEMENT_TYPES = List.of("text", "image", "rect", "bar", "item", "button", "group");
	public static final List<String> CONTAINERS = List.of("group", "button", "subwindow", "part");
	public static final List<String> ANCHORS = List.of("top_left", "top", "top_right", "left", "center", "right", "bottom_left", "bottom", "bottom_right");
	public static final List<String> EASINGS = List.of("linear", "ease", "ease_in", "ease_out", "ease_in_out", "back_out", "elastic_out", "bounce_out");

	private static final Map<String, Kind> KINDS = new HashMap<>();
	private static final Map<String, Set<String>> BY_TYPE = new HashMap<>();
	/** Keys that hold objects and are flattened into "group.key". */
	public static final Set<String> GROUPS = Set.of("transform", "parallax", "transition", "sounds");

	private static final Set<String> COMMON = new LinkedHashSet<>();
	private static final Set<String> CONTAINER = new LinkedHashSet<>();

	private static void kind(Kind kind, String... keys) {
		for (String k : keys) {
			KINDS.put(k, kind);
		}
	}

	static {
		kind(Kind.LENGTH, "x", "y", "width", "height", "gap", "padding", "size", "outlineWidth", "transform.z", "parallax.move", "perspective", "lineHeight", "radius");
		kind(Kind.NUMBER, "opacity", "order", "transform.rotateX", "transform.rotateY", "transform.rotateZ", "transform.scale",
				"transform.scaleX", "transform.scaleY", "transform.skewX", "transform.skewY", "transform.pivotX", "transform.pivotY",
				"parallax.tilt", "parallax.smoothing", "transition.duration", "fps", "frames", "sliceScale", "value", "count");
		kind(Kind.COLOR, "color", "color2", "tint", "outline", "background");
		kind(Kind.BOOL, "visible", "enabled", "shadow", "wrap", "pixelated", "readable");
		kind(Kind.TEXT, "text", "tooltip");
		kind(Kind.WORD, "anchor", "origin", "arrange", "align", "font", "src", "fit", "frameLayout", "direction", "gradient", "item",
				"transition.easing", "sounds.hover", "sounds.click", "layer", "image", "textAlign");

		COMMON.addAll(List.of("type", "anchor", "origin", "x", "y", "width", "height", "visible", "opacity", "order", "layer",
				"enabled", "transform", "parallax", "states", "transition", "sounds", "tooltip"));
		CONTAINER.addAll(List.of("elements", "arrange", "gap", "padding", "align", "background", "outline", "outlineWidth", "perspective"));

		type("text", "text", "color", "shadow", "size", "font", "textAlign", "wrap", "lineHeight", "readable");
		type("image", "src", "tint", "slice", "sliceScale", "fit", "frames", "fps", "frameLayout", "pixelated");
		type("rect", "color", "color2", "gradient", "outline", "outlineWidth");
		type("bar", "value", "color", "background", "direction", "image");
		type("item", "item", "count");
		type("group");
		type("button", "action");
		type("subwindow");
		type("part", "ports");
	}

	private static void type(String type, String... keys) {
		Set<String> set = new LinkedHashSet<>(COMMON);
		if (CONTAINERS.contains(type)) {
			set.addAll(CONTAINER);
		}
		set.addAll(List.of(keys));
		BY_TYPE.put(type, set);
	}

	private PropKinds() {
	}

	public static Kind kind(String flatKey) {
		return KINDS.get(flatKey);
	}

	public static Set<String> keysFor(String type) {
		return BY_TYPE.getOrDefault(type, COMMON);
	}

	public static boolean isContainer(String type) {
		return CONTAINERS.contains(type);
	}

	/** Keys allowed inside transform/parallax/transition/sounds. */
	public static Set<String> groupKeys(String group) {
		Set<String> out = new LinkedHashSet<>();
		for (String k : KINDS.keySet()) {
			if (k.startsWith(group + ".")) {
				out.add(k.substring(group.length() + 1));
			}
		}
		return out;
	}

	/** "did you mean 'color'?" for a near miss, else "". */
	public static String suggest(String wrong, Collection<String> known) {
		String best = null;
		int bestDistance = 3;
		for (String k : known) {
			int d = distance(wrong.toLowerCase(), k.toLowerCase());
			if (d < bestDistance) {
				bestDistance = d;
				best = k;
			}
		}
		return best == null ? "" : " (did you mean '" + best + "'?)";
	}

	static int distance(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
