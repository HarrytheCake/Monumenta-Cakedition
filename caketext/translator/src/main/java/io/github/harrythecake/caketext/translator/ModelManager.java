package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.translator.mt.OpusMt;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The local translation model: opus-mt-en-zh (Helsinki-NLP, Apache-2.0), converted to int8 on the player's PC.
 *
 * <p>Install = download the Hugging Face files at a pinned revision (~312 MB), check each SHA-256, convert to
 * {@code model.ctm} (~75 MB), delete the download. Nothing is downloaded until the player presses the button
 * (or runs {@code /cttranslate model install}).
 */
public final class ModelManager {
	public static final String ID = "opus-mt-en-zh-int8";
	public static final String LABEL = "opus-mt-en-zh int8";
	private static final String REPO = "Helsinki-NLP/opus-mt-en-zh";
	private static final String REVISION = "408d9bc410a388e1d9aef112a2daba955b945255";

	private record Remote(String name, String sha256, long size) {
	}

	private static final List<Remote> FILES = List.of(
			new Remote("config.json", "bcae8ed74fed77fb51c58462b62397fee6b1a1a34aece79183a0dd02ad329e71", 1403),
			new Remote("vocab.json", "f1832ca38f7aa158b9a944ff583e4901e6b37d9d4ad9241f03838e636e9cfb03", 1617791),
			new Remote("source.spm", "5775ddc9e3ff2fae91554da56468ad35ff56edaba870fea74447bc7234bfdaa8", 806435),
			new Remote("flax_model.msgpack", "de82e23a535e25896ebdf433ec2b72706a428fb8e47bb87ef72531426edee8c3", 309944974));

	/** Total download in bytes, for the button text. */
	public static final long DOWNLOAD_BYTES = FILES.stream().mapToLong(Remote::size).sum();

	public enum State {
		MISSING,
		DOWNLOADING,
		CONVERTING,
		LOADING,
		READY,
		FAILED
	}

	private volatile State state = State.MISSING;
	private volatile long done;
	private volatile @Nullable String error;
	private volatile @Nullable OpusMt model;
	private Thread worker;

	public static Path dir() {
		return CakeText.dir().resolve("models").resolve(ID);
	}

	public State state() {
		return state;
	}

	public @Nullable OpusMt model() {
		return state == State.READY ? model : null;
	}

	public boolean installed() {
		return Files.exists(dir().resolve(OpusMt.MODEL_FILE)) && Files.exists(dir().resolve(OpusMt.SPM_FILE));
	}

	/** One line for the settings screen and {@code /cttranslate status}. */
	public Text describe() {
		return switch (state) {
			case MISSING -> Text.translatable("caketext_translator.model.missing", DOWNLOAD_BYTES >> 20);
			case DOWNLOADING -> Text.translatable("caketext_translator.model.downloading", done >> 20, DOWNLOAD_BYTES >> 20);
			case CONVERTING -> Text.translatable("caketext_translator.model.converting");
			case LOADING -> Text.translatable("caketext_translator.model.loading");
			case READY -> Text.translatable("caketext_translator.model.ready", LABEL);
			case FAILED -> Text.translatable("caketext_translator.model.failed", String.valueOf(error));
		};
	}

	/** Loads the model in the background if it's installed. */
	public synchronized void loadIfInstalled() {
		if (installed() && (state == State.MISSING || state == State.FAILED)) {
			start(this::load);
		}
	}

	/** Downloads, converts and loads, in the background. */
	public synchronized void install() {
		if (state == State.DOWNLOADING || state == State.CONVERTING || state == State.LOADING) {
			return;
		}
		start(() -> {
			if (!installed()) {
				download();
			}
			load();
		});
	}

	public synchronized void delete() {
		if (worker != null && worker.isAlive()) {
			return;
		}
		model = null;
		state = State.MISSING;
		deleteTree(dir());
	}

	private interface Job {
		void run() throws Exception;
	}

	private void start(Job job) {
		worker = new Thread(() -> {
			try {
				job.run();
			} catch (Throwable t) {
				error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				state = State.FAILED;
				CakeText.LOG.warn("Translation model: {}", error, t);
			}
		}, "CakeText-model");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY);
		worker.start();
	}

	private void load() throws IOException {
		state = State.LOADING;
		// a download folder left by an earlier install (Windows keeps a memory-mapped file locked until it's released)
		Path leftover = dir().resolve("download");
		if (Files.exists(leftover) && installed()) {
			System.gc();
			deleteTree(leftover);
		}
		long t = System.nanoTime();
		model = OpusMt.load(dir());
		state = State.READY;
		CakeText.LOG.info("Loaded {} in {} ms ({} MB of weights)", LABEL, (System.nanoTime() - t) / 1_000_000,
				model.weightBytes() >> 20);
	}

	private void download() throws Exception {
		state = State.DOWNLOADING;
		done = 0;
		Path tmp = dir().resolve("download");
		Files.createDirectories(tmp);
		HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20)).build();
		for (Remote f : FILES) {
			Path target = tmp.resolve(f.name);
			if (Files.exists(target) && Files.size(target) == f.size && f.sha256.equals(sha256(target))) {
				done += f.size;
				continue;
			}
			URI uri = URI.create("https://huggingface.co/" + REPO + "/resolve/" + REVISION + "/" + f.name);
			HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", "CakeText-Translator")
					.timeout(Duration.ofMinutes(30)).GET().build();
			HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException(f.name + ": HTTP " + response.statusCode());
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
				byte[] buf = new byte[1 << 16];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
					digest.update(buf, 0, n);
					done += n;
				}
			}
			String got = HexFormat.of().formatHex(digest.digest());
			if (!got.equals(f.sha256)) {
				Files.deleteIfExists(target);
				throw new IOException(f.name + ": checksum mismatch");
			}
		}
		state = State.CONVERTING;
		OpusMt.convert(tmp, dir(), true, LABEL, REPO + "@" + REVISION);
		// the converter memory-maps the checkpoint; let the mapping go before deleting it (load() retries)
		System.gc();
		deleteTree(tmp);
	}

	private static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[1 << 16];
			int n;
			while ((n = in.read(buf)) > 0) {
				digest.update(buf, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static void deleteTree(Path root) {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					CakeText.LOG.warn("Could not delete {}", p);
				}
			});
		} catch (IOException e) {
			CakeText.LOG.warn("Could not delete {}", root);
		}
	}
}
