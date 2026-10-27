package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the published pack.toml with what is installed.
 * <ul>
 *   <li>Installed by packwiz-installer: packwiz.json stores the sha256 of the pack.toml it installed,
 *       so any published change counts as an update.</li>
 *   <li>Installed some other way (e.g. a .mrpack): compare {@code version} in pack.toml with
 *       config/cakeupdater/pack-version.txt, which the pack ships.</li>
 * </ul>
 */
final class UpdateCheck {
	record Result(String remoteVersion, String localVersion, String remoteHash, boolean available) {}

	private static final Pattern VERSION = Pattern.compile("^version\\s*=\\s*\"([^\"]*)\"", Pattern.MULTILINE);

	private UpdateCheck() {}

	/** Completes with null if the check failed (offline, GitHub down, ...). */
	static CompletableFuture<Result> runAsync(String packUrl, Path gameDir) {
		return Net.get(packUrl, Duration.ofSeconds(15))
				.thenApply(body -> {
					Result result = compare(body, gameDir);
					CakeUpdater.LOGGER.info("Modpack update check: {}", result);
					return result;
				})
				.exceptionally(e -> {
					CakeUpdater.LOGGER.warn("Modpack update check failed: {}", e.toString());
					return null;
				});
	}

	static Result compare(byte[] packToml, Path gameDir) {
		String remoteHash = hash("SHA-256", packToml);
		Matcher m = VERSION.matcher(new String(packToml, StandardCharsets.UTF_8));
		String remoteVersion = m.find() ? m.group(1) : "?";
		String localVersion = readLocalVersion(gameDir);
		String installedHash = readInstalledHash(gameDir);

		boolean available;
		if (installedHash != null) {
			available = !installedHash.equalsIgnoreCase(remoteHash);
		} else if (localVersion != null) {
			available = !localVersion.equals(remoteVersion);
		} else {
			available = false;
		}
		return new Result(remoteVersion, localVersion, remoteHash, available);
	}

	/** What is installed, to notice an update between two starts: the packwiz.json hash, else the pack version. */
	static String installedIdentity(Path gameDir) {
		String hash = readInstalledHash(gameDir);
		if (hash != null) {
			return "packwiz:" + hash;
		}
		String version = readLocalVersion(gameDir);
		return version == null ? null : "version:" + version;
	}

	static String readInstalledHash(Path gameDir) {
		try {
			Path manifest = gameDir.resolve("packwiz.json");
			if (!Files.exists(manifest)) {
				return null;
			}
			JsonObject hash = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject()
					.getAsJsonObject("packFileHash");
			return hash != null && "sha256".equals(hash.get("type").getAsString()) ? hash.get("value").getAsString() : null;
		} catch (Exception e) {
			CakeUpdater.LOGGER.warn("Could not read packwiz.json: {}", e.toString());
			return null;
		}
	}

	static String readLocalVersion(Path gameDir) {
		try {
			Path file = gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve("pack-version.txt");
			return Files.exists(file) ? Files.readString(file).trim() : null;
		} catch (Exception e) {
			return null;
		}
	}

	static String hash(String algorithm, byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
