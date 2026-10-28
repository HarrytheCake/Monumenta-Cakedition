package io.github.harrythecake.cakewiki.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakewiki.net.WikiClient.Page;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Pages saved on disk, one gzipped JSON file per requested title, so the reader works offline and loads fast. */
public final class PageCache {
	public record Entry(Page page, Instant fetched) {}

	private final Path dir;

	public PageCache(Path dir) {
		this.dir = dir;
	}

	public Optional<Entry> get(String title) {
		Path file = file(title);
		if (!Files.isRegularFile(file)) return Optional.empty();
		try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			Page page = new Page(o.get("title").getAsString(), o.get("displayTitle").getAsString(),
					o.get("revision").getAsLong(), o.get("html").getAsString());
			return Optional.of(new Entry(page, Instant.ofEpochMilli(o.get("fetched").getAsLong())));
		} catch (IOException | RuntimeException e) {
			return Optional.empty(); // unreadable: treat as not cached; the next fetch overwrites it
		}
	}

	/** Saves under the requested title (which may be a redirect) and, if different, the canonical one. */
	public void put(String requested, Page page, Instant fetched) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("title", page.title());
		o.addProperty("displayTitle", page.displayTitle());
		o.addProperty("revision", page.revision());
		o.addProperty("fetched", fetched.toEpochMilli());
		o.addProperty("html", page.html());
		byte[] json = o.toString().getBytes(StandardCharsets.UTF_8);
		write(file(requested), json);
		if (!key(requested).equals(key(page.title()))) write(file(page.title()), json);
	}

	private void write(Path file, byte[] json) throws IOException {
		Files.createDirectories(dir);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
			out.write(json);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private Path file(String title) {
		return dir.resolve(key(title) + ".json.gz");
	}

	/** MediaWiki titles ignore underscores vs spaces and the case of the first letter. */
	static String key(String title) {
		String t = title.replace('_', ' ').trim().replaceAll(" +", " ");
		if (!t.isEmpty()) t = t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
		try {
			byte[] hash = MessageDigest.getInstance("SHA-1").digest(t.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
