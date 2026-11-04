package io.github.harrythecake.cakeui.expr;

/** A problem in an expression; `column` counts from 1 within the expression text. */
public final class ExprException extends RuntimeException {
	public final int column;

	public ExprException(String message, int column) {
		super(message);
		this.column = column;
	}
}
