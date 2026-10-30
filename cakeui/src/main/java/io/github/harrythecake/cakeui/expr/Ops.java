package io.github.harrythecake.cakeui.expr;

/** The operators of the expression language. */
final class Ops {
	private static final Env FOLD_ENV = new Env();

	private Ops() {
	}

	/** Computes an operator once when all its inputs are constant. */
	static Expr fold(Expr e) {
		boolean constant;
		if (e instanceof Ternary t) {
			constant = t.cond.isConstant() && t.a.isConstant() && t.b.isConstant();
		} else if (e instanceof Or o) {
			constant = o.a.isConstant() && o.b.isConstant();
		} else if (e instanceof And o) {
			constant = o.a.isConstant() && o.b.isConstant();
		} else if (e instanceof Eq o) {
			constant = o.a.isConstant() && o.b.isConstant();
		} else if (e instanceof Compare o) {
			constant = o.a.isConstant() && o.b.isConstant();
		} else if (e instanceof Arith o) {
			constant = o.a.isConstant() && o.b.isConstant();
		} else if (e instanceof Not o) {
			constant = o.a.isConstant();
		} else if (e instanceof Neg o) {
			constant = o.a.isConstant();
		} else {
			constant = false;
		}
		return constant ? Expr.constant(e.eval(FOLD_ENV)) : e;
	}

	record UnitLiteral(Env.Unit unit, double value) implements Expr {
		@Override
		public Object eval(Env env) {
			return new Px(env.unit(unit, value));
		}
	}

	record Ternary(Expr cond, Expr a, Expr b) implements Expr {
		@Override
		public Object eval(Env env) {
			return Values.bool(cond.eval(env)) ? a.eval(env) : b.eval(env);
		}
	}

	record Or(Expr a, Expr b) implements Expr {
		@Override
		public Object eval(Env env) {
			return Values.bool(a.eval(env)) || Values.bool(b.eval(env));
		}
	}

	record And(Expr a, Expr b) implements Expr {
		@Override
		public Object eval(Env env) {
			return Values.bool(a.eval(env)) && Values.bool(b.eval(env));
		}
	}

	record Not(Expr a) implements Expr {
		@Override
		public Object eval(Env env) {
			return !Values.bool(a.eval(env));
		}
	}

	record Neg(Expr a) implements Expr {
		@Override
		public Object eval(Env env) {
			Object v = a.eval(env);
			return v instanceof Px p ? new Px(-p.value()) : (Object) (-Values.num(v));
		}
	}

	record Eq(Expr a, Expr b, boolean negate) implements Expr {
		@Override
		public Object eval(Env env) {
			return Values.equal(a.eval(env), b.eval(env)) != negate;
		}
	}

	record Compare(Expr a, Expr b, int op) implements Expr {
		static final int LT = 0, LE = 1, GT = 2, GE = 3;

		@Override
		public Object eval(Env env) {
			double x = Values.num(a.eval(env));
			double y = Values.num(b.eval(env));
			return switch (op) {
				case LT -> x < y;
				case LE -> x <= y;
				case GT -> x > y;
				default -> x >= y;
			};
		}
	}

	/**
	 * + - * /. Text joins with +. A length (Px) mixed with a plain number in + or - treats the
	 * number as design pixels, so "50% - 10" means 50% minus 10r.
	 */
	record Arith(Expr a, Expr b, char op) implements Expr {
		@Override
		public Object eval(Env env) {
			Object x = a.eval(env);
			Object y = b.eval(env);
			if (op == '+' && (x instanceof String || y instanceof String)) {
				return Values.str(x) + Values.str(y);
			}
			boolean px = x instanceof Px;
			boolean py = y instanceof Px;
			if (!px && !py) {
				double l = Values.num(x);
				double r = Values.num(y);
				return switch (op) {
					case '+' -> l + r;
					case '-' -> l - r;
					case '*' -> l * r;
					default -> r == 0 ? 0.0 : l / r;
				};
			}
			switch (op) {
				case '+', '-' -> {
					double l = px ? ((Px) x).value() : env.plainLength(Values.num(x));
					double r = py ? ((Px) y).value() : env.plainLength(Values.num(y));
					return new Px(op == '+' ? l + r : l - r);
				}
				case '*' -> {
					return new Px(Values.num(x) * Values.num(y));
				}
				default -> {
					double r = Values.num(y);
					if (r == 0) {
						return px && !py ? new Px(0) : (Object) 0.0;
					}
					double q = Values.num(x) / r;
					return px && !py ? new Px(q) : (Object) q;
				}
			}
		}
	}
}
