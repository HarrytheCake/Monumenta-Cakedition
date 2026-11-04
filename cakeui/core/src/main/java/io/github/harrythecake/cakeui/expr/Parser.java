package io.github.harrythecake.cakeui.expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles CakeUI's expression language into an {@link Expr} tree.
 *
 * <pre>
 * numbers 12, 1.5, with units 100r 20px 50%   colours #ffb000   text "a" or 'a'
 * names: player.health, mouse.x, self.hovered, declared names   true false pi
 * a ? b : c   ||  &&  == != &lt; &lt;= &gt; &gt;=   + -   * /   !x  -x   f(a, b)
 * </pre>
 *
 * There is no modulo operator (use mod(a, b)) so that 50% is always a unit.
 * Names are bound while compiling: an unknown name is an error here, not at draw time.
 */
public final class Parser {
	/** Looks a name up while compiling; returns null when nothing has that name. */
	public interface Resolver {
		Expr resolve(String name);
	}

	private final String src;
	private final Resolver resolver;
	private int pos;

	private Parser(String src, Resolver resolver) {
		this.src = src;
		this.resolver = resolver;
	}

	public static Expr compile(String src, Resolver resolver) {
		Parser p = new Parser(src, resolver);
		p.space();
		if (p.pos >= src.length()) {
			throw new ExprException("empty expression", 1);
		}
		Expr e = p.ternary();
		p.space();
		if (p.pos < src.length()) {
			throw p.error("unexpected '" + src.charAt(p.pos) + "'");
		}
		return e;
	}

	private ExprException error(String message) {
		return new ExprException(message, pos + 1);
	}

	private void space() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}

	private boolean eat(String op) {
		space();
		if (src.startsWith(op, pos)) {
			pos += op.length();
			return true;
		}
		return false;
	}

	private boolean peek(String op) {
		space();
		return src.startsWith(op, pos);
	}

	private Expr ternary() {
		Expr cond = or();
		if (eat("?")) {
			Expr a = ternary();
			if (!eat(":")) {
				throw error("expected ':' in 'a ? b : c'");
			}
			Expr b = ternary();
			return Ops.fold(new Ops.Ternary(cond, a, b));
		}
		return cond;
	}

	private Expr or() {
		Expr left = and();
		while (eat("||")) {
			left = Ops.fold(new Ops.Or(left, and()));
		}
		return left;
	}

	private Expr and() {
		Expr left = equality();
		while (eat("&&")) {
			left = Ops.fold(new Ops.And(left, equality()));
		}
		return left;
	}

	private Expr equality() {
		Expr left = comparison();
		while (true) {
			if (eat("==")) {
				left = Ops.fold(new Ops.Eq(left, comparison(), false));
			} else if (eat("!=")) {
				left = Ops.fold(new Ops.Eq(left, comparison(), true));
			} else {
				return left;
			}
		}
	}

	private Expr comparison() {
		Expr left = additive();
		while (true) {
			if (eat("<=")) {
				left = Ops.fold(new Ops.Compare(left, additive(), Ops.Compare.LE));
			} else if (eat(">=")) {
				left = Ops.fold(new Ops.Compare(left, additive(), Ops.Compare.GE));
			} else if (eat("<")) {
				left = Ops.fold(new Ops.Compare(left, additive(), Ops.Compare.LT));
			} else if (eat(">")) {
				left = Ops.fold(new Ops.Compare(left, additive(), Ops.Compare.GT));
			} else {
				return left;
			}
		}
	}

	private Expr additive() {
		Expr left = multiplicative();
		while (true) {
			if (eat("+")) {
				left = Ops.fold(new Ops.Arith(left, multiplicative(), '+'));
			} else if (peek("-")) {
				pos++;
				left = Ops.fold(new Ops.Arith(left, multiplicative(), '-'));
			} else {
				return left;
			}
		}
	}

	private Expr multiplicative() {
		Expr left = unary();
		while (true) {
			if (eat("*")) {
				left = Ops.fold(new Ops.Arith(left, unary(), '*'));
			} else if (eat("/")) {
				left = Ops.fold(new Ops.Arith(left, unary(), '/'));
			} else if (peek("%")) {
				throw error("'%' is only a unit (50%); use mod(a, b) for the remainder");
			} else {
				return left;
			}
		}
	}

	private Expr unary() {
		if (eat("!")) {
			return Ops.fold(new Ops.Not(unary()));
		}
		if (eat("-")) {
			return Ops.fold(new Ops.Neg(unary()));
		}
		if (eat("+")) {
			return unary();
		}
		return primary();
	}

	private Expr primary() {
		space();
		if (pos >= src.length()) {
			throw error("expression ends too early");
		}
		char c = src.charAt(pos);
		if (c == '(') {
			pos++;
			Expr e = ternary();
			if (!eat(")")) {
				throw error("missing ')'");
			}
			return e;
		}
		if (c == '"' || c == '\'') {
			return Expr.constant(string());
		}
		if (c == '#') {
			int start = pos++;
			while (pos < src.length() && Character.digit(src.charAt(pos), 16) >= 0) {
				pos++;
			}
			Integer color = Values.parseColor(src.substring(start, pos));
			if (color == null) {
				pos = start;
				throw error("bad colour '" + src.substring(start, Math.min(src.length(), start + 10)) + "' (use #RGB, #RRGGBB or #RRGGBBAA)");
			}
			return Expr.constant((double) (color & 0xFFFFFFFFL));
		}
		if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
			return number();
		}
		if (Character.isLetter(c) || c == '_') {
			int start = pos;
			String name = name();
			if (peek("(")) {
				pos++;
				return call(name, start);
			}
			switch (name) {
				case "true":
					return Expr.constant(Boolean.TRUE);
				case "false":
					return Expr.constant(Boolean.FALSE);
				case "pi":
					return Expr.constant(Math.PI);
				default:
					break;
			}
			Expr e = resolver.resolve(name);
			if (e == null) {
				pos = start;
				throw error("unknown name '" + name + "'");
			}
			return e;
		}
		throw error("unexpected '" + c + "'");
	}

	private Expr call(String name, int start) {
		List<Expr> args = new ArrayList<>();
		if (!eat(")")) {
			do {
				args.add(ternary());
			} while (eat(","));
			if (!eat(")")) {
				throw error("missing ')' after the arguments of " + name + "()");
			}
		}
		try {
			return Functions.call(name, args);
		} catch (ExprException e) {
			throw new ExprException(e.getMessage(), start + 1);
		}
	}

	private String name() {
		int start = pos;
		while (pos < src.length()) {
			char c = src.charAt(pos);
			if (Character.isLetterOrDigit(c) || c == '_') {
				pos++;
			} else if (c == '.' && pos + 1 < src.length() && (Character.isLetter(src.charAt(pos + 1)) || src.charAt(pos + 1) == '_')) {
				pos++;
			} else {
				break;
			}
		}
		return src.substring(start, pos);
	}

	private Expr number() {
		int start = pos;
		while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
			pos++;
		}
		if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')
				&& pos + 1 < src.length() && (Character.isDigit(src.charAt(pos + 1)) || src.charAt(pos + 1) == '-' || src.charAt(pos + 1) == '+')) {
			pos += 2;
			while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
				pos++;
			}
		}
		double value;
		try {
			value = Double.parseDouble(src.substring(start, pos));
		} catch (NumberFormatException e) {
			pos = start;
			throw error("bad number");
		}
		if (src.startsWith("px", pos) && !identAfter(pos + 2)) {
			pos += 2;
			return new Ops.UnitLiteral(Env.Unit.PX, value);
		}
		if (src.startsWith("r", pos) && !identAfter(pos + 1)) {
			pos += 1;
			return new Ops.UnitLiteral(Env.Unit.R, value);
		}
		if (src.startsWith("%", pos)) {
			pos += 1;
			return new Ops.UnitLiteral(Env.Unit.PERCENT, value);
		}
		if (pos < src.length() && Character.isLetter(src.charAt(pos))) {
			throw error("unknown unit after " + Values.format(value) + " (units: r, px, %)");
		}
		return Expr.constant(value);
	}

	private boolean identAfter(int i) {
		return i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_');
	}

	private String string() {
		char quote = src.charAt(pos++);
		StringBuilder sb = new StringBuilder();
		while (pos < src.length()) {
			char c = src.charAt(pos++);
			if (c == quote) {
				return sb.toString();
			}
			if (c == '\\' && pos < src.length()) {
				char e = src.charAt(pos++);
				sb.append(e == 'n' ? '\n' : e);
			} else {
				sb.append(c);
			}
		}
		throw error("unclosed text (missing " + quote + ")");
	}
}
