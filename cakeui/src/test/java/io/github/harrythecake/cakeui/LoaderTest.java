package io.github.harrythecake.cakeui;

import io.github.harrythecake.cakeui.expr.Env;
import io.github.harrythecake.cakeui.expr.Values;
import io.github.harrythecake.cakeui.model.GameValues;
import io.github.harrythecake.cakeui.model.Layout;
import io.github.harrythecake.cakeui.model.Loader;
import io.github.harrythecake.cakeui.model.Node;
import io.github.harrythecake.cakeui.model.Problems;
import io.github.harrythecake.cakeui.model.Scope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderTest {
	@TempDir
	Path dir;

	@BeforeAll
	static void values() {
		GameValues.register("player.health", () -> 14.0f);
		GameValues.register("mouse.x", () -> 0.5);
	}

	private void write(String path, String text) throws IOException {
		Path p = dir.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text);
	}

	private static String problems(Layout l) {
		StringBuilder sb = new StringBuilder();
		for (Problems.Problem p : l.problems.list()) {
			sb.append(p).append('\n');
		}
		return sb.toString();
	}

	private static boolean hasProblem(Layout l, String file, int line, String text) {
		return l.problems.list().stream().anyMatch(p -> p.file().equals(file) && p.line() == line && p.message().contains(text));
	}

	@Test
	void loadsAWholeLayout() throws IOException {
		write("window.json5", """
				{
				  format: 1,
				  design: [1920, 1080],
				  scale: "fill",
				}
				""");
		write("declaration.json5", """
				{
				  constants: { accent: "#ffb000", gap: 12, double_gap: "= gap * 2" },
				  variables: { hp: "= player.health / 20", greeting: "Hi {player.health}" },
				}
				""");
		write("lang/en_us.json5", "{ 'menu.play': 'Play' }");
		write("lang/zh_cn.json5", "{ 'menu.play': '开始' }");
		write("main_menu/declaration.json5", "{ constants: { gap: 4 }, variables: { open: false } }");
		write("main_menu/layout.json5", """
				{
				  ports: {
				    logo: { hide: true },
				    splash: { list: "splashes.txt", mixVanilla: false, color: "accent" },
				    buttons: { "menu.singleplayer": { x: 10, y: "50%", skin: { bg: { type: "rect", color: "#000" } } } },
				  },
				  elements: {
				    title: { type: "text", text: "{t:menu.play} {fmt(hp * 100, 0)}%", anchor: "top", y: "double_gap", size: 32 },
				    toggle: { type: "button", width: 200, height: 40, action: { toggle: "open" },
				              states: { hover: { transform: { scale: 1.05 } } } },
				  },
				}
				""");
		write("main_menu/splashes.txt", "First!\n# comment\n\nSecond\n");
		write("main_menu/subwindow_panel/layout.json5", """
				{ anchor: "right", width: "30%", perspective: 800, transform: { rotateY: "mouse.x * 8" },
				  elements: { label: { type: "text", text: "{gap}" } } }
				""");

		Loader loader = Loader.load(dir);
		Layout l = loader.layout();
		assertFalse(loader.fatal);
		assertTrue(l.problems.isEmpty(), problems(l));
		assertEquals(1920, l.designWidth);
		assertEquals(Layout.ScaleMode.FILL, l.scaleMode);
		assertEquals("开始", l.translate("zh_cn", "menu.play"));
		assertEquals("Play", l.translate("de_de", "menu.play"));

		Node menu = l.part("main_menu");
		assertNotNull(menu);
		assertEquals(List.of("First!", "Second"), menu.ports.get("splash").splashes);
		assertTrue(menu.ports.get("splash").customStyle);
		assertTrue(menu.ports.get("buttons").buttons.get("menu.singleplayer").customStyle);
		assertEquals(3, menu.children.size()); // title, toggle, subwindow panel

		Env env = new Env();
		env.translator = k -> l.translate("en_us", k);
		Node title = menu.children.get(0);
		assertEquals("Play 70%", title.prop("text").base.eval(env));
		// the part's own gap (4) hides the window's (12); double_gap was computed from the window's
		assertEquals(24.0, Values.num(title.prop("y").base.eval(env)));
		Node panel = menu.children.get(2);
		assertEquals("subwindow", panel.type);
		assertEquals("4", panel.children.get(0).prop("text").base.eval(env));
		assertEquals(4.0, Values.num(panel.prop("transform.rotateY").base.eval(env)));

		Node toggle = menu.children.get(1);
		assertTrue(toggle.interactive);
		assertEquals("set", toggle.action.kind());
		Scope.State open = toggle.action.assigns().get(0).target();
		assertEquals(false, open.get(env));
	}

	@Test
	void problemsNameFileAndLine() throws IOException {
		write("window.json5", "{ format: 1, desing: [1, 2] }");
		write("main_menu/layout.json5", """
				{
				  elements: {
				    a: { type: "txt" },
				    b: { type: "text", colour: "#fff" },
				    c: { type: "text", text: "{player.helth}" },
				    d: { type: "rect", anchor: "middle" },
				    e: { type: "button", action: { set: { secret: 1 } } },
				    f: { type: "image" },
				  },
				  ports: { splsh: {} },
				}
				""");
		write("main_menu/subwindow_side/declaration.json5", "{ variables: { secret: 1 } }");
		write("mainmenu/layout.json5", "{}");
		Layout l = Loader.load(dir).layout();
		String all = problems(l);
		assertTrue(hasProblem(l, "window.json5", 1, "did you mean 'design'"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 3, "did you mean 'text'"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 4, "did you mean 'color'"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 5, "did you mean 'player.health'"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 6, "can't be 'middle'"), all);
		// a sibling subwindow's variable is invisible to the part
		assertTrue(hasProblem(l, "main_menu/layout.json5", 7, "no variable 'secret'"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 8, "has no src"), all);
		assertTrue(hasProblem(l, "main_menu/layout.json5", 10, "did you mean 'splash'"), all);
		assertTrue(hasProblem(l, "mainmenu/", 0, "did you mean 'main_menu'"), all);
		// the good parts still load
		assertNotNull(l.part("main_menu"));
	}

	@Test
	void foldersSetOnlyTheirOwnVariables() throws IOException {
		write("window.json5", "{ format: 1 }");
		write("declaration.json5", "{ variables: { theme: 'dark' } }");
		write("main_menu/layout.json5", "{ elements: { b: { type: 'button', action: { set: { theme: 'light' } } } } }");
		write("main_menu/subwindow_s/declaration.json5", "{ variables: { mine: 0 } }");
		write("main_menu/subwindow_s/layout.json5", "{ elements: { b: { type: 'button', action: { set: { mine: 'mine + 1' } } } } }");
		Layout l = Loader.load(dir).layout();
		assertTrue(hasProblem(l, "main_menu/layout.json5", 1, "belongs to a parent folder"), problems(l));
		Node sub = l.part("main_menu").children.stream().filter(n -> n.type.equals("subwindow")).findFirst().orElseThrow();
		Node.Assign set = sub.children.get(0).action.assigns().get(0);
		Env env = new Env();
		set.target().set(set.value().eval(env));
		set.target().set(set.value().eval(env));
		assertEquals(2.0, set.target().get(env));
	}

	@Test
	void constantsCantUseGameValuesAndCyclesAreCaught() throws IOException {
		write("window.json5", "{ format: 1 }");
		write("declaration.json5", """
				{
				  constants: { bad: "= player.health" },
				  variables: { a: "= b + 1", b: "= a + 1" },
				}
				""");
		write("main_menu/layout.json5", "{ elements: { t: { type: 'text', text: '{a}' } } }");
		Layout l = Loader.load(dir).layout();
		assertTrue(hasProblem(l, "declaration.json5", 2, "declare it under variables"), problems(l));
		assertNull(l.scope.own("bad"));
		Node t = l.part("main_menu").children.get(0);
		Env env = new Env();
		boolean threw = false;
		try {
			t.prop("text").base.eval(env);
		} catch (IllegalStateException e) {
			threw = e.getMessage().contains("depends on itself");
		}
		assertTrue(threw);
	}

	@Test
	void tooNewOrMissingWindowIsFatal() throws IOException {
		assertTrue(Loader.load(dir).fatal);
		write("window.json5", "{ format: 2 }");
		Loader loader = Loader.load(dir);
		assertTrue(loader.fatal);
		assertTrue(hasProblem(loader.layout(), "window.json5", 1, "newer CakeUI"), problems(loader.layout()));
	}
}
