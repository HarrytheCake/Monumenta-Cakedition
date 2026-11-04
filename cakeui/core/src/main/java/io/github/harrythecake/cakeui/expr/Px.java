package io.github.harrythecake.cakeui.expr;

/** A length that is already resolved to GUI pixels (from a `px`, `r` or `%` literal). */
public record Px(double value) {
	@Override
	public String toString() {
		return Values.format(value);
	}
}
