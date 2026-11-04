package io.github.harrythecake.cakeui.expr;

/**
 * A compiled expression. Values are {@link Double}, {@link String}, {@link Boolean} or {@link Px}
 * (a length already in GUI pixels).
 */
public interface Expr {
	Object eval(Env env);

	/** True when the value never changes, so it can be computed once. */
	default boolean isConstant() {
		return false;
	}

	static Expr constant(Object value) {
		return new Const(value);
	}

	record Const(Object value) implements Expr {
		@Override
		public Object eval(Env env) {
			return value;
		}

		@Override
		public boolean isConstant() {
			return true;
		}
	}
}
