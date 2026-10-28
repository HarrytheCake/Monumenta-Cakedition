package io.github.harrythecake.cakechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
	void brokenFileIsKeptAside() throws IOException {
		Path file = dir.resolve("config.json");
		Files.writeString(file, "{ not json", StandardCharsets.UTF_8);

		ChatConfig config = ChatConfig.load(file);
		assertTrue(config.compact.enabled);
		assertTrue(Files.exists(dir.resolve("config.json.broken")));
		assertFalse(Files.exists(file));
	}

	@Test
	void timestampPatternCheck() {
		assertTrue(ChatConfig.Timestamps.validPattern("HH:mm:ss"));
		assertFalse(ChatConfig.Timestamps.validPattern("HH:mm{"));
	}
}
