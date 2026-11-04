package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

	private static JsonObject section(ChatPlusImport.Result r, String key) {
		return r.patch().getAsJsonObject(key);
	}

	private static JsonObject window(ChatPlusImport.Result r, int i) {
		return r.patch().getAsJsonArray("windows").get(i).getAsJsonObject();
	}

	private static List<String> tabNames(JsonObject window) {
		List<String> names = new ArrayList<>();
		window.getAsJsonArray("tabs").forEach(t -> names.add(t.getAsJsonObject().get("name").getAsString()));
		return names;
	}

	private static JsonObject tab(JsonObject window, int i) {
		return window.getAsJsonArray("tabs").get(i).getAsJsonObject();
	}

	@Test
	void settingsFrom27() {
		ChatPlusImport.Result r = ChatPlusImport.convert(json(V27), 540);
		assertEquals(0, section(r, "history").get("maxMessages").getAsInt());
		JsonObject timestamps = section(r, "timestamps");
		assertTrue(timestamps.get("enabled").getAsBoolean());
		assertEquals("HH:mm:ss", timestamps.get("pattern").getAsString());
		assertEquals("HOVER", timestamps.get("placement").getAsString());
		JsonObject compact = section(r, "compact");
		assertEquals(" ({count})", compact.get("format").getAsString());
		assertEquals("WITHIN_SECONDS", compact.get("mode").getAsString());
		assertEquals(3, compact.get("searchLines").getAsInt());
		assertEquals(45, compact.get("seconds").getAsInt());
		assertFalse(compact.get("compareStyle").getAsBoolean());
		assertTrue(section(r, "scroll").get("invert").getAsBoolean());
		assertFalse(section(r, "scroll").get("scrollbar").getAsBoolean());
		assertTrue(section(r, "find").get("matchCase").getAsBoolean());
		assertTrue(section(r, "find").get("regex").getAsBoolean());
		// ChatPlus's default peek key isn't taken over (CakeChat's Left Alt stays); a bound hide key is
		assertNull(r.peekKey());
		assertEquals("key.keyboard.h", r.hideKey());
	}

	@Test
	void windowsAndTabsFrom27() {
		ChatPlusImport.Result r = ChatPlusImport.convert(json(V27), 540);
		// the disabled window is left out
		assertEquals(2, r.windows());
		assertEquals(2, r.patch().getAsJsonArray("windows").size());

		JsonObject main = window(r, 0);
		assertEquals(4, main.get("x").getAsInt());
		assertEquals(140, main.get("bottom").getAsInt());
		assertEquals(280, main.get("width").getAsInt());
		assertEquals(120, main.get("height").getAsInt());
		assertEquals(60, main.get("unfocusedHeight").getAsInt());
		assertEquals(List.of("All", "lfg", "gold"), tabNames(main));
		assertEquals(1, main.get("selectedTab").getAsInt());

		JsonObject all = tab(main, 0);
		assertEquals("", all.get("pattern").getAsString());
		assertTrue(all.get("alwaysAdd").getAsBoolean());

		JsonObject lfg = tab(main, 1);
		assertEquals("^(?:(?:.*\\<lfg\\>.*)|(?:.*LFG:.*))$", lfg.get("pattern").getAsString());
		assertEquals("^(?:.*example\\.net)$", lfg.get("server").getAsString());
		assertEquals("/lfg ", lfg.get("prefix").getAsString());
		assertEquals(5, lfg.get("priority").getAsInt());
		assertTrue(lfg.get("skipOthers").getAsBoolean());
		assertEquals("", lfg.get("channel").getAsString());

		assertEquals(List.of("gold"), r.formattedTabs());
		assertEquals(List.of("broken"), r.skippedTabs());

		// width below CakeChat's minimum is raised; no height = follow Chat Settings
		JsonObject dm = window(r, 1);
		assertEquals(60, dm.get("width").getAsInt());
		assertEquals(-1, dm.get("height").getAsInt());
		assertEquals(340, dm.get("bottom").getAsInt());
	}

	@Test
	void importedPatternsMatchTheWholeTextLikeChatPlus() {
		ChatPlusImport.Result r = ChatPlusImport.convert(json(V27), 540);
		Pattern lfg = Pattern.compile(tab(window(r, 0), 1).get("pattern").getAsString());
		assertTrue(lfg.matcher("<lfg> qwertfox » need tank").find());
		assertTrue(lfg.matcher("LFG: zxcvbnm").find());
		assertFalse(lfg.matcher("lfg: zxcvbnm").find());
	}

	@Test
	void layoutFrom25() {
		ChatPlusImport.Result r = ChatPlusImport.convert(json(V25), 0);
		assertEquals(500, section(r, "history").get("maxMessages").getAsInt());
		assertEquals(1, r.windows());
		JsonObject main = window(r, 0);
		assertEquals(List.of("All", "friend"), tabNames(main));
		assertEquals("^(?:(?:a\\.example)|(?:b\\.example))$", tab(main, 1).get("server").getAsString());
		// out-of-range selection is clamped; unknown screen height keeps CakeChat's bottom
		assertEquals(1, main.get("selectedTab").getAsInt());
		assertEquals(40, main.get("bottom").getAsInt());
		assertEquals(171, main.get("unfocusedHeight").getAsInt());
		assertEquals("key.keyboard.o", r.peekKey());
		// a key with a modifier (Ctrl+J) isn't taken over: CakeChat's keys have no modifiers
		assertNull(r.hideKey());
	}

	@Test
	void mergeKeepsWhatChatPlusDoesntHave() {
		JsonObject cakechat = json("""
				{"configVersion": 3, "history": {"maxMessages": 0, "keepOnDisconnect": false},
				 "channelMemory": {"play.example": "g"}, "windows": [{"tabs": [{"name": "cakechat.tab.all"}]}]}
				""");
		ChatPlusImport.merge(cakechat, ChatPlusImport.convert(json(V25), 0).patch());
		assertEquals(500, cakechat.getAsJsonObject("history").get("maxMessages").getAsInt());
		assertFalse(cakechat.getAsJsonObject("history").get("keepOnDisconnect").getAsBoolean());
		assertEquals("g", cakechat.getAsJsonObject("channelMemory").get("play.example").getAsString());
		assertEquals(3, cakechat.get("configVersion").getAsInt());
		JsonArray windows = cakechat.getAsJsonArray("windows");
		assertEquals(List.of("All", "friend"), tabNames(windows.get(0).getAsJsonObject()));

		// no windows in the file: CakeChat's stay
		JsonObject untouched = json("{\"windows\": [{\"tabs\": []}]}");
		ChatPlusImport.merge(untouched, ChatPlusImport.convert(json("{\"chatWindows\": []}"), 540).patch());
		assertEquals(1, untouched.getAsJsonArray("windows").size());
	}

	@Test
	void worthImportingOnlyWhenItGivesMoreThanOneAllTab() {
		assertTrue(ChatPlusImport.worthImporting(json(V27)));
		assertTrue(ChatPlusImport.worthImporting(json(V25)));
		assertFalse(ChatPlusImport.worthImporting(json("""
				{"chatWindows": [{"tabSettings": {"tabs": [{"settings": [{"pattern": "(?s).*", "name": "All"}]}]}}]}
				""")));
		assertTrue(ChatPlusImport.worthImporting(json("""
				{"chatWindows": [{"tabSettings": {"tabs": [{"settings": [{"pattern": "^<g>.*", "name": "g"}]}]}}]}
				""")));
		assertFalse(ChatPlusImport.worthImporting(json("{}")));
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
