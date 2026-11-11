package io.github.harrythecake.caketext.core.strings;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A file of strings, shared by both mods:
 * <pre>
 * { "format": "caketext/strings", "version": 1, "lang": "zh_cn",
 *   "entries": [ { "src": "Total Damage: {~}", "dst": "总伤害：{~}" }, ... ] }
 * </pre>
 * The collector writes it with {@code dst} empty; translators fill {@code dst}; the translator reads it.
 * A flat {@code { "src": "dst", ... }} object is also accepted, for small hand-written files.
 *
 * <h2>Encoding</h2>
 * Files ending in {@code .gz} are the same JSON, gzip-compressed. Gzip (DEFLATE: LZ77 sliding window + Huffman)
 * is built into Java, so it adds no code to the mods, and collected strings repeat a lot (keys, contexts, the
 * server name), so they shrink several-fold. Readers detect gzip by its magic bytes, not by the name.
 */
public final class StringsFile {
	public static final String FORMAT = "caketext/strings";
	public static final int VERSION = 1;

	private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

	public String format = FORMAT;
	public int version = VERSION;
	/** Language of {@code dst}, e.g. {@code zh_cn}. Empty in collected files. */
	public String lang;
	/** Language of {@code src}. */
	public String source = "en_us";
	/** Server the strings were collected on (host name), when collected. */
	public String server;
	public List<StringEntry> entries = new ArrayList<>();

	public static StringsFile read(Path path) throws IOException {
		try (InputStream in = Files.newInputStream(path)) {
			return read(in);
		}
	}

	public static StringsFile read(InputStream raw) throws IOException {
		BufferedInputStream in = new BufferedInputStream(raw);
		in.mark(2);
		int b1 = in.read();
		int b2 = in.read();
		in.reset();
		InputStream body = b1 == 0x1f && b2 == 0x8b ? new GZIPInputStream(in) : in;
		try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new IOException("not a JSON object");
			}
			JsonObject obj = root.getAsJsonObject();
			if (obj.has("entries")) {
				StringsFile file = COMPACT.fromJson(obj, StringsFile.class);
				if (file.entries == null) {
					file.entries = new ArrayList<>();
				}
				file.entries.removeIf(e -> e == null || e.src == null || e.src.isEmpty());
				return file;
			}
			StringsFile file = new StringsFile();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				if (!e.getKey().startsWith("_") && e.getValue().isJsonPrimitive()) {
					file.entries.add(new StringEntry(e.getKey(), e.getValue().getAsString()));
				}
			}
			return file;
		}
	}

	/** Serialized bytes: pretty JSON, or compact JSON in gzip. */
	public byte[] toBytes(boolean gzip) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (OutputStream out = gzip ? new GZIPOutputStream(bytes, 1 << 16) : bytes) {
			out.write((gzip ? COMPACT : PRETTY).toJson(this).getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

	/** Writes atomically (temp file + move), so a crash never leaves half a file. */
	public void write(Path path, boolean gzip) throws IOException {
		writeAtomically(path, toBytes(gzip));
	}

	public static void writeAtomically(Path path, byte[] data) throws IOException {
		Files.createDirectories(path.getParent());
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.write(tmp, data);
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** {@code .json} or {@code .json.gz}: the names both mods read. */
	public static boolean isStringsFile(Path path) {
		String name = path.getFileName().toString();
		return name.endsWith(".json") || name.endsWith(".json.gz");
	}
}
