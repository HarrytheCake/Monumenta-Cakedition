package io.github.harrythecake.caketext.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import io.github.harrythecake.caketext.translator.mt.OpusMt;
import org.junit.jupiter.api.Test;

/**
 * The real install: the test plays the player and downloads the files from Hugging Face (~312 MB) into the model
 * folder, then the mod checks, converts, loads, translates and deletes. Opt-in with {@code -Dcaketext.downloadTest=true}.
 */
class ModelInstallTest {
	@Test
	void installFromFolder() throws Exception {
		assumeTrue(Boolean.getBoolean("caketext.downloadTest"), "download test not requested");
		ModelManager models = new ModelManager();
		models.delete();
		Path src = ModelManager.importDir();
		Files.createDirectories(src);

		// with a file missing, Install says which, and stays MISSING
		download("config.json", src);
		models.install();
		waitWhileBusy(models);
		assertEquals(ModelManager.State.MISSING, models.state());
		System.out.println("  " + models.describe().getString());

		for (String name : ModelManager.FILE_NAMES.split(", ")) {
			download(name, src);
		}
		long t = System.nanoTime();
		models.install();
		waitWhileBusy(models);
		System.out.printf("%5.1f s  %s%n", (System.nanoTime() - t) / 1e9, models.state());
		assertEquals(ModelManager.State.READY, models.state(), () -> models.describe().getString());
		for (String name : ModelManager.FILE_NAMES.split(", ")) {
			assertFalse(Files.exists(src.resolve(name)), name + " should be deleted after converting");
		}
		System.out.println("  " + models.model().translate("Welcome to the Valley!", ">>cmn_Hans<<", OpusMt.Quality.BEST));
		models.delete();
	}

	private static void waitWhileBusy(ModelManager models) throws InterruptedException {
		Thread.sleep(200);
		ModelManager.State last = null;
		while (models.state() == ModelManager.State.CHECKING || models.state() == ModelManager.State.CONVERTING
				|| models.state() == ModelManager.State.LOADING) {
			if (models.state() != last) {
				last = models.state();
				System.out.println("  " + last);
			}
			Thread.sleep(200);
		}
	}

	private static void download(String name, Path dir) throws Exception {
		Path target = dir.resolve(name);
		if (Files.exists(target)) {
			return;
		}
		URI uri = URI.create(ModelManager.PAGE_URL.replace("/tree/", "/resolve/") + "/" + name);
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20)).build();
		HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		assertEquals(200, response.statusCode(), name);
		try (InputStream in = response.body()) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
