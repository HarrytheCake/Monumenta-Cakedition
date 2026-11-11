package io.github.harrythecake.caketext.translator.mt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An opus-mt model ready to translate: SentencePiece source tokenizer, shared vocabulary and the Marian network.
 * Thread-safe for one caller at a time (the translator's single worker thread).
 */
public final class OpusMt {
	public static final String MODEL_FILE = "model.ctm";
	public static final String SPM_FILE = "source.spm";

	private final SentencePiece spm;
	private final Marian model;
	private final Map<String, Integer> ids = new HashMap<>();
	private final List<String> vocab;
	private final int unk;
	private final String label;

	private OpusMt(SentencePiece spm, ModelFile file) {
		this.spm = spm;
		this.model = new Marian(file);
		this.vocab = file.vocab;
		for (int i = 0; i < vocab.size(); i++) {
			ids.putIfAbsent(vocab.get(i), i);
		}
		this.unk = ids.getOrDefault(spm.unknownPiece(), ids.getOrDefault("<unk>", 1));
		this.label = file.config.has("label") ? file.config.get("label").getAsString() : "opus-mt";
	}

	/** Loads {@code model.ctm} and {@code source.spm} from {@code dir}. */
	public static OpusMt load(Path dir) throws IOException {
		SentencePiece spm = SentencePiece.load(Files.readAllBytes(dir.resolve(SPM_FILE)));
		return new OpusMt(spm, ModelFile.read(dir.resolve(MODEL_FILE)));
	}

	/** Shown to players, e.g. {@code opus-mt-en-zh int8}. */
	public String label() {
		return label;
	}

	public long weightBytes() {
		return model.bytes();
	}

	/** Token ids like the Hugging Face MarianTokenizer: [language token] + pieces + eos. */
	public int[] encode(String text, String languageToken) {
		List<Integer> out = new ArrayList<>();
		if (languageToken != null) {
			Integer lang = ids.get(languageToken);
			if (lang == null) {
				throw new IllegalArgumentException("model has no language token " + languageToken);
			}
			out.add(lang);
		}
		for (String piece : spm.encode(text)) {
			out.add(ids.getOrDefault(piece, unk));
		}
		out.add(model.eos);
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	public List<String> pieces(int[] tokenIds) {
		List<String> out = new ArrayList<>();
		for (int id : tokenIds) {
			out.add(id >= 0 && id < vocab.size() ? vocab.get(id) : "<unk>");
		}
		return out;
	}

	/** Text of generated ids, special tokens dropped (like {@code decode(skip_special_tokens=True)}). */
	public String decode(int[] tokenIds) {
		StringBuilder sb = new StringBuilder();
		for (int id : tokenIds) {
			if (id == model.eos || id == model.pad || id == unk || id < 0 || id >= vocab.size()) {
				continue;
			}
			String piece = vocab.get(id);
			if (piece.startsWith(">>") && piece.endsWith("<<")) {
				continue;
			}
			sb.append(piece);
		}
		return sb.toString().replace('▁', ' ').strip();
	}

	/** Generated ids for {@code text}; {@code beams} 1 = greedy. */
	public int[] generate(String text, String languageToken, int beams, int maxNewTokens) {
		int[] src = encode(text, languageToken);
		return model.beam(src, beams, maxNewTokens);
	}

	/**
	 * Measured on the reference sentences (int8 weights): BEST = float activations and the model's 4 beams,
	 * ~0.6 s a line, closest to the full model; FAST = int8 activations and greedy search, ~0.08 s a line,
	 * a bit rougher.
	 */
	public enum Quality {
		BEST,
		FAST
	}

	/** Translates one sentence. Output length is capped at about three times the input. */
	public String translate(String text, String languageToken, Quality quality) {
		Linear.quantizeInputs = quality == Quality.FAST;
		return translate(text, languageToken, quality == Quality.FAST ? 1 : model.defaultBeams);
	}

	/** Translates with {@code beams} (1 = greedy; 0 = the model's default). */
	public String translate(String text, String languageToken, int beams) {
		int[] src = encode(text, languageToken);
		int maxNew = Math.min(256, src.length * 3 + 10);
		return decode(model.beam(src, beams > 0 ? beams : model.defaultBeams, maxNew));
	}

	/** Writes {@code model.ctm} and copies {@code source.spm} from a Hugging Face checkpoint folder. */
	public static void convert(Path hfDir, Path outDir, boolean int8, String label, String revision) throws IOException {
		Files.createDirectories(outDir);
		FlaxConverter.convert(hfDir, outDir.resolve(MODEL_FILE), int8, label, revision);
		Files.copy(hfDir.resolve(SPM_FILE), outDir.resolve(SPM_FILE), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}
}
