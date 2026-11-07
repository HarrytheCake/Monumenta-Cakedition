package io.github.harrythecake.cakeui.edit;

import org.jetbrains.annotations.Nullable;

/**
 * One HUD port's edits from the HUD editor (personal edits: {@code config/cakeui/hud.json5}).
 * Every field is optional: null = the layout's (or vanilla's) value stays. The position moves the
 * whole port (the vanilla piece and its elements) as one outer transform; see {@link Placement}.
 */
public final class PortEdit {
	/** How the position is kept when the window or GUI scale changes; null = not moved. */
	@Nullable
	public Mode mode;
	/** Anchored: the screen anchor (0, 0.5 or 1 on each axis). Percent: the fraction of the free space. */
	public double ax;
	public double ay;
	/** Anchored: offset in GUI pixels from the anchor to the piece's same point. Fixed: the top-left corner. */
	public double x;
	public double y;
	@Nullable
	public Double scale;
	@Nullable
	public Float opacity;
	@Nullable
	public Boolean hide;
	/**
	 * An element the HUD editor added (key {@code +id}): its definition in CakeUI's element language
	 * (type, text, size, color, …), drawn at the edit's position. Null for ports and layout elements.
	 */
	@Nullable
	public com.google.gson.JsonObject element;

	public enum Mode {
		ANCHORED, PERCENT, FIXED;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		@Nullable
		public static Mode of(String id) {
			for (Mode m : values()) {
				if (m.id().equals(id)) {
					return m;
				}
			}
			return null;
		}
	}

	public PortEdit copy() {
		PortEdit e = new PortEdit();
		e.mode = mode;
		e.ax = ax;
		e.ay = ay;
		e.x = x;
		e.y = y;
		e.scale = scale;
		e.opacity = opacity;
		e.hide = hide;
		e.element = element == null ? null : element.deepCopy();
		return e;
	}

	/** Nothing set: the port is as if never edited. */
	public boolean isEmpty() {
		return mode == null && scale == null && opacity == null && hide == null && element == null;
	}

	public double scaleOr1() {
		return scale == null ? 1 : scale;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PortEdit e && mode == e.mode && ax == e.ax && ay == e.ay && x == e.x && y == e.y
				&& java.util.Objects.equals(scale, e.scale) && java.util.Objects.equals(opacity, e.opacity) && java.util.Objects.equals(hide, e.hide)
				&& java.util.Objects.equals(element, e.element);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(mode, ax, ay, x, y, scale, opacity, hide, element);
	}
}
