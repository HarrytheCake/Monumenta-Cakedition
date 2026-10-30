package io.github.harrythecake.cakeui.model;

import io.github.harrythecake.cakeui.expr.Expr;

/** One property of a node: its base value and optional values for the hover, pressed and disabled states. */
public final class Prop {
	public final String key;
	public final PropKinds.Kind kind;
	public Expr base;
	public Expr hover;
	public Expr pressed;
	public Expr disabled;
	public final int line;

	public Prop(String key, PropKinds.Kind kind, Expr base, int line) {
		this.key = key;
		this.kind = kind;
		this.base = base;
		this.line = line;
	}

	public boolean hasStates() {
		return hover != null || pressed != null || disabled != null;
	}

	/** The expression for the element's current state: disabled, else pressed, else hover, else base. */
	public Expr pick(boolean isHovered, boolean isPressed, boolean isDisabled) {
		if (isDisabled && disabled != null) {
			return disabled;
		}
		if (isPressed && pressed != null) {
			return pressed;
		}
		if ((isHovered || isPressed) && hover != null) {
			return hover;
		}
		return base;
	}
}
