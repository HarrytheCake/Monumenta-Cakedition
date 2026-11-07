package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConfigTest {
	@TempDir
	Path dir;

	@Test
	void missingFileGivesDefaults() {
		ChatConfig config = ChatConfig.load(dir.resolve("config.json"));
		assertEquals(0, config.history.maxMessages);
		assertTrue(config.history.keepOnDisconnect);
		assertTrue(config.compact.enabled);
	}

	@Test
	void resetTargetIsTheShippedTab() {
		List<ChatConfig.Tab> shipped = ChatConfig.Window.main().tabs;
		// a default tab the player edited: reset brings back its own pattern and channel, not a blank "All" tab
		ChatConfig.Tab trade = shipped.get(3).copy();
		trade.channel = "x";
		trade.pattern = "changed";
		ChatConfig.Tab target = trade.resetTarget();
		assertEquals(shipped.get(3).name, target.name);
		assertEquals("^<tr> ", target.pattern);
		assertEquals("tr", target.channel);
		assertEquals(shipped.get(3).server, target.server);
		// renamed but pattern unchanged: still found
		ChatConfig.Tab lfg = shipped.get(2).copy();
		lfg.name = "Groups";
		lfg.channel = "";
		assertEquals("lfg", lfg.resetTarget().channel);
		assertEquals(shipped.get(2).name, lfg.resetTarget().name);
		// a tab the player added: reset keeps it as it is
		ChatConfig.Tab own = ChatConfig.Tab.named("Mine", "^<mine> ");
		own.channel = "mine";
		ChatConfig.Tab ownTarget = own.resetTarget();
		assertEquals("Mine", ownTarget.name);
		assertEquals("^<mine> ", ownTarget.pattern);
		assertEquals("mine", ownTarget.channel);
	}

	@Test
	void roundTrip() throws IOException {
		Path file = dir.resolve("cakechat").resolve("config.json");
		ChatConfig config = new ChatConfig();
		config.history.maxMessages = 5000;
		config.compact.mode = ChatConfig.Compact.Mode.WITHIN_SECONDS;
		config.timestamps.placement = ChatConfig.Timestamps.Placement.BEFORE;
		config.save(file);

		ChatConfig back = ChatConfig.load(file);
		assertEquals(5000, back.history.maxMessages);
		assertEquals(ChatConfig.Compact.Mode.WITHIN_SECONDS, back.compact.mode);
		assertEquals(ChatConfig.Timestamps.Placement.BEFORE, back.timestamps.placement);
		assertFalse(Files.exists(file.resolveSibling("config.json.tmp")));
	}

	@Test
	void partialFileKeepsOtherDefaults() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"compact\": {\"enabled\": false, \"mode\": \"NO_SUCH_MODE\"}, \"timestamps\": null}", StandardCharsets.UTF_8);

		ChatConfig config = ChatConfig.load(file);
		assertFalse(config.compact.enabled);
		assertEquals(ChatConfig.Compact.Mode.LATEST, config.compact.mode);
		assertEquals(" (x{count})", config.compact.format);
		assertEquals("HH:mm", config.timestamps.pattern);
		assertTrue(config.history.keepOnDisconnect);
	}

	@Test
	void windowsAreRepaired() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"windows\": [{\"tabs\": [], \"selectedTab\": 4}, null, {\"x\": 300, \"tabs\": [{\"name\": \"Trade\", \"pattern\": null, \"prefix\": \"/tr \"}], \"selectedTab\": 3}]}",
				StandardCharsets.UTF_8);

		ChatConfig config = ChatConfig.load(file);
		assertEquals(2, config.windows.size());
		assertEquals("cakechat.tab.all", config.windows.get(0).tabs.get(0).name);
		assertEquals(0, config.windows.get(0).selectedTab);
		ChatConfig.Window trade = config.windows.get(1);
		assertEquals(300, trade.x);
		assertEquals("", trade.tabs.get(0).pattern);
		assertEquals("/tr ", trade.tabs.get(0).prefix);
		assertEquals(0, trade.selectedTab);
	}

	@Test
	void noWindowsGivesTheMainOne() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"windows\": []}", StandardCharsets.UTF_8);
		ChatConfig.Window main = ChatConfig.load(file).windows.get(0);
		assertEquals(-1, main.width);
		assertEquals(40, main.bottom);
	}

	@Test
	void version1GetsTheChineseTabAfterTrade() throws IOException {
		Path file = dir.resolve("config.json");
		ChatConfig old = new ChatConfig();
		old.configVersion = 1;
		old.windows.get(0).tabs.removeIf(t -> t.name.equals("cakechat.tab.zh"));
		old.windows.get(0).selectedTab = 5;
		old.save(file);

		ChatConfig config = ChatConfig.load(file);
		assertEquals(3, config.configVersion);
		List<ChatConfig.Tab> tabs = config.windows.get(0).tabs;
		assertEquals("cakechat.tab.trade", tabs.get(3).name);
		assertEquals("cakechat.tab.zh", tabs.get(4).name);
		assertEquals("zh", tabs.get(4).channel);
		assertEquals(6, config.windows.get(0).selectedTab, "still on the same tab");
	}

	@Test
	void version2GuildTabGetsTheGuildChannel() throws IOException {
		Path file = dir.resolve("config.json");
		ChatConfig old = new ChatConfig();
		old.configVersion = 2;
		old.windows.get(0).tabs.get(5).channel = "";
		old.save(file);

		ChatConfig config = ChatConfig.load(file);
		assertEquals(3, config.configVersion);
		assertEquals("cakechat.tab.guild", config.windows.get(0).tabs.get(5).name);
		assertEquals(GuildTag.PLACEHOLDER, config.windows.get(0).tabs.get(5).channel);
	}

	@Test
	void version2KeepsAGuildChannelThePlayerSet() throws IOException {
		Path file = dir.resolve("config.json");
		ChatConfig old = new ChatConfig();
		old.configVersion = 2;
		old.windows.get(0).tabs.get(5).channel = "XYZ";
		old.save(file);

		assertEquals("XYZ", ChatConfig.load(file).windows.get(0).tabs.get(5).channel);
	}

	@Test
	void migrationLeavesCustomTabsAlone() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{\"configVersion\": 1, \"windows\": [{\"tabs\": [{\"name\": \"Mine\"}]}]}", StandardCharsets.UTF_8);
		ChatConfig config = ChatConfig.load(file);
		assertEquals(1, config.windows.get(0).tabs.size());
		assertTrue(config.channelMemory.isEmpty());
	}

	@Test
	void brokenFileIsKeptAside() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{ not json", StandardCharsets.UTF_8);

		ChatConfig config = ChatConfig.load(file);
		assertTrue(config.compact.enabled);
		assertTrue(Files.exists(dir.resolve("config.json.broken")));
		assertFalse(Files.exists(file));
	}

	@Test
	void windowEditingIsOffByDefault() {
		assertFalse(new ChatConfig().windowEditing);
		assertFalse(ChatConfig.load(dir.resolve("config.json")).windowEditing);
	}

	@Test
	void olderFileWithoutWindowEditingLoadsLocked() throws IOException {
		Path file = dir.resolve("config.json");
		// a 0.3.0 file: current config version, moved main window, no windowEditing field
		Files.writeString(file, "{\"configVersion\": 3, \"windows\": [{\"x\": 12, \"bottom\": 60, \"tabs\": [{\"name\": \"Mine\"}]}]}",
				StandardCharsets.UTF_8);
		ChatConfig config = ChatConfig.load(file);
		assertFalse(config.windowEditing);
		assertEquals(12, config.windows.get(0).x);
		assertEquals(3, config.configVersion);
	}

	@Test
	void windowEditingIsSaved() throws IOException {
		Path file = dir.resolve("config.json");
		ChatConfig config = new ChatConfig();
		config.windowEditing = true;
		config.save(file);
		assertTrue(ChatConfig.load(file).windowEditing);
	}

	@Test
	void timestampPatternCheck() {
		assertTrue(ChatConfig.Timestamps.validPattern("HH:mm:ss"));
		assertFalse(ChatConfig.Timestamps.validPattern("HH:mm{"));
	}
}
