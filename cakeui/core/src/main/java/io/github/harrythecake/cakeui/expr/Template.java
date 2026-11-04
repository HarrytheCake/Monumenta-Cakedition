package io.github.harrythecake.cakeui.expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Text with expressions in braces: "HP {player.health}/{player.maxHealth}". {t:key} looks the key
 * up in the layout's language files; {{ and }} write a literal brace. A string starting with "="
 * is one whole expression instead ("= hp > 10 ? 'ok' : 'low'").
 */
public final class Template {
	private Template() {
	}

	public static Expr compile(String text, Parser.Resolver resolver) {
		if (text.startsWith("=")) {
			try {
				return Parser.compile(text.substring(1), resolver);
			} catch (ExprException e) {
				throw new ExprException(e.getMessage(), e.column + 1);
			}
		}
		List<Expr> parts = new ArrayList<>();
		StringBuilder literal = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '{' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
				literal.append('{');
				i += 2;
			} else if (c == '}' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
				literal.append('}');
				i += 2;
			} else if (c == '{') {
				int close = findClose(text, i + 1);
				if (close < 0) {
					throw new ExprException("'{' without a matching '}' (write {{ for a literal brace)", i + 1);
				}
				if (literal.length() > 0) {
					parts.add(Expr.constant(literal.toString()));
					literal.setLength(0);
				}
				String inner = text.substring(i + 1, close);
				if (inner.startsWith("t:")) {
					String key = inner.substring(2).trim();
					parts.add(env -> env.translator.apply(key));
				} else {
					try {
						parts.add(Parser.compile(inner, resolver));
					} catch (ExprException e) {
						throw new ExprException(e.getMessage(), i + 1 + e.column);
					}
				}
				i = close + 1;
			} else {
				literal.append(c);
				i++;
			}
		}
		if (literal.length() > 0 || parts.isEmpty()) {
			parts.add(Expr.constant(literal.toString()));
		}
		if (parts.size() == 1 && parts.get(0).isConstant()) {
			return Expr.constant(Values.str(parts.get(0).eval(null)));
		}
		Expr[] array = parts.toArray(new Expr[0]);
		return env -> {
			StringBuilder sb = new StringBuilder();
			for (Expr part : array) {
				sb.append(Values.str(part.eval(env)));
			}
			return sb.toString();
		};
	}

	/** The '}' closing an expression that starts at `from`, skipping quoted text inside it. */
	private static int findClose(String text, int from) {
		char quote = 0;
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '}') {
				return i;
			}
		}
		return -1;
	}
}
