package io.github.harrythecake.caketext.translator.mt;

import java.io.IOException;
import java.io.Reader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Converts a Hugging Face MarianMT checkpoint (Helsinki-NLP opus-mt) to {@code model.ctm}.
 * Reads {@code config.json}, {@code vocab.json} and {@code flax_model.msgpack}; matrices become int8
 * (or stay float32 with {@code int8 = false}, which is only for checking the Java port against the reference).
 */
final class FlaxConverter {
	private FlaxConverter() {
	}

	static void convert(Path hfDir, Path outFile, boolean int8, String label, String revision) throws IOException {
		Gson gson = new Gson();
		JsonObject hf;
		try (Reader r = Files.newBufferedReader(hfDir.resolve("config.json"), StandardCharsets.UTF_8)) {
			hf = gson.fromJson(r, JsonObject.class);
		}
		JsonObject vocabJson;
		try (Reader r = Files.newBufferedReader(hfDir.resolve("vocab.json"), StandardCharsets.UTF_8)) {
			vocabJson = gson.fromJson(r, JsonObject.class);
		}
		int vocabSize = hf.get("vocab_size").getAsInt();
		String[] vocab = new String[vocabSize];
		for (var e : vocabJson.entrySet()) {
			int id = e.getValue().getAsInt();
			if (id < vocabSize) {
				vocab[id] = e.getKey();
			}
		}
		for (int i = 0; i < vocabSize; i++) {
			if (vocab[i] == null) {
				vocab[i] = "<pad>";
			}
		}

		if (!"swish".equals(hf.get("activation_function").getAsString()) || hf.get("normalize_before").getAsBoolean()) {
			throw new IOException("unsupported Marian variant (activation or pre-norm)");
		}
		int d = hf.get("d_model").getAsInt();
		JsonObject config = new JsonObject();
		config.addProperty("label", label);
		config.addProperty("revision", revision);
		config.addProperty("d_model", d);
		config.addProperty("heads", hf.get("encoder_attention_heads").getAsInt());
		config.addProperty("ffn", hf.get("encoder_ffn_dim").getAsInt());
		config.addProperty("encoder_layers", hf.get("encoder_layers").getAsInt());
		config.addProperty("decoder_layers", hf.get("decoder_layers").getAsInt());
		config.addProperty("vocab_size", vocabSize);
		config.addProperty("max_positions", hf.get("max_position_embeddings").getAsInt());
		config.addProperty("scale_embedding", hf.get("scale_embedding").getAsBoolean());
		config.addProperty("pad", hf.get("pad_token_id").getAsInt());
		config.addProperty("eos", hf.get("eos_token_id").getAsInt());
		config.addProperty("decoder_start", hf.get("decoder_start_token_id").getAsInt());
		config.addProperty("num_beams", hf.has("num_beams") ? hf.get("num_beams").getAsInt() : 4);

		Map<String, ModelFile.Tensor> tensors = new LinkedHashMap<>();
		try (FileChannel ch = FileChannel.open(hfDir.resolve("flax_model.msgpack"), StandardOpenOption.READ)) {
			MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
			@SuppressWarnings("unchecked")
			Map<String, Object> root = (Map<String, Object>) Msgpack.parse(buf);
			Map<String, Object> model = sub(root, "model");

			Msgpack.NdArray embed = nd(sub(model, "shared"), "embedding");
			tensors.put("embed", matrix("embed", embed.toFloats(), embed.shape()[0], embed.shape()[1], int8));
			float[] bias = nd(root, "final_logits_bias").toFloats();
			tensors.put("final_logits_bias", ModelFile.Tensor.f32("final_logits_bias", 1, bias.length, bias));

			int encLayers = config.get("encoder_layers").getAsInt();
			for (int i = 0; i < encLayers; i++) {
				Map<String, Object> l = sub(sub(sub(model, "encoder"), "layers"), String.valueOf(i));
				String p = "enc." + i + ".";
				attention(tensors, p, sub(l, "self_attn"), "", int8);
				norm(tensors, p + "ln1", sub(l, "self_attn_layer_norm"));
				dense(tensors, p + "fc1", sub(l, "fc1"), int8);
				dense(tensors, p + "fc2", sub(l, "fc2"), int8);
				norm(tensors, p + "ln2", sub(l, "final_layer_norm"));
			}
			int decLayers = config.get("decoder_layers").getAsInt();
			for (int i = 0; i < decLayers; i++) {
				Map<String, Object> l = sub(sub(sub(model, "decoder"), "layers"), String.valueOf(i));
				String p = "dec." + i + ".";
				attention(tensors, p, sub(l, "self_attn"), "", int8);
				norm(tensors, p + "ln1", sub(l, "self_attn_layer_norm"));
				attention(tensors, p, sub(l, "encoder_attn"), "x", int8);
				norm(tensors, p + "ln2", sub(l, "encoder_attn_layer_norm"));
				dense(tensors, p + "fc1", sub(l, "fc1"), int8);
				dense(tensors, p + "fc2", sub(l, "fc2"), int8);
				norm(tensors, p + "ln3", sub(l, "final_layer_norm"));
			}
		}
		Files.createDirectories(outFile.getParent());
		new ModelFile(config, new ArrayList<>(Arrays.asList(vocab)), tensors).write(outFile);
	}

	private static void attention(Map<String, ModelFile.Tensor> t, String p, Map<String, Object> attn, String x, boolean int8)
			throws IOException {
		dense(t, p + x + "q", sub(attn, "q_proj"), int8);
		dense(t, p + x + "k", sub(attn, "k_proj"), int8);
		dense(t, p + x + "v", sub(attn, "v_proj"), int8);
		dense(t, p + x + "o", sub(attn, "out_proj"), int8);
	}

	/** Flax kernel is (in, out); we store (out, in) so each output is one contiguous row. */
	private static void dense(Map<String, ModelFile.Tensor> t, String name, Map<String, Object> layer, boolean int8)
			throws IOException {
		Msgpack.NdArray kernel = nd(layer, "kernel");
		int in = kernel.shape()[0];
		int out = kernel.shape()[1];
		float[] k = kernel.toFloats();
		float[] tr = new float[k.length];
		for (int i = 0; i < in; i++) {
			for (int o = 0; o < out; o++) {
				tr[o * in + i] = k[i * out + o];
			}
		}
		t.put(name, matrix(name, tr, out, in, int8));
		float[] b = nd(layer, "bias").toFloats();
		t.put(name + ".b", ModelFile.Tensor.f32(name + ".b", 1, b.length, b));
	}

	private static void norm(Map<String, ModelFile.Tensor> t, String name, Map<String, Object> layer) throws IOException {
		float[] g = nd(layer, "scale").toFloats();
		float[] b = nd(layer, "bias").toFloats();
		t.put(name + ".g", ModelFile.Tensor.f32(name + ".g", 1, g.length, g));
		t.put(name + ".b", ModelFile.Tensor.f32(name + ".b", 1, b.length, b));
	}

	private static ModelFile.Tensor matrix(String name, float[] f, int rows, int cols, boolean int8) {
		return int8 ? ModelFile.Tensor.int8(name, rows, cols, f) : ModelFile.Tensor.f32(name, rows, cols, f);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> sub(Map<String, Object> m, String key) throws IOException {
		Object v = m.get(key);
		if (!(v instanceof Map)) {
			throw new IOException("checkpoint has no '" + key + "'");
		}
		return (Map<String, Object>) v;
	}

	private static Msgpack.NdArray nd(Map<String, Object> m, String key) throws IOException {
		Object v = m.get(key);
		if (!(v instanceof Msgpack.NdArray a)) {
			throw new IOException("checkpoint has no array '" + key + "'");
		}
		return a;
	}
}
