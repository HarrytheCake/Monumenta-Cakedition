package io.github.harrythecake.caketext.translator.mt;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Our converted model file ({@code model.ctm}):
 * <pre>
 * "CTMODEL1" | int32 LE header length | header JSON | tensors in header order
 * </pre>
 * The header holds {@code config} (dimensions, special ids, labels), {@code vocab} (pieces by id) and
 * {@code tensors} ({@code name}, {@code dtype} {@code i8|f32}, {@code shape [rows, cols]}).
 * An i8 tensor is rows×cols signed bytes followed by rows float32 scales; f32 is rows×cols float32. All little-endian.
 */
final class ModelFile {
	static final String MAGIC = "CTMODEL1";
	private static final Gson GSON = new Gson();

	/** One tensor before writing: int8 (q + scale) or float32 (f). */
	record Tensor(String name, int rows, int cols, byte[] q, float[] scale, float[] f) {
		static Tensor f32(String name, int rows, int cols, float[] f) {
			return new Tensor(name, rows, cols, null, null, f);
		}

		/** Symmetric int8, one scale per row. */
		static Tensor int8(String name, int rows, int cols, float[] f) {
			byte[] q = new byte[rows * cols];
			float[] scale = new float[rows];
			for (int r = 0; r < rows; r++) {
				float max = 0;
				for (int c = 0; c < cols; c++) {
					max = Math.max(max, Math.abs(f[r * cols + c]));
				}
				float s = max == 0 ? 1f : max / 127f;
				scale[r] = s;
				for (int c = 0; c < cols; c++) {
					q[r * cols + c] = (byte) Math.max(-127, Math.min(127, Math.round(f[r * cols + c] / s)));
				}
			}
			return new Tensor(name, rows, cols, q, scale, null);
		}
	}

	final JsonObject config;
	final List<String> vocab;
	final Map<String, Tensor> tensors;

	ModelFile(JsonObject config, List<String> vocab, Map<String, Tensor> tensors) {
		this.config = config;
		this.vocab = vocab;
		this.tensors = tensors;
	}

	void write(Path path) throws IOException {
		JsonObject header = new JsonObject();
		header.add("config", config);
		header.add("vocab", GSON.toJsonTree(vocab));
		List<Map<String, Object>> list = new ArrayList<>();
		for (Tensor t : tensors.values()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("name", t.name());
			m.put("dtype", t.q() != null ? "i8" : "f32");
			m.put("shape", new int[] {t.rows(), t.cols()});
			list.add(m);
		}
		header.add("tensors", GSON.toJsonTree(list));
		byte[] json = GSON.toJson(header).getBytes(StandardCharsets.UTF_8);
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 20)) {
			out.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
			out.write(le(ByteBuffer.allocate(4)).putInt(json.length).array());
			out.write(json);
			for (Tensor t : tensors.values()) {
				if (t.q() != null) {
					out.write(t.q());
					writeFloats(out, t.scale());
				} else {
					writeFloats(out, t.f());
				}
			}
		}
		Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	static ModelFile read(Path path) throws IOException {
		try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(path), 1 << 20))) {
			byte[] magic = new byte[8];
			in.readFully(magic);
			if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII))) {
				throw new IOException("not a CakeText model file: " + path);
			}
			byte[] len = new byte[4];
			in.readFully(len);
			byte[] json = new byte[le(ByteBuffer.wrap(len)).getInt()];
			in.readFully(json);
			JsonObject header = GSON.fromJson(new String(json, StandardCharsets.UTF_8), JsonObject.class);
			List<String> vocab = new ArrayList<>();
			header.getAsJsonArray("vocab").forEach(e -> vocab.add(e.getAsString()));
			Map<String, Tensor> tensors = new LinkedHashMap<>();
			for (var e : header.getAsJsonArray("tensors")) {
				JsonObject t = e.getAsJsonObject();
				String name = t.get("name").getAsString();
				int rows = t.getAsJsonArray("shape").get(0).getAsInt();
				int cols = t.getAsJsonArray("shape").get(1).getAsInt();
				if (t.get("dtype").getAsString().equals("i8")) {
					byte[] q = new byte[rows * cols];
					in.readFully(q);
					tensors.put(name, new Tensor(name, rows, cols, q, readFloats(in, rows), null));
				} else {
					tensors.put(name, new Tensor(name, rows, cols, null, null, readFloats(in, rows * cols)));
				}
			}
			return new ModelFile(header.getAsJsonObject("config"), vocab, tensors);
		}
	}

	private static ByteBuffer le(ByteBuffer b) {
		return b.order(ByteOrder.LITTLE_ENDIAN);
	}

	private static void writeFloats(OutputStream out, float[] f) throws IOException {
		ByteBuffer b = le(ByteBuffer.allocate(f.length * 4));
		b.asFloatBuffer().put(f);
		out.write(b.array());
	}

	private static float[] readFloats(InputStream in, int n) throws IOException {
		byte[] raw = new byte[n * 4];
		new DataInputStream(in).readFully(raw);
		float[] f = new float[n];
		le(ByteBuffer.wrap(raw)).asFloatBuffer().get(f);
		return f;
	}
}
