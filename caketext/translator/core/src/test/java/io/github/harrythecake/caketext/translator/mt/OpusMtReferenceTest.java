package io.github.harrythecake.caketext.translator.mt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Checks the Java port against Hugging Face transformers. Needs, as system properties:
 * {@code caketext.hfModel} (a downloaded Helsinki-NLP/opus-mt-en-zh folder with flax_model.msgpack),
 * {@code caketext.hfReference} (JSON from hf_reference.py) and {@code caketext.workDir} (for converted models).
 * Skipped otherwise.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpusMtReferenceTest {
	private static final String LANG = ">>cmn_Hans<<";

	private static Path prop(String name) {
		String v = System.getProperty(name, "");
		return v.isEmpty() ? null : Path.of(v);
	}

	private static List<JsonObject> reference() throws Exception {
		try (Reader r = Files.newBufferedReader(prop("caketext.hfReference"), StandardCharsets.UTF_8)) {
			List<JsonObject> rows = new ArrayList<>();
			for (JsonElement e : new Gson().fromJson(r, JsonArray.class)) {
				rows.add(e.getAsJsonObject());
			}
			return rows;
		}
	}

	private static int[] ints(JsonArray a, boolean dropStart, boolean dropEos) {
		List<Integer> out = new ArrayList<>();
		for (JsonElement e : a) {
			out.add(e.getAsInt());
		}
		if (dropStart && !out.isEmpty() && out.get(0) == 65000) {
			out.remove(0);
		}
		while (dropEos && !out.isEmpty() && (out.get(out.size() - 1) == 0 || out.get(out.size() - 1) == 65000)) {
			out.remove(out.size() - 1);
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	private static Path model(boolean int8) throws Exception {
		Path hf = prop("caketext.hfModel");
		Path work = prop("caketext.workDir");
		assumeTrue(hf != null && work != null && prop("caketext.hfReference") != null, "reference files not configured");
		Path dir = work.resolve(int8 ? "opus-mt-en-zh-int8" : "opus-mt-en-zh-f32");
		if (!Files.exists(dir.resolve(OpusMt.MODEL_FILE))) {
			long t = System.nanoTime();
			OpusMt.convert(hf, dir, int8, int8 ? "opus-mt-en-zh int8" : "opus-mt-en-zh f32", "test");
			System.out.printf("converted %s in %.1f s, %d MB%n", dir.getFileName(), (System.nanoTime() - t) / 1e9,
					Files.size(dir.resolve(OpusMt.MODEL_FILE)) >> 20);
		}
		return dir;
	}

	@Test
	@Order(1)
	void tokenizerMatchesReference() throws Exception {
		OpusMt mt = OpusMt.load(model(true));
		for (JsonObject row : reference()) {
			String text = row.get("text").getAsString();
			int[] expected = ints(row.getAsJsonArray("input_ids"), false, false);
			int[] actual = mt.encode(text, LANG);
			assertArrayEquals(expected, actual, () -> "tokens of '" + text + "': " + mt.pieces(actual));
		}
	}

	@Test
	@Order(2)
	void float32MatchesReferenceExactly() throws Exception {
		OpusMt mt = OpusMt.load(model(false));
		int greedyOk = 0;
		int beamOk = 0;
		List<JsonObject> rows = reference();
		for (JsonObject row : rows) {
			String text = row.get("text").getAsString();
			int[] greedy = mt.generate(text, LANG, 1, 128);
			int[] beam = mt.generate(text, LANG, 4, 128);
			boolean g = java.util.Arrays.equals(ints(row.getAsJsonArray("greedy_ids"), true, true), greedy);
			boolean b = java.util.Arrays.equals(ints(row.getAsJsonArray("beam_ids"), true, true), beam);
			greedyOk += g ? 1 : 0;
			beamOk += b ? 1 : 0;
			System.out.printf("%s %s %-40s greedy=%s | beam=%s%n", g ? "ok  " : "DIFF", b ? "ok  " : "DIFF",
					"'" + text + "'", mt.decode(greedy), mt.decode(beam));
			if (!g || !b) {
				System.out.println("      reference: greedy=" + row.get("greedy").getAsString() + " | beam=" + row.get("beam").getAsString());
			}
		}
		assertEquals(rows.size(), greedyOk, "greedy matches");
		assertEquals(rows.size(), beamOk, "beam matches");
	}

	@Test
	@Order(3)
	void int8IsCloseAndFast() throws Exception {
		Path dir = model(true);
		Runtime rt = Runtime.getRuntime();
		System.gc();
		long before = rt.totalMemory() - rt.freeMemory();
		OpusMt mt = OpusMt.load(dir);
		System.gc();
		long after = rt.totalMemory() - rt.freeMemory();
		System.out.printf("int8 model: file %d MB, heap +%d MB%n", Files.size(dir.resolve(OpusMt.MODEL_FILE)) >> 20,
				(after - before) >> 20);
		List<JsonObject> rows = reference();
		int sameDefault = 0;
		// variant: activations float | int8 everywhere | int8 but float logits | int8 but float logits and fc2
		String[] names = {"float", "int8 all", "int8, float logits", "int8, float logits+fc2"};
		for (int variant = 3; variant >= 0; variant--) {
			Linear.quantizeInputs = variant != 0;
			Marian.preciseLogits = variant >= 2;
			Marian.preciseFc2 = variant == 3;
			mt = OpusMt.load(dir);
			String name = names[variant];
			boolean quantizeInputs = variant != 0;
			for (int beams : new int[] {4, 2, 1}) {
				for (int w = 0; w < 4; w++) {
					mt.translate("Warm up the JIT, please, with a longer sentence number " + w + ".", LANG, beams);
				}
				int same = 0;
				long total = 0;
				long worst = 0;
				StringBuilder diffs = new StringBuilder();
				for (JsonObject row : rows) {
					String text = row.get("text").getAsString();
					long t = System.nanoTime();
					String out = mt.translate(text, LANG, beams);
					long ms = (System.nanoTime() - t) / 1_000_000;
					total += ms;
					worst = Math.max(worst, ms);
					String ref = row.get(beams == 1 ? "greedy" : "beam").getAsString();
					if (out.equals(ref)) {
						same++;
					} else {
						diffs.append(String.format("      '%s': %s (reference %s)%n", text, out, ref));
					}
				}
				System.out.printf("%-24s beams %d: %2d/%d same as reference%s, %5d ms total, slowest %4d ms%n%s",
						name, beams, same, rows.size(), beams == 2 ? " (beam 4)" : "", total, worst, diffs);
				if (quantizeInputs && beams == 4) {
					sameDefault = same;
				}
			}
		}
		Linear.quantizeInputs = true;
		Marian.preciseLogits = true;
		Marian.preciseFc2 = false;
		assumeTrue(sameDefault * 2 >= rows.size(), "int8 drifted from the reference more than expected");
	}
}
