package io.github.harrythecake.cakeui.expr;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** The functions an expression can call. All are pure except t(), which reads the layout's language files. */
public final class Functions {
	private interface Impl {
		Object apply(Object[] args, Env env);
	}

	private record Fn(int minArgs, int maxArgs, boolean pure, Impl impl) {
	}

	private static final Map<String, Fn> FUNCTIONS = new HashMap<>();
	private static final Env FOLD_ENV = new Env();

	private Functions() {
	}

	private static void def(String name, int min, int max, Impl impl) {
		FUNCTIONS.put(name, new Fn(min, max, true, impl));
	}

	private static double n(Object[] a, int i) {
		return Values.num(a[i]);
	}

	/** Keeps a length a length: f(Px) → Px. */
	private static Object sameKind(Object in, double v) {
		return in instanceof Px ? new Px(v) : (Object) v;
	}

	static {
		def("sin", 1, 1, (a, e) -> Math.sin(n(a, 0)));
		def("cos", 1, 1, (a, e) -> Math.cos(n(a, 0)));
		def("tan", 1, 1, (a, e) -> Math.tan(n(a, 0)));
		def("rad", 1, 1, (a, e) -> Math.toRadians(n(a, 0)));
		def("deg", 1, 1, (a, e) -> Math.toDegrees(n(a, 0)));
		def("abs", 1, 1, (a, e) -> sameKind(a[0], Math.abs(n(a, 0))));
		def("sign", 1, 1, (a, e) -> Math.signum(n(a, 0)));
		def("sqrt", 1, 1, (a, e) -> Math.sqrt(Math.max(0, n(a, 0))));
		def("pow", 2, 2, (a, e) -> Math.pow(n(a, 0), n(a, 1)));
		def("floor", 1, 1, (a, e) -> sameKind(a[0], Math.floor(n(a, 0))));
		def("ceil", 1, 1, (a, e) -> sameKind(a[0], Math.ceil(n(a, 0))));
		def("round", 1, 2, (a, e) -> {
			double scale = a.length > 1 ? Math.pow(10, Math.round(n(a, 1))) : 1;
			return sameKind(a[0], Math.round(n(a, 0) * scale) / scale);
		});
		def("mod", 2, 2, (a, e) -> {
			double m = n(a, 1);
			if (m == 0) {
				return 0.0;
			}
			double r = n(a, 0) % m;
			return r < 0 ? r + Math.abs(m) : r;
		});
		def("min", 1, 16, (a, e) -> {
			int best = 0;
			for (int i = 1; i < a.length; i++) {
				if (n(a, i) < n(a, best)) {
					best = i;
				}
			}
			return a[best];
		});
		def("max", 1, 16, (a, e) -> {
			int best = 0;
			for (int i = 1; i < a.length; i++) {
				if (n(a, i) > n(a, best)) {
					best = i;
				}
			}
			return a[best];
		});
		def("clamp", 3, 3, (a, e) -> sameKind(a[0], Math.max(n(a, 1), Math.min(n(a, 2), n(a, 0)))));
		def("lerp", 3, 3, (a, e) -> sameKind(a[0], n(a, 0) + (n(a, 1) - n(a, 0)) * n(a, 2)));
		def("smoothstep", 3, 3, (a, e) -> {
			double edge0 = n(a, 0);
			double edge1 = n(a, 1);
			double t = edge1 == edge0 ? 0 : Math.max(0, Math.min(1, (n(a, 2) - edge0) / (edge1 - edge0)));
			return t * t * (3 - 2 * t);
		});
		def("if", 3, 3, (a, e) -> Values.bool(a[0]) ? a[1] : a[2]);
		def("fmt", 2, 2, (a, e) -> Values.format(n(a, 0), (int) Math.round(n(a, 1))));
		def("str", 1, 1, (a, e) -> Values.str(a[0]));
		def("num", 1, 1, (a, e) -> Values.num(a[0]));
		def("len", 1, 1, (a, e) -> (double) Values.str(a[0]).length());
		def("upper", 1, 1, (a, e) -> Values.str(a[0]).toUpperCase(Locale.ROOT));
		def("lower", 1, 1, (a, e) -> Values.str(a[0]).toLowerCase(Locale.ROOT));
		def("contains", 2, 2, (a, e) -> Values.str(a[0]).contains(Values.str(a[1])));
		def("rgb", 3, 3, (a, e) -> argb(255, n(a, 0), n(a, 1), n(a, 2)));
		def("rgba", 4, 4, (a, e) -> argb(n(a, 3) * 255, n(a, 0), n(a, 1), n(a, 2)));
		def("alpha", 2, 2, (a, e) -> {
			int c = Values.color(a[0]);
			int alpha = (int) Math.round(Math.max(0, Math.min(1, n(a, 1))) * 255);
			return (double) (((long) alpha << 24) | (c & 0xFFFFFFL));
		});
		def("mix", 3, 3, (a, e) -> (double) (Values.mixColor(Values.color(a[0]), Values.color(a[1]), n(a, 2)) & 0xFFFFFFFFL));
		FUNCTIONS.put("t", new Fn(1, 1, false, (a, e) -> e.translator.apply(Values.str(a[0]))));
	}

	private static double argb(double a, double r, double g, double b) {
		long ai = clampByte(a);
		long ri = clampByte(r);
		long gi = clampByte(g);
		long bi = clampByte(b);
		return (double) ((ai << 24) | (ri << 16) | (gi << 8) | bi);
	}

	private static long clampByte(double v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	/** Builds a call; an unknown name or a wrong number of arguments is an error. */
	static Expr call(String name, List<Expr> args) {
		Fn fn = FUNCTIONS.get(name);
		if (fn == null) {
			throw new ExprException("unknown function '" + name + "()' (known: " + String.join(", ", new TreeSet<>(FUNCTIONS.keySet())) + ")", 1);
		}
		if (args.size() < fn.minArgs || args.size() > fn.maxArgs) {
			String want = fn.minArgs == fn.maxArgs ? String.valueOf(fn.minArgs) : fn.minArgs + " to " + fn.maxArgs;
			throw new ExprException(name + "() takes " + want + " argument(s), got " + args.size(), 1);
		}
		Expr[] argv = args.toArray(new Expr[0]);
		Expr call = env -> {
			Object[] values = new Object[argv.length];
			for (int i = 0; i < argv.length; i++) {
				values[i] = argv[i].eval(env);
			}
			return fn.impl.apply(values, env);
		};
		boolean constant = fn.pure;
		for (Expr arg : argv) {
			constant &= arg.isConstant();
		}
		return constant ? Expr.constant(call.eval(FOLD_ENV)) : call;
	}
}
