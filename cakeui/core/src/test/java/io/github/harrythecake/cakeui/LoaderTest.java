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
		// (the real list lives in McValues, a Minecraft class; these stand in for the ones hudPorts uses)
		GameValues.register("player.y", () -> 64.0);
		GameValues.register("player.xp", () -> 0.5);
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

	@Test
	void pauseIsABuiltinSubwindowWithPorts() throws IOException {
		write("window.json5", "{ format: 1, design: [640, 360] }");
		write("hud/declaration.json5", "{ constants: { accent: '#ffb000' } }");
		write("hud/subwindow_pause/declaration.json5", "{ variables: { open: false } }");
		write("hud/subwindow_pause/layout.json5", """
				{
				  ports: {
				    background: { hide: true, elements: { dim: { type: "rect", color: "#80000000" } } },
				    buttons: { "menu.returnToGame": { y: 40, skin: { bg: { type: "rect", color: "accent" } } } },
				    logo: { hide: true },
				  },
				  elements: { panel: { type: "rect", color: "accent", width: 200, height: 100, anchor: "center" } },
				}
				""");
		write("hud/subwindow_pause/subwindow_side/layout.json5", "{ elements: { t: { type: 'text', text: '{open}' } } }");
		write("hud/subwindow_compass/layout.json5", "{ elements: { t: { type: 'text', text: 'N' } } }");
		Layout l = Loader.load(dir).layout();

		Node hud = l.part("hud");
		Node pause = hud.builtins.get("pause");
		assertNotNull(pause, problems(l));
		assertEquals(1, hud.children.size(), "the HUD draws its own subwindows, never the pause menu");
		assertEquals("compass", hud.children.get(0).name);
		assertTrue(pause.ports.get("background").has("hide"));
		assertEquals(1, pause.ports.get("buttons").buttons.size());
		assertEquals(2, pause.children.size(), "panel + subwindow_side");
		assertTrue(hasProblem(l, "hud/subwindow_pause/layout.json5", 5, "unknown port 'logo' in hud/pause"), problems(l));
		assertEquals(1, l.problems.list().size(), problems(l));
	}

	@Test
	void windowTitleIconAndBorder() throws IOException {
		write("window.json5", """
				{
				  format: 1,
				  title: "My pack {accent}",
				  icon: ["icon_16.png", "missing.png"],
				  border: { dark: true, color: "accent", glow: 1 },
				}
				""");
		write("declaration.json5", "{ constants: { accent: '#ffb000' } }");
		write("assets/icon_16.png", "not checked until it is set");
		Layout l = Loader.load(dir).layout();

		assertTrue(l.window.has("title"), problems(l));
		assertTrue(l.window.has("border.dark"));
		assertTrue(l.window.has("border.color"), "border colours can use the root declaration");
		assertEquals(1, l.icons.size());
		assertTrue(hasProblem(l, "window.json5", 4, "icon not found: assets/missing.png"), problems(l));
		assertTrue(hasProblem(l, "window.json5", 5, "'glow' isn't a border setting"), problems(l));
		assertEquals(2, l.problems.list().size(), problems(l));
	}

	@Test
	void hudPorts() throws IOException {
		write("window.json5", "{ format: 1, design: [640, 360] }");
		write("hud/layout.json5", """
				{
				  ports: {
				    hotbar: { y: -4, opacity: 0.8 },
				    experience: { hide: true, elements: { xp: { type: "bar", value: "player.xp" } } },
				    title: { scale: 0.5, text: "nope" },
				    chat: { hide: true },
				  },
				  elements: { coords: { type: "text", text: "{fmt(player.y, 0)}" } },
				}
				""");
		Layout l = Loader.load(dir).layout();
		Node hud = l.part("hud");
		assertEquals(3, hud.ports.size(), problems(l));
		assertEquals(1, hud.ports.get("experience").children.size());
		assertTrue(hasProblem(l, "hud/layout.json5", 5, "'text' isn't a setting of the title port"), problems(l));
		assertTrue(hasProblem(l, "hud/layout.json5", 6, "unknown port 'chat' in hud"), problems(l));
		assertEquals(2, l.problems.list().size(), problems(l));
	}

	@Test
	void containerScreensAreKeyedByMenuId() throws IOException {
		write("window.json5", "{ format: 1, design: [640, 360] }");
		write("container/declaration.json5", "{ constants: { frame: '#3a2a1a' } }");
		write("container/minecraft/furnace/layout.json5", """
				{
				  ports: {
				    panel: { hide: true, width: "190px", height: "170px", elements: { bg: { type: "rect", color: "frame" } } },
				    title: { text: "Oven", color: "#ffffff" },
				    inventoryTitle: { hide: true },
				    slots: {
				      "*": { skin: { frame: { type: "rect", color: "#000" } }, highlight: {} },
				      "0": { x: "56px", y: "17px" },
				      "3-5": { skin: {} },
				      "nine": { x: 1 },
				    },
				    buttons: { "*": { visible: false } },
				  },
				  elements: {
				    arrow: { type: "bar", value: "menu.data.2 / max(1, menu.data.3)" },
				    go: { type: "button", action: { menuButton: 1 } },
				  },
				}
				""");
		write("container/pigtech/machine/treadmill/layout.json5", "{ elements: { b: { type: 'button', action: { menuButton: 'menu.data.0 > 0 ? 2 : 3' } } } }");
		write("container/pigtech/machine/treadmill/subwindow_side/layout.json5", "{ elements: { t: { type: 'text', text: 'side' } } }");
		write("main_menu/layout.json5", "{ elements: { b: { type: 'button', action: { menuButton: 0 } } } }");
		GameValues.registerFamily("menu.data.", n -> (double) n);
		Layout l = Loader.load(dir).layout();

		Node furnace = l.container("minecraft:furnace");
		assertNotNull(furnace, problems(l));
		assertEquals(List.of("panel", "title", "inventoryTitle", "slots", "buttons"), List.copyOf(furnace.ports.keySet()));
		assertTrue(furnace.ports.get("panel").has("width"));
		assertTrue(furnace.ports.get("title").has("text"));
		Node slots = furnace.ports.get("slots");
		assertEquals(3, slots.slots.size(), problems(l));
		assertNotNull(slots.slots.get("*").ports.get("highlight"), "an empty highlight is kept: it hides vanilla's");
		assertTrue(slots.slots.get("0").selector.matches(0, false));
		assertEquals("menuButton", furnace.children.get(1).action.kind());
		Node treadmill = l.container("pigtech:machine/treadmill");
		assertNotNull(treadmill, "a / in the id's path is a deeper folder");
		assertEquals(2, treadmill.children.size(), "its button and its subwindow");
		assertTrue(hasProblem(l, "container/minecraft/furnace/layout.json5", 10, "'nine' isn't a slot"), problems(l));
		assertTrue(hasProblem(l, "main_menu/layout.json5", 1, "'menuButton' works on container screens only"), problems(l));
		assertEquals(2, l.problems.list().size(), problems(l));
	}

	@Test
	void containerFolderProblems() throws IOException {
		write("window.json5", "{ format: 1 }");
		write("container/layout.json5", "{}");
		write("container/Pig Tech/furnace/layout.json5", "{}");
		write("container/minecraft/empty/.keep", "");
		write("container/minecraft/smoker/layout.json5", "{ ports: { panel: { scale: 2 }, player: {} } }");
		Layout l = Loader.load(dir).layout();
		assertTrue(hasProblem(l, "container/layout.json5", 0, "container/ has no layout of its own"), problems(l));
		assertTrue(hasProblem(l, "container/Pig Tech/", 0, "isn't a namespace"), problems(l));
		assertTrue(hasProblem(l, "container/minecraft/empty/", 0, "no screen here"), problems(l));
		assertTrue(hasProblem(l, "container/minecraft/smoker/layout.json5", 1, "can't be scaled"), problems(l));
		assertTrue(hasProblem(l, "container/minecraft/smoker/layout.json5", 1, "unknown port 'player' in container"), problems(l));
		assertEquals(5, l.problems.list().size(), problems(l));
	}

	@Test
	void aModsLayoutHoldsOnlyContainerScreens() throws IOException {
		// assets/<modid>/cakeui/ in a mod's jar: no window.json5 needed, plain numbers are GUI pixels
		write("container/pigtech/treadmill/layout.json5", "{ ports: { panel: { elements: { bar: { type: 'bar', x: 10, width: 40 } } } } }");
		write("hud/layout.json5", "{}");
		write("assets/tank.png", "");
		Loader loader = Loader.loadMod(dir, "pigtech");
		Layout l = loader.layout();
		assertFalse(loader.fatal);
		assertEquals("pigtech", l.owner);
		assertNotNull(l.container("pigtech:treadmill"));
		assertEquals(1, l.designScale(1000, 500)[0], "without a design, 1 r is 1 GUI pixel");
		assertTrue(hasProblem(l, "hud/", 0, "a mod's layout only holds container/"), problems(l));
		assertEquals(1, l.problems.list().size(), problems(l));

		write("window.json5", "{ design: [320, 240], title: 'no' }");
		Layout withWindow = Loader.loadMod(dir, "pigtech").layout();
		assertEquals(320, withWindow.designWidth);
		assertTrue(hasProblem(withWindow, "window.json5", 1, "a mod's layout can't set it"), problems(withWindow));
	}

	@Test
	void slotSelectors() {
		var one = io.github.harrythecake.cakeui.model.SlotSelector.parse("4");
		var set = io.github.harrythecake.cakeui.model.SlotSelector.parse("0, 2, 5-7");
		var player = io.github.harrythecake.cakeui.model.SlotSelector.parse("player");
		var any = io.github.harrythecake.cakeui.model.SlotSelector.parse("*");
		assertTrue(one.matches(4, true) && !one.matches(5, false));
		assertTrue(set.matches(2, false) && set.matches(6, false) && !set.matches(3, false));
		assertTrue(player.matches(30, true) && !player.matches(0, false));
		assertTrue(any.matches(99, false));
		assertTrue(one.specificity() > set.specificity() && set.specificity() > player.specificity() && player.specificity() > any.specificity());
		assertNull(io.github.harrythecake.cakeui.model.SlotSelector.parse("7-3"));
		assertNull(io.github.harrythecake.cakeui.model.SlotSelector.parse("hotbar"));
		assertNull(io.github.harrythecake.cakeui.model.SlotSelector.parse("1,,2"));
	}

	@Test
	void numberedValueFamilies() {
		GameValues.registerFamily("test.slot.", n -> n * 10.0);
		assertTrue(GameValues.has("test.slot.3"));
		assertFalse(GameValues.has("test.slot.x"));
		assertFalse(GameValues.has("test.slot.1000"));
		assertEquals(30.0, GameValues.resolve("test.slot.3").eval(new Env()));
		assertTrue(GameValues.names().contains("test.slot.0"));
	}
}
