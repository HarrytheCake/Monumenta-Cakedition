package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;

import io.github.harrythecake.caketext.translator.mt.OpusMt;
import org.junit.jupiter.api.Test;

/**
 * The real install: download from Hugging Face (~312 MB), verify, convert, load, translate, delete.
 * Opt-in with {@code -Dcaketext.downloadTest=true}, since it downloads.
 */
class ModelInstallTest {
	@Test
	void installFromHuggingFace() throws Exception {
		assumeTrue(Boolean.getBoolean("caketext.downloadTest"), "download test not requested");
		ModelManager models = new ModelManager();
		models.delete();
		long t = System.nanoTime();
		models.install();
		ModelManager.State last = null;
		while (models.state() != ModelManager.State.READY && models.state() != ModelManager.State.FAILED) {
			if (models.state() != last) {
				last = models.state();
				System.out.printf("%5.1f s  %s%n", (System.nanoTime() - t) / 1e9, last);
			}
			Thread.sleep(200);
		}
		System.out.printf("%5.1f s  %s%n", (System.nanoTime() - t) / 1e9, models.state());
		assertEquals(ModelManager.State.READY, models.state(), () -> models.describe().getString());
		try (var files = Files.list(ModelManager.dir())) {
			files.forEach(p -> System.out.println("  " + p.getFileName() + (Files.isDirectory(p) ? " (folder, left over)" : " " + p.toFile().length() / 1024 + " KB")));
		}
		System.out.println("  " + models.model().translate("Welcome to the Valley!", ">>cmn_Hans<<", OpusMt.Quality.BEST));
		models.delete();
	}
}
