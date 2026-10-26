package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.core.strings.StringsFile;
import net.minecraft.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Machine translations are cached on disk per engine and reloaded; player chat never touches the disk. */
class MachineCacheTest {
	private static final String LANG = "zz_cache";

	/** Answers instantly with "译=" + the input. */
	private static final class FakeEngine implements TranslationEngine {
		final String id;

		FakeEngine(String id) {
			this.id = id;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String label() {
			return id + " label";
		}

		@Override
		public boolean ready() {
			return true;
		}

		@Override
		public Text describe() {
			return Text.literal(id);
		}

		@Override
		public String translate(String text, TranslatorConfig config) {
			return "译=" + text;
		}
	}

	@AfterEach
	void cleanUp() throws Exception {
		Path dir = CakeText.translations(LANG);
		if (Files.exists(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	private static void waitFor(Set<String> done, String template) throws InterruptedException {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!done.contains(template) && System.nanoTime() < until) {
			Thread.sleep(20);
		}
		assertTrue(done.contains(template), "never finished: " + template);
	}

	@Test
	void savedPerEngineAndReloaded() throws Exception {
		TranslatorConfig config = new TranslatorConfig();
		config.language = LANG;
		FakeEngine[] active = {new FakeEngine("engine-a")};
		Set<String> done = ConcurrentHashMap.newKeySet();
		MachineTranslator machine = new MachineTranslator(() -> active[0], done::add);
		Dictionary dict = Dictionary.load(LANG);
		machine.use(dict, config);

		assertTrue(machine.submit("Welcome to the Valley!", false));
		assertTrue(machine.submit("anyone selling a key?", true));
		waitFor(done, "Welcome to the Valley!");
		waitFor(done, "anyone selling a key?");
		machine.save();

		// server text: on disk, in engine A's own file
		Path fileA = CakeText.translations(LANG).resolve("_machine/engine-a.json.gz");
		assertTrue(Files.exists(fileA));
		StringsFile saved = StringsFile.read(fileA);
		assertEquals(1, saved.entries.size());
		assertEquals("译=Welcome to the Valley!", saved.entries.get(0).dst);
		assertEquals("engine-a label", saved.entries.get(0).engine);
		// player chat: in memory only
		assertEquals("译=anyone selling a key?", machine.chatResult("anyone selling a key?"));
		try (Stream<Path> files = Files.walk(CakeText.translations(LANG))) {
			assertFalse(files.filter(Files::isRegularFile).anyMatch(p -> {
				try {
					return StringsFile.read(p).entries.stream().anyMatch(e -> e.src.contains("selling"));
				} catch (Exception e) {
					return false;
				}
			}), "player chat was written to disk");
		}

		// a fresh start (new dictionary from disk) finds it without asking the engine again
		Dictionary reloaded = Dictionary.load(LANG);
		machine.use(reloaded, config);
		Dictionary.Hit hit = reloaded.lookup("Welcome to the Valley!", () -> null);
		assertNotNull(hit);
		assertTrue(hit.machine());
		assertEquals("engine-a label", hit.engine());
		assertFalse(machine.submit("Welcome to the Valley!", false), "already cached, nothing to queue");

		// another engine has its own cache: A's result isn't used, B translates it again into its own file
		active[0] = new FakeEngine("engine-b");
		machine.use(reloaded, config);
		assertNull(reloaded.lookup("Welcome to the Valley!", () -> null));
		done.clear();
		assertTrue(machine.submit("Welcome to the Valley!", false));
		waitFor(done, "Welcome to the Valley!");
		machine.save();
		assertTrue(Files.exists(CakeText.translations(LANG).resolve("_machine/engine-b.json.gz")));
		assertEquals(1, StringsFile.read(fileA).entries.size(), "engine A's file untouched");
	}
}
