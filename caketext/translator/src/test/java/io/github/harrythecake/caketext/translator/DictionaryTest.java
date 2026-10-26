package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.context.GameContext;
import io.github.harrythecake.caketext.core.strings.StringEntry;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Lookup rules: scoped beats unscoped, more specific beats less, humans beat the model, whitespace is forgiven. */
class DictionaryTest {
	private static final String LANG = "zz_test";

	@AfterEach
	void cleanUp() throws Exception {
		Path dir = CakeText.translations(LANG);
		if (Files.exists(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	private static StringEntry entry(String src, String dst, Map<String, String> scope) {
		StringEntry e = new StringEntry(src, dst);
		e.scope = scope;
		return e;
	}

	private static GameContext at(String surface, String shard, String area, int x, int y, int z) {
		return new GameContext(surface, "server.playmonumenta.com", "minecraft:overworld", shard, area, new BlockPos(x, y, z));
	}

	@Test
	void scopesAndPriorities() throws Exception {
		StringsFile human = new StringsFile();
		human.entries.add(entry("Back", "返回", null));
		human.entries.add(entry("Back", "向后", Map.of("surface", "nameplate")));
		human.entries.add(entry("Back", "后退一步", Map.of("surface", "nameplate", "box", "100 60 100 110 70 110")));
		human.entries.add(entry("Total   Damage: {~}", "总伤害：{~}", null));
		human.write(CakeText.translations(LANG).resolve("ui/main.json"), false);

		StringsFile machine = new StringsFile();
		StringEntry m1 = new StringEntry("Total Damage: {~}", "损失总额：{~}");
		m1.engine = "opus-mt-en-zh int8";
		StringEntry m2 = new StringEntry("Click to track", "单击以跟踪");
		m2.engine = "opus-mt-en-zh int8";
		machine.entries.add(m1);
		machine.entries.add(m2);
		machine.write(CakeText.translations(LANG).resolve(Dictionary.MACHINE_DIR + "/opus.json.gz"), true);

		Dictionary d = Dictionary.load(LANG);
		assertEquals(2, d.files());
		// machine caches are per engine; lookups use the active engine's file only
		d.setActiveMachineFile(Dictionary.MACHINE_DIR + "/opus.json.gz");

		assertEquals("返回", d.lookup("Back", () -> at("screen_title", "valley", null, 0, 0, 0)).dst());
		assertEquals("向后", d.lookup("Back", () -> at("nameplate", "valley", null, 0, 64, 0)).dst());
		assertEquals("后退一步", d.lookup("Back", () -> at("nameplate", "valley", null, 105, 64, 109)).dst());

		// human (whitespace-forgiving) beats the model's cached translation
		Dictionary.Hit damage = d.lookup("Total Damage: {~}", () -> at("chat", null, null, 0, 0, 0));
		assertEquals("总伤害：{~}", damage.dst());
		assertFalse(damage.machine());
		Dictionary.Hit track = d.lookup("Click to track", () -> at("chat", null, null, 0, 0, 0));
		assertTrue(track.machine());
		assertEquals("opus-mt-en-zh int8", track.engine());
		assertNull(d.lookup("Nothing here", () -> at("chat", null, null, 0, 0, 0)));
		assertTrue(d.hasHuman("Back"));
		assertFalse(d.hasHuman("Click to track"));
	}

	@Test
	void unknownScopeKeyNeverMatches() throws Exception {
		StringsFile human = new StringsFile();
		human.entries.add(entry("Gate", "大门", Map.of("shrad", "valley")));
		human.write(CakeText.translations(LANG).resolve("typo.json"), false);
		assertNull(Dictionary.load(LANG).lookup("Gate", () -> at("nameplate", "valley", null, 0, 0, 0)));
	}
}
