package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TabRulesTest {
	private static ChatConfig.Tab tab(String pattern, int priority, boolean skipOthers, boolean alwaysAdd) {
		ChatConfig.Tab t = ChatConfig.Tab.named(pattern, pattern);
		t.priority = priority;
		t.skipOthers = skipOthers;
		t.alwaysAdd = alwaysAdd;
		return t;
	}

	private static ChatConfig.Window window(ChatConfig.Tab... tabs) {
		ChatConfig.Window w = new ChatConfig.Window();
		w.tabs = List.of(tabs);
		return w;
	}

	private static String bits(BitSet set, int size) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < size; i++) {
			s.append(set.get(i) ? '1' : '0');
		}
		return s.toString();
	}

	@Test
	void emptyPatternTakesEverythingAndPatternsAreFound() {
		TabRules rules = TabRules.of(List.of(window(tab("", 0, false, false), tab("^<g> ", 0, false, false), tab("lfg", 0, false, false))));
		assertEquals("110", bits(rules.route("<g> qwertfox » hi"), 3));
		assertEquals("101", bits(rules.route("<lfg> [AB] qwertfox » omw"), 3));
		assertEquals("100", bits(rules.route("You found a chest"), 3));
	}

	@Test
	void skipOthersStopsLowerTabsButNotAlwaysAdd() {
		// tab order: All (0), Global (priority 10, skip), Spy (always add)
		TabRules rules = TabRules.of(List.of(window(
				tab("", 0, false, false),
				tab("^<g> ", 10, true, false),
				tab("", -5, false, true))));
		assertEquals("011", bits(rules.route("<g> zxcvbnm » hi."), 3));
		assertEquals("101", bits(rules.route("Welcome back"), 3));
	}

	@Test
	void equalPrioritiesKeepTabOrder() {
		TabRules rules = TabRules.of(List.of(window(tab("a", 0, true, false), tab("a", 0, true, false))));
		assertEquals("10", bits(rules.route("a"), 2));
	}

	@Test
	void windowsAreIndependent() {
		TabRules rules = TabRules.of(List.of(
				window(tab("", 0, true, false), tab("", 0, false, false)),
				window(tab("trade", 0, false, false))));
		assertEquals(3, rules.size());
		assertEquals(2, rules.index(1, 0));
		assertEquals("101", bits(rules.route("<tr> [AbC] Mock_Pl4yer » trade?"), 3));
	}

	@Test
	void invalidPatternMatchesNothing() {
		TabRules rules = TabRules.of(List.of(window(tab("(oops", 0, false, false), tab("", 0, false, false))));
		assertEquals("01", bits(rules.route("(oops"), 2));
		assertNotNull(TabRules.patternError("(oops"));
		assertNull(TabRules.patternError("^<(g|lfg)> "));
	}

	/** The shipped main window: All, Global, LFG, Trade, 中文, Guild, Whisper (made-up players and guilds). */
	@Test
	void defaultMonumentaTabs() {
		TabRules rules = TabRules.of(List.of(ChatConfig.Window.main()));
		assertEquals(7, rules.size());
		assertEquals("1100000", bits(rules.route("<g> qwertfox » hi."), 7));
		assertEquals("1100000", bits(rules.route("<g> [AB] qwertfox » hi."), 7));
		assertEquals("1010000", bits(rules.route("<lfg> [AB] zxcvbnm » omw arr"), 7));
		assertEquals("1001000", bits(rules.route("<tr> [AbC] Mock_Pl4yer » wts [Salazar's Greed] 5%"), 7));
		assertEquals("1000100", bits(rules.route("<zh> [CKE] testcrafter » 你好"), 7));
		assertEquals("1000010", bits(rules.route("<Cakes> [CKE] testcrafter » anyone up?"), 7));
		assertEquals("1000001", bits(rules.route("qwertfox whispers to you: hey"), 7));
		assertEquals("1000001", bits(rules.route("You whisper to qwertfox: hey"), 7));
		assertEquals("1000000", bits(rules.route("You found a chest!"), 7));
		for (ChatConfig.Tab tab : ChatConfig.Window.main().tabs) {
			assertNull(TabRules.patternError(tab.pattern), tab.name);
			assertNull(TabRules.patternError(tab.server), tab.name);
		}
	}

	@Test
	void monumentaServerPattern() {
		java.util.regex.Pattern server = java.util.regex.Pattern.compile(ChatConfig.Tab.MONUMENTA, java.util.regex.Pattern.CASE_INSENSITIVE);
		assertEquals(true, server.matcher("server.playmonumenta.com").find());
		assertEquals(true, server.matcher("PlayMonumenta.com:25565").find());
		assertEquals(false, server.matcher("notplaymonumenta.com").find());
		assertEquals(false, server.matcher("").find());
	}

	@Test
	void routingTextDropsCakeTextIcons() {
		assertEquals("<g> testcrafter » full", TabRules.routingText(" <g> testcrafter » full"));
		assertEquals("plain", TabRules.routingText("plain"));
	}
}
