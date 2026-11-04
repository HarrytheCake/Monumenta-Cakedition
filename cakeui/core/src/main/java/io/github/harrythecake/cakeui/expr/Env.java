package io.github.harrythecake.cakeui.expr;

import java.util.function.UnaryOperator;

/**
 * What an expression can see while it runs: the frame, the axis a length is measured on, the
 * size of the parent box, the design scale and the element being drawn. One instance is reused
 * on the render thread; callers set the fields before evaluating.
 */
public final class Env {
	public static final int AXIS_NONE = -1;
	public static final int AXIS_X = 0;
	public static final int AXIS_Y = 1;

	/** Increases once per frame; computed variables cache their value per frame. */
	public long frame;
	public int axis = AXIS_NONE;
	public double parentWidth;
	public double parentHeight;
	/** GUI pixels per design pixel, horizontally and vertically. */
	public double rx = 1;
	public double ry = 1;
	/** The element being evaluated, or null. */
	public SelfValues self;
	/** Layout translation lookup for `{t:key}` and `t("key")`. */
	public UnaryOperator<String> translator = key -> key;

	/** GUI pixels of `value` in the given unit on the current axis. */
	public double unit(Unit unit, double value) {
		return switch (unit) {
			case PX -> value;
			case R -> value * (axis == AXIS_Y ? ry : rx);
			case PERCENT -> switch (axis) {
				case AXIS_X -> value / 100.0 * parentWidth;
				case AXIS_Y -> value / 100.0 * parentHeight;
				default -> value / 100.0;
			};
		};
	}

	/** A plain number meant as a length is in design pixels. */
	public double plainLength(double value) {
		return unit(Unit.R, value);
	}

	public enum Unit {
		PX, R, PERCENT
	}

	/** The values an element exposes as `self.*`. */
	public interface SelfValues {
		Object get(String name);
	}
}
