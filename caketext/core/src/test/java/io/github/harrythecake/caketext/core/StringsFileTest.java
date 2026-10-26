package io.github.harrythecake.caketext.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StringsFileTest {
	@TempDir
	Path dir;

	private static StringsFile sample(int n) {
		StringsFile file = new StringsFile();
		file.server = "server.playmonumenta.com";
		String[] shards = {"valley", "isles", "ring", "plots"};
		for (int i = 0; i < n; i++) {
			StringEntry e = new StringEntry("{c1}Quest line number {~}: talk to the guard near gate " + (i % 37), "");
			e.count = (long) (i % 9 + 1);
			e.first = "2026-10-26T12:00:00Z";
			e.last = "2026-10-26T13:00:00Z";
			Map<String, Object> ctx = new LinkedHashMap<>();
			ctx.put("surface", i % 3 == 0 ? "chat" : "nameplate");
			ctx.put("server", "server.playmonumenta.com");
			ctx.put("dimension", "minecraft:overworld");
			ctx.put("shard", shards[i % shards.length]);
			ctx.put("area", "Sierhaven");
			ctx.put("pos", new int[] {-700 + i, 64, 120 - i});
			e.contexts = List.of(ctx);
			file.entries.add(e);
		}
		return file;
	}

	@Test
	void roundTripRawAndGzip() throws Exception {
		StringsFile file = sample(500);
		Path raw = dir.resolve("a.json");
		Path gz = dir.resolve("a.json.gz");
		file.write(raw, false);
		file.write(gz, true);
		for (Path p : List.of(raw, gz)) {
			StringsFile back = StringsFile.read(p);
			assertEquals(500, back.entries.size());
			assertEquals(file.entries.get(7).src, back.entries.get(7).src);
			assertEquals("isles", back.entries.get(1).contexts.get(0).get("shard"));
		}
		long rawSize = Files.size(raw);
		long gzSize = Files.size(gz);
		System.out.printf("500 entries: raw %d bytes, gzip %d bytes (%.1f%%)%n", rawSize, gzSize, 100.0 * gzSize / rawSize);
		assertTrue(gzSize * 4 < rawSize);
	}

	@Test
	void flatMapIsAccepted() throws Exception {
		String json = "{\"_note\": \"x\", \"Click to track\": \"点击追踪\", \"Total Damage: {~}\": \"总伤害：{~}\"}";
		StringsFile file = StringsFile.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		assertEquals(2, file.entries.size());
		assertEquals("点击追踪", file.entries.get(0).dst);
	}
}
