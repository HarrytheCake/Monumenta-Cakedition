package io.github.harrythecake.cakeupdater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgentAndMemoryTest {
	@TempDir
	Path game;

	private static final String WITHDRAWN = """
			{"2.3.1": {"en_us": "Crashes when joining.", "zh_cn": "进入服务器时崩溃。"},
			 "2.3.2": "Exploit in a mod.",
			 "2.3.3": {}}
			""";

	private UpdateCheck.Result check(String installed, String published) throws Exception {
		Path version = game.resolve("config/cakeupdater/pack-version.txt");
		Files.createDirectories(version.getParent());
		Files.writeString(version, installed);
		byte[] packToml = ("name = \"Pack\"\nversion = \"" + published + "\"\n").getBytes(StandardCharsets.UTF_8);
		return UpdateCheck.withWithdrawn(UpdateCheck.compare(packToml, game), WITHDRAWN);
	}

	@Test
	void withdrawnInstalledVersionMakesTheUpdateUrgent() throws Exception {
		UpdateCheck.Result result = check("2.3.1", "2.3.4");
		assertTrue(result.urgent());
		assertEquals("Crashes when joining.", Withdrawn.pick(result.withdrawn(), "en_us"));
		assertEquals("进入服务器时崩溃。", Withdrawn.pick(result.withdrawn(), "zh_tw"));
		assertEquals("Crashes when joining.", Withdrawn.pick(result.withdrawn(), "de_de"));
	}

	@Test
	void otherVersionsAreNotUrgent() throws Exception {
		assertFalse(check("2.3.0", "2.3.4").urgent());
		// no update available: nothing to be urgent about
		assertFalse(check("2.3.1", "2.3.1").urgent());
	}

	@Test
	void reasonMayBeAPlainStringOrMissing() throws Exception {
		assertEquals("Exploit in a mod.", Withdrawn.pick(check("2.3.2", "2.3.4").withdrawn(), "zh_cn"));
		UpdateCheck.Result noReason = check("2.3.3", "2.3.4");
		assertTrue(noReason.urgent());
		assertEquals("", Withdrawn.pick(noReason.withdrawn(), "en_us"));
	}

	@Test
	void withdrawnFileSitsNextToPackToml() {
		assertEquals("https://raw.githubusercontent.com/o/r/packwiz/pack/config/cakeupdater/withdrawn.json",
				Withdrawn.url("https://raw.githubusercontent.com/o/r/packwiz/pack/pack.toml"));
		assertNull(Withdrawn.reasons("{}", "2.3.1"));
	}

	@Test
	void memoryRange() {
		assertEquals(MemoryCheck.Problem.LOW, MemoryCheck.problem(2048, 3, 12));
		assertNull(MemoryCheck.problem(3000, 3, 12)); // -Xmx3G reported a little under 3072
		assertNull(MemoryCheck.problem(12 * 1024, 3, 12));
		assertEquals(MemoryCheck.Problem.HIGH, MemoryCheck.problem(16 * 1024, 3, 12));
		assertNull(MemoryCheck.problem(64 * 1024, 3, 0));
		assertEquals("3.5", MemoryCheck.gb(3584));
		assertEquals("4", MemoryCheck.gb(4096));
	}

	@Test
	void memoryWarningShowsOncePerSetting() {
		assertEquals(MemoryCheck.Problem.LOW, MemoryCheck.warnOnce(game, 2048, 3, 12));
		assertNull(MemoryCheck.warnOnce(game, 2048, 3, 12));
		assertEquals(MemoryCheck.Problem.LOW, MemoryCheck.warnOnce(game, 1024, 3, 12));
		assertNull(MemoryCheck.warnOnce(game, 4096, 3, 12));
		// back in range, then out again: warned again
		assertEquals(MemoryCheck.Problem.LOW, MemoryCheck.warnOnce(game, 1024, 3, 12));
	}
}
