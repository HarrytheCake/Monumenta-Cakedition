package io.github.harrythecake.cakechat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Made-up ChatPlus files in the 2.7 (tab rules under "settings") and 2.5 (rules on the tab) layouts. */
class ChatPlusImportTest {
	private static final String V27 = """
			{
			  "maxMessages": -1,
			  "timestampSettings": {"enabled": true, "timestampFormat": "&7at &e{HH:mm:ss}&7.", "chatTimestampModeType": "HOVER"},
			  "compactMessagesEnabled": true,
			  "compactMessagesFormat": " &7(%VALUE%)",
			  "compactMessagesSearchAmount": 3,
			  "compactMessagesTimeSeconds": 45,
			  "compactMessagesMode": "TIME",
			  "compactMessageSettings": {"style": false},
			  "invertedScrolling": true,
			  "scrollbarEnabled": false,
			  "keyPeekChat": "key.keyboard.p",
			  "hideChatToggleKey": {"key": "key.keyboard.h", "modifier": 0},
			  "findMessageIgnoreCase": false,
			  "findMessageDefaultMode": "REGEX",
			  "chatWindows": [
			    {
			      "generalSettings": {"disabled": false, "unfocusedHeight": 0.5},
			      "renderer": {"x": 4, "y": 400, "width": 280, "height": 120},
			      "tabSettings": {
			        "selectedTabIndex": 1,
			        "tabs": [
			          {"settings": [{"pattern": "(?s).*", "formatted": false, "serverPattern": {"pattern": ""},
			            "name": "All", "autoPrefix": "", "priority": 0, "alwaysAdd": true, "skipOthers": false}]},
			          {"settings": [{"pattern": ".*\\\\<lfg\\\\>.*", "formatted": false, "serverPattern": {"pattern": ".*example\\\\.net"},
			            "name": "lfg", "autoPrefix": "/lfg ", "priority": 5, "alwaysAdd": false, "skipOthers": true},
			            {"pattern": ".*LFG:.*", "formatted": false, "name": "ignored"}]},
			          {"settings": [{"pattern": "&6.*", "formatted": true, "name": "gold"}]},
			          {"settings": [{"pattern": "(unclosed", "formatted": false, "name": "broken"}]}
			        ]
			      }
			    },
			    {"generalSettings": {"disabled": true}, "renderer": {"x": 0, "y": 100, "width": 100, "height": 50},
			     "tabSettings": {"tabs": [{"settings": [{"pattern": ".*", "name": "Hidden"}]}]}},
			    {"renderer": {"x": 300, "y": 200, "width": 50, "height": 0},
			     "tabSettings": {"tabs": [{"settings": [{"pattern": ".*whispers to.*", "name": "dm"}]}]}}
			  ]
			}
			""";

	private static final String V25 = """
			{
			  "maxMessages": 500,
			  "keyPeekChat": "key.keyboard.o",
			  "hideChatToggleKey": {"key": "key.keyboard.j", "modifier": 2},
			  "chatWindows": [{
			    "renderer": {"x": 0, "y": 475, "width": 300, "height": 171},
			    "tabSettings": {"selectedTabIndex": 9, "tabs": [
			      {"pattern": "(?s).*", "name": "All", "autoPrefix": "", "serverTabPatterns": [], "priority": 0, "alwaysAdd": true, "skipOthers": false},
			      {"pattern": ".*Mock_Pl4yer.*", "name": "friend", "serverTabPatterns": ["a\\\\.example", {"pattern": "b\\\\.example"}]}
			    ]}
			  }]
			}
			""";

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void settingsFrom27() {
		ChatConfig c = new ChatConfig();
		ChatPlusImport.Result r = ChatPlusImport.apply(json(V27), c, 540);
		assertEquals(0, c.history.maxMessages);
		assertTrue(c.timestamps.enabled);
		assertEquals("HH:mm:ss", c.timestamps.pattern);
		assertEquals(ChatConfig.Timestamps.Placement.HOVER, c.timestamps.placement);
		assertEquals(" ({count})", c.compact.format);
		assertEquals(ChatConfig.Compact.Mode.WITHIN_SECONDS, c.compact.mode);
		assertEquals(3, c.compact.searchLines);
		assertEquals(45, c.compact.seconds);
		assertFalse(c.compact.compareStyle);
		assertTrue(c.scroll.invert);
		assertFalse(c.scroll.scrollbar);
		assertTrue(c.find.matchCase);
		assertTrue(c.find.regex);
		// ChatPlus's default peek key isn't taken over (CakeChat's Left Alt stays); a bound hide key is
		assertNull(r.peekKey());
		assertEquals("key.keyboard.h", r.hideKey());
	}

	@Test
	void windowsAndTabsFrom27() {
		ChatConfig c = new ChatConfig();
		ChatPlusImport.Result r = ChatPlusImport.apply(json(V27), c, 540);
		// the disabled window is left out
		assertEquals(2, r.windows());
		assertEquals(2, c.windows.size());

		ChatConfig.Window main = c.windows.get(0);
		assertEquals(4, main.x);
		assertEquals(140, main.bottom);
		assertEquals(280, main.width);
		assertEquals(120, main.height);
		assertEquals(60, main.unfocusedHeight);
		assertEquals(List.of("All", "lfg", "gold"), main.tabs.stream().map(t -> t.name).toList());
		assertEquals(1, main.selectedTab);

		ChatConfig.Tab all = main.tabs.get(0);
		assertEquals("", all.pattern);
		assertTrue(all.alwaysAdd);

		ChatConfig.Tab lfg = main.tabs.get(1);
		assertEquals("^(?:(?:.*\\<lfg\\>.*)|(?:.*LFG:.*))$", lfg.pattern);
		assertEquals("^(?:.*example\\.net)$", lfg.server);
		assertEquals("/lfg ", lfg.prefix);
		assertEquals(5, lfg.priority);
		assertTrue(lfg.skipOthers);
		assertEquals("", lfg.channel);

		assertEquals(List.of("gold"), r.formattedTabs());
		assertEquals(List.of("broken"), r.skippedTabs());

		// width below CakeChat's minimum is raised; no height = follow Chat Settings
		ChatConfig.Window dm = c.windows.get(1);
		assertEquals(60, dm.width);
		assertEquals(-1, dm.height);
		assertEquals(340, dm.bottom);
	}

	@Test
	void importedPatternsMatchTheWholeTextLikeChatPlus() {
		ChatConfig c = new ChatConfig();
		ChatPlusImport.apply(json(V27), c, 540);
		TabRules rules = TabRules.of(c.windows);
		ChatConfig.Tab lfg = c.windows.get(0).tabs.get(1);
		assertTrue(java.util.regex.Pattern.compile(lfg.pattern).matcher("<lfg> qwertfox » need tank").find());
		assertTrue(java.util.regex.Pattern.compile(lfg.pattern).matcher("LFG: zxcvbnm").find());
		assertFalse(java.util.regex.Pattern.compile(lfg.pattern).matcher("lfg: zxcvbnm").find());
		assertEquals(4, rules.size());
	}

	@Test
	void layoutFrom25() {
		ChatConfig c = new ChatConfig();
		ChatPlusImport.Result r = ChatPlusImport.apply(json(V25), c, 0);
		assertEquals(500, c.history.maxMessages);
		assertEquals(1, c.windows.size());
		ChatConfig.Window main = c.windows.get(0);
		assertEquals(List.of("All", "friend"), main.tabs.stream().map(t -> t.name).toList());
		assertEquals("^(?:(?:a\\.example)|(?:b\\.example))$", main.tabs.get(1).server);
		// out-of-range selection is clamped; unknown screen height keeps CakeChat's bottom
		assertEquals(1, main.selectedTab);
		assertEquals(40, main.bottom);
		assertEquals(171, main.unfocusedHeight);
		assertEquals("key.keyboard.o", r.peekKey());
		// a key with a modifier (Ctrl+J) isn't taken over: CakeChat's keys have no modifiers
		assertNull(r.hideKey());
	}

	@Test
	void settingsChatPlusDoesntHaveStay() {
		ChatConfig c = new ChatConfig();
		c.history.keepOnDisconnect = false;
		c.channelMemory.put("play.example", "g");
		ChatPlusImport.apply(json("{\"chatWindows\": []}"), c, 540);
		assertFalse(c.history.keepOnDisconnect);
		assertEquals("g", c.channelMemory.get("play.example"));
		// no windows in the file: CakeChat's stay
		assertEquals(ChatConfig.Window.main().tabs.size(), c.windows.get(0).tabs.size());
	}

	@Test
	void offeredOnlyWhenItGivesMoreThanOneAllTab() {
		assertTrue(ChatPlusImport.worthOffering(json(V27)));
		assertTrue(ChatPlusImport.worthOffering(json(V25)));
		assertFalse(ChatPlusImport.worthOffering(json("""
				{"chatWindows": [{"tabSettings": {"tabs": [{"settings": [{"pattern": "(?s).*", "name": "All"}]}]}}]}
				""")));
		assertTrue(ChatPlusImport.worthOffering(json("""
				{"chatWindows": [{"tabSettings": {"tabs": [{"settings": [{"pattern": "^<g>.*", "name": "g"}]}]}}]}
				""")));
		assertFalse(ChatPlusImport.worthOffering(json("{}")));
	}

	@Test
	void newestFileByVersionNumber(@TempDir Path dir) throws IOException {
		assertEquals(Optional.empty(), ChatPlusImport.newestFile(dir.resolve("missing")));
		for (String name : List.of("chatplus-v2.5.0.json", "chatplus-v2.10.0.json", "chatplus-v2.7.0.json", "other.json", "chatplus-v3.json.bak")) {
			Files.writeString(dir.resolve(name), "{}");
		}
		assertEquals("chatplus-v2.10.0.json", ChatPlusImport.newestFile(dir).orElseThrow().getFileName().toString());
		assertTrue(ChatPlusImport.read(dir.resolve("chatplus-v2.10.0.json")).isPresent());
		Files.writeString(dir.resolve("broken.json"), "{not json");
		assertTrue(ChatPlusImport.read(dir.resolve("broken.json")).isEmpty());
	}
}
