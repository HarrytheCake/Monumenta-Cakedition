package io.github.harrythecake.cakeupdater;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Rebuilds the packwiz pack from a .mrpack, so packwiz-installer can update from Modrinth exactly as it does from
 * GitHub. Plain Java (it runs in {@link Helper}, outside the game: no Gson).
 *
 * <p>The .mrpack (built by Cakedition's tools/mrpack.py) carries the pack's own files in
 * {@code overrides/config/cakeupdater/packwiz/}: pack.toml, index.toml, every metafile, and files.tsv, which says
 * where every other pack file is: {@code <path>\t<sha256>\tmrpack:<zip entry>} or {@code \turl:<url>} (large files,
 * linked instead of bundled). A linked file that the install already has with the right hash is copied from there;
 * only changed ones are downloaded. Every file is checked against its sha256 before the pack is used.
 */
final class MrpackPack {
	static final String PACKWIZ = "overrides/config/cakeupdater/packwiz/";
	static final String TSV = PACKWIZ + "files.tsv";

	private MrpackPack() {}

	/** Downloads the .mrpack, checks it, and rebuilds the pack in {@code work}; returns its pack.toml. */
	static Path download(String url, String sha512, Path gameDir, Path work, String userAgent)
			throws IOException, InterruptedException {
		byte[] mrpack = get(url, userAgent);
		if (!hash("SHA-512", mrpack).equalsIgnoreCase(sha512)) {
			throw new IOException("the downloaded pack file doesn't match Modrinth's sha512");
		}
		return rebuild(mrpack, gameDir, work, userAgent);
	}

	static Path rebuild(byte[] mrpack, Path gameDir, Path work, String userAgent) throws IOException, InterruptedException {
		Map<String, byte[]> entries = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(mrpack))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
				if (!e.isDirectory()) {
					entries.put(e.getName(), zip.readAllBytes());
				}
			}
		}
		byte[] tsv = entries.get(TSV);
		if (tsv == null || !entries.containsKey(PACKWIZ + "pack.toml")) {
			throw new IOException("the pack file has no packwiz pack inside (built before Cakedition Updater 1.6.0?)");
		}
		Files.createDirectories(work);
		for (Map.Entry<String, byte[]> e : entries.entrySet()) {
			if (e.getKey().startsWith(PACKWIZ) && !e.getKey().equals(TSV)) {
				write(work, e.getKey().substring(PACKWIZ.length()), e.getValue());
			}
		}
		for (String line : new String(tsv, StandardCharsets.UTF_8).split("\n")) {
			if (line.isBlank()) {
				continue;
			}
			String[] cols = line.split("\t", 3);
			if (cols.length != 3) {
				throw new IOException("bad files.tsv line: " + line);
			}
			String rel = cols[0], sha256 = cols[1], where = cols[2];
			Path target = inside(work, rel);
			if (where.startsWith("mrpack:")) {
				byte[] data = entries.get(where.substring("mrpack:".length()));
				if (data == null) {
					throw new IOException("the pack file is missing " + where);
				}
				check(rel, data, sha256);
				write(work, rel, data);
			} else if (where.startsWith("url:")) {
				Path installed = inside(gameDir, rel);
				if (Files.isRegularFile(installed) && hash("SHA-256", Files.readAllBytes(installed)).equalsIgnoreCase(sha256)) {
					Files.createDirectories(target.getParent());
					Files.copy(installed, target, StandardCopyOption.REPLACE_EXISTING);
				} else {
					byte[] data = get(where.substring("url:".length()), userAgent);
					check(rel, data, sha256);
					write(work, rel, data);
				}
			} else {
				throw new IOException("bad files.tsv source: " + where);
			}
		}
		return work.resolve("pack.toml");
	}

	/** {@code root}/{@code rel}, refusing anything that would land outside {@code root}. */
	static Path inside(Path root, String rel) throws IOException {
		Path base = root.toAbsolutePath().normalize();
		Path p = base.resolve(rel).normalize();
		if (rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || rel.contains(":") || !p.startsWith(base)
				|| p.equals(base)) {
			throw new IOException("unsafe path in the pack file: " + rel);
		}
		return p;
	}

	private static void write(Path root, String rel, byte[] data) throws IOException {
		Path p = inside(root, rel);
		Files.createDirectories(p.getParent());
		Files.write(p, data);
	}

	private static void check(String rel, byte[] data, String sha256) throws IOException {
		if (!hash("SHA-256", data).equalsIgnoreCase(sha256)) {
			throw new IOException(rel + " doesn't match its hash in the pack");
		}
	}

	private static byte[] get(String url, String userAgent) throws IOException, InterruptedException {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5))
				.header("User-Agent", userAgent).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 200) {
			throw new IOException("HTTP " + r.statusCode() + " from " + url);
		}
		return r.body();
	}

	static String hash(String algorithm, byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Deletes the work folder; best effort. */
	static void delete(Path dir) {
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException ignored) {
			// a temp folder left behind is harmless
		}
	}
}
