package io.github.harrythecake.caketext.translator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import io.github.harrythecake.caketext.core.CakeText;
import io.github.harrythecake.caketext.translator.mt.OpusMt;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The local translation model: opus-mt-en-zh (Helsinki-NLP, Apache-2.0), converted to int8 on the player's PC.
 *
 * <p>The mod downloads nothing. The player downloads the four Hugging Face files of a pinned revision (~312 MB,
 * {@link #PAGE_URL}) into {@link #importDir()} and presses Install (or runs {@code /cttranslate model install}):
 * each file's size and SHA-256 are checked, they're converted to {@code model.ctm} (~75 MB), and then deleted.
 */
public final class ModelManager {
	public static final String ID = "opus-mt-en-zh-int8";
	public static final String LABEL = "opus-mt-en-zh int8";
	private static final String REPO = "Helsinki-NLP/opus-mt-en-zh";
	private static final String REVISION = "408d9bc410a388e1d9aef112a2daba955b945255";
	/** Where the player downloads the files: the model's file list at the pinned revision. */
	public static final String PAGE_URL = "https://huggingface.co/" + REPO + "/tree/" + REVISION;

	private record Remote(String name, String sha256, long size) {
	}

	private static final List<Remote> FILES = List.of(
			new Remote("config.json", "bcae8ed74fed77fb51c58462b62397fee6b1a1a34aece79183a0dd02ad329e71", 1403),
			new Remote("vocab.json", "f1832ca38f7aa158b9a944ff583e4901e6b37d9d4ad9241f03838e636e9cfb03", 1617791),
			new Remote("source.spm", "5775ddc9e3ff2fae91554da56468ad35ff56edaba870fea74447bc7234bfdaa8", 806435),
			new Remote("flax_model.msgpack", "de82e23a535e25896ebdf433ec2b72706a428fb8e47bb87ef72531426edee8c3", 309944974));

	/** Total size of the files the player downloads, for the UI. */
	public static final long DOWNLOAD_BYTES = FILES.stream().mapToLong(Remote::size).sum();
	/** The file names, for the UI. */
	public static final String FILE_NAMES = String.join(", ", FILES.stream().map(Remote::name).toList());

	public enum State {
		MISSING,
		CHECKING,
		CONVERTING,
		LOADING,
		READY,
		FAILED
	}

	private volatile State state = State.MISSING;
	private volatile long done;
	private volatile @Nullable Text error;
	/** Files still missing from {@link #importDir()} after the last Install, or null. */
	private volatile @Nullable String missingFiles;
	private volatile @Nullable OpusMt model;
	private Thread worker;

	public static Path dir() {
		return CakeText.dir().resolve("models").resolve(ID);
	}

	/** Where the player puts the downloaded files ({@code config/caketext/models/opus-mt-en-zh}). */
	public static Path importDir() {
		return CakeText.dir().resolve("models").resolve("opus-mt-en-zh");
	}

	/** Creates {@link #importDir()} and opens it in the file manager. */
	public static void openImportDir() {
		try {
			Files.createDirectories(importDir());
		} catch (IOException ignored) {
			// opening still shows what exists
		}
		Util.getOperatingSystem().open(importDir().toFile());
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
			case MISSING -> missingFiles != null
					? Text.translatable("caketext_translator.model.incomplete", missingFiles)
					: Text.translatable("caketext_translator.model.missing", DOWNLOAD_BYTES >> 20);
			case CHECKING -> Text.translatable("caketext_translator.model.checking", done >> 20, DOWNLOAD_BYTES >> 20);
			case CONVERTING -> Text.translatable("caketext_translator.model.converting");
			case LOADING -> Text.translatable("caketext_translator.model.loading");
			case READY -> Text.translatable("caketext_translator.model.ready", LABEL);
			case FAILED -> Text.translatable("caketext_translator.model.failed", error == null ? Text.literal("?") : error);
		};
	}

	/** Loads the model in the background if it's installed. */
	public synchronized void loadIfInstalled() {
		if (installed() && (state == State.MISSING || state == State.FAILED)) {
			start(this::load);
		}
	}

	/** Checks the files in {@link #importDir()}, converts and loads them, in the background. */
	public synchronized void install() {
		if (state == State.CHECKING || state == State.CONVERTING || state == State.LOADING) {
			return;
		}
		start(() -> {
			if (!installed() && !importFiles()) {
				return;
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
				error = Text.literal(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
				state = State.FAILED;
				CakeText.LOG.warn("Translation model: {}", error.getString(), t);
			}
		}, "CakeText-model");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY);
		worker.start();
	}

	private void load() throws IOException {
		state = State.LOADING;
		missingFiles = null;
		// source files an earlier install couldn't delete (Windows keeps a memory-mapped file locked until it's
		// released), and the download folder of versions before 0.2.0
		if (installed()) {
			System.gc();
			deleteSources();
			deleteTree(dir().resolve("download"));
		}
		long t = System.nanoTime();
		model = OpusMt.load(dir());
		state = State.READY;
		CakeText.LOG.info("Loaded {} in {} ms ({} MB of weights)", LABEL, (System.nanoTime() - t) / 1_000_000,
				model.weightBytes() >> 20);
	}

	/** Checks the player's files and converts them; false (state MISSING or FAILED) if they aren't all right. */
	private boolean importFiles() throws Exception {
		state = State.CHECKING;
		done = 0;
		Path src = importDir();
		Files.createDirectories(src);
		List<String> missing = new ArrayList<>();
		for (Remote f : FILES) {
			if (!Files.isRegularFile(src.resolve(f.name))) {
				missing.add(f.name);
			}
		}
		if (!missing.isEmpty()) {
			missingFiles = String.join(", ", missing);
			state = State.MISSING;
			return false;
		}
		missingFiles = null;
		for (Remote f : FILES) {
			Path file = src.resolve(f.name);
			if (Files.size(file) != f.size || !f.sha256.equals(sha256(file))) {
				error = Text.translatable("caketext_translator.model.bad_file", f.name);
				state = State.FAILED;
				return false;
			}
			done += f.size;
		}
		state = State.CONVERTING;
		OpusMt.convert(src, dir(), true, LABEL, REPO + "@" + REVISION);
		// the converter memory-maps the checkpoint; let the mapping go before deleting it (load() retries)
		System.gc();
		deleteSources();
		return true;
	}

	/** Deletes the player's four files once they're converted (only those: the folder may hold other things). */
	private static void deleteSources() {
		for (Remote f : FILES) {
			try {
				Files.deleteIfExists(importDir().resolve(f.name));
			} catch (IOException e) {
				CakeText.LOG.warn("Could not delete {} (deleted on the next load)", f.name);
			}
		}
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
