package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Expr;
import io.github.harrythecake.cakeui.expr.ExprException;
import io.github.harrythecake.cakeui.expr.Parser;
import io.github.harrythecake.cakeui.expr.Px;
import io.github.harrythecake.cakeui.expr.Template;
import io.github.harrythecake.cakeui.expr.Values;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprTest {
	private static final Map<String, Object> NAMES = Map.of("hp", 15.0, "maxHp", 20.0, "name", "qwertfox", "on", true);
	private static final Parser.Resolver R = n -> NAMES.containsKey(n) ? Expr.constant(NAMES.get(n)) : null;

	private static Object eval(String s, Env env) {
		return Parser.compile(s, R).eval(env);
	}

	private static Object eval(String s) {
		return eval(s, new Env());
	}

	@Test
	void arithmeticAndLogic() {
		assertEquals(7.0, eval("1 + 2 * 3"));
		assertEquals(9.0, eval("(1 + 2) * 3"));
		assertEquals(0.75, eval("hp / maxHp"));
		assertEquals(-3.0, eval("-3"));
		assertEquals(true, eval("hp > 10 && on"));
		assertEquals(false, eval("!on || hp < 0"));
		assertEquals("ok", eval("hp >= 15 ? 'ok' : 'low'"));
		assertEquals("a1", eval("'a' + 1"));
		assertEquals(true, eval("name == \"qwertfox\""));
		assertEquals(0.0, eval("1 / 0"));
	}

	@Test
	void functions() {
		assertEquals(2.0, eval("mod(5, 3)"));
		assertEquals(1.0, eval("mod(-2, 3)"));
		assertEquals(5.0, eval("clamp(9, 0, 5)"));
		assertEquals(15.0, eval("lerp(10, 20, 0.5)"));
		assertEquals("75.0", eval("fmt(hp / maxHp * 100, 1)"));
		assertEquals(3.14, eval("round(pi, 2)"));
		assertEquals("b", eval("if(on, 'b', 'c')"));
		assertEquals((double) 0xFFFF0000L, eval("rgb(255, 0, 0)"));
		assertEquals((double) 0x80FFFFFFL, eval("alpha(#ffffff, 0.5)"));
	}

	@Test
	void constantsAreFolded() {
		assertTrue(Parser.compile("1 + 2 * clamp(5, 0, 3)", R).isConstant());
		assertTrue(Parser.compile("hp * 2", R).isConstant());
		assertFalse(Parser.compile("10r + 1", R).isConstant());
	}

	@Test
	void unitsAndLengths() {
		Env env = new Env();
		env.parentWidth = 400;
		env.parentHeight = 200;
		env.rx = 0.5;
		env.ry = 0.25;
		env.axis = Env.AXIS_X;
		assertEquals(new Px(200), eval("50%", env));
		assertEquals(new Px(50), eval("100r", env));
		assertEquals(new Px(20), eval("20px", env));
		// a plain number mixed with a length is design pixels
		assertEquals(new Px(195), eval("50% - 10", env));
		assertEquals(new Px(100), eval("50% / 2", env));
		assertEquals(2.0, eval("50% / 100px", env));
		env.axis = Env.AXIS_Y;
		assertEquals(new Px(100), eval("50%", env));
		assertEquals(new Px(25), eval("100r", env));
		// plain results of a length property are design pixels
		assertEquals(4.0, Values.length(16.0, env));
		assertEquals(30.0, Values.length("30px", env));
	}

	@Test
	void colours() {
		assertEquals(0xFFFFB000, (int) Values.parseColor("#ffb000"));
		assertEquals(0x80FFB000, (int) Values.parseColor("#ffb00080"));
		assertEquals(0xFFFF0000, (int) Values.parseColor("#f00"));
		assertEquals(0xFFFFB000, Values.color(eval("#ffb000")));
		assertEquals(null, Values.parseColor("#12"));
	}

	@Test
	void templates() {
		Env env = new Env();
		env.translator = k -> k.equals("menu.play") ? "Play" : k;
		assertEquals("HP 15/20", Template.compile("HP {hp}/{maxHp}", R).eval(env));
		assertEquals("{literal}", Template.compile("{{literal}}", R).eval(env));
		assertEquals("Play!", Template.compile("{t:menu.play}!", R).eval(env));
		assertEquals(0.75, Template.compile("= hp / maxHp", R).eval(env));
		assertEquals("a}b", Template.compile("{'a}b'}", R).eval(env));
	}

	@Test
	void errorsAreClear() {
		ExprException e = assertThrows(ExprException.class, () -> Parser.compile("hp + nope", R));
		assertTrue(e.getMessage().contains("unknown name 'nope'"), e.getMessage());
		assertEquals(6, e.column);
		e = assertThrows(ExprException.class, () -> Parser.compile("hp % 2", R));
		assertTrue(e.getMessage().contains("mod("), e.getMessage());
		e = assertThrows(ExprException.class, () -> Parser.compile("clamp(1)", R));
		assertTrue(e.getMessage().contains("takes 3"), e.getMessage());
		e = assertThrows(ExprException.class, () -> Parser.compile("12em", R));
		assertTrue(e.getMessage().contains("unit"), e.getMessage());
		e = assertThrows(ExprException.class, () -> Parser.compile("(1 + 2", R));
		assertTrue(e.getMessage().contains(")"), e.getMessage());
		e = assertThrows(ExprException.class, () -> Template.compile("HP {hp", R));
		assertTrue(e.getMessage().contains("}"), e.getMessage());
	}
}
