package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Is a newer modpack published? Asked in this order (1.6.0+), the first answer wins:
 * <ol>
 *   <li><b>Modrinth</b> ({@code packwiz.modrinthProject}): the project's newest release for this game version and
 *       Fabric, compared with config/cakeupdater/pack-version.txt (which the pack ships). Its .mrpack is then what the
 *       updater downloads, so the update counts as a Modrinth download.</li>
 *   <li>Each <b>pack.toml</b> address ({@code packUrl}, then {@code fallbackUrls}: GitHub, then GitLab).
 *       Installed by packwiz-installer: packwiz.json stores the sha256 of the pack.toml it installed, so any published
 *       change counts as an update. Installed some other way (a .mrpack): compare {@code version} with
 *       pack-version.txt.</li>
 * </ol>
 * The withdrawn-versions list is read from the first pack.toml address that has it.
 */
final class UpdateCheck {
	/** A Modrinth version's pack file: what the updater downloads first. */
	record Mrpack(String versionId, String url, String sha512) {}

	/**
	 * {@code withdrawn}: the installed version's withdrawal reasons by language when an update is available and the
	 * published pack lists that version as withdrawn (an urgent update), else null. {@code mrpack}: set when Modrinth
	 * answered. {@code packUrl}: the pack.toml address that answered (null when Modrinth did).
	 */
	record Result(String remoteVersion, String localVersion, String remoteHash, boolean available,
			Map<String, String> withdrawn, Mrpack mrpack, String packUrl) {
		boolean urgent() {
			return available && withdrawn != null;
		}

		Result withWithdrawn(Map<String, String> reasons) {
			return new Result(remoteVersion, localVersion, remoteHash, available, reasons, mrpack, packUrl);
		}
	}

	private static final Pattern VERSION = Pattern.compile("^version\\s*=\\s*\"([^\"]*)\"", Pattern.MULTILINE);
	private static final String MODRINTH_API = "https://api.modrinth.com/v2";

	private UpdateCheck() {}

	/** Completes with null if every source failed (offline, ...). */
	static CompletableFuture<Result> runAsync(Config.Packwiz packwiz, Path gameDir) {
		List<String> packUrls = packwiz.packUrls();
		String localVersion = readLocalVersion(gameDir);
		CompletableFuture<Result> check;
		if (!packwiz.modrinth().isEmpty() && localVersion != null) {
			check = Net.getJson(modrinthUrl(packwiz.modrinth(), minecraftVersion()))
					.thenApply(json -> fromModrinth(json, localVersion))
					.exceptionallyCompose(e -> {
						CakeUpdater.LOGGER.info("Modrinth check failed ({}), asking {}", e.toString(), packUrls);
						return fromPackToml(packUrls, 0, gameDir);
					});
		} else {
			check = fromPackToml(packUrls, 0, gameDir);
		}
		return check
				.thenCompose(result -> !result.available() || result.localVersion() == null
						? CompletableFuture.completedFuture(result)
						: withdrawn(result, packUrls, 0))
				.thenApply(result -> {
					CakeUpdater.LOGGER.info("Modpack update check: {}", result);
					return result;
				})
				.exceptionally(e -> {
					CakeUpdater.LOGGER.warn("Modpack update check failed: {}", e.toString());
					return null;
				});
	}

	static String modrinthUrl(String project, String minecraft) {
		String query = "?loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
				+ (minecraft == null ? "" : "&game_versions=" + URLEncoder.encode("[\"" + minecraft + "\"]", StandardCharsets.UTF_8));
		return MODRINTH_API + "/project/" + URLEncoder.encode(project, StandardCharsets.UTF_8) + "/version" + query;
	}

	/** The newest release in a Modrinth version list (newest first) compared with the installed version. */
	static Result fromModrinth(JsonElement versions, String localVersion) {
		for (JsonElement e : versions.getAsJsonArray()) {
			JsonObject v = e.getAsJsonObject();
			if (!"release".equals(v.get("version_type").getAsString())) {
				continue;
			}
			JsonObject file = null;
			for (JsonElement f : v.getAsJsonArray("files")) {
				if (f.getAsJsonObject().get("primary").getAsBoolean() || file == null) {
					file = f.getAsJsonObject();
				}
			}
			if (file == null || !file.get("filename").getAsString().endsWith(".mrpack")) {
				continue;
			}
			String remote = v.get("version_number").getAsString();
			Mrpack mrpack = new Mrpack(v.get("id").getAsString(), file.get("url").getAsString(),
					file.getAsJsonObject("hashes").get("sha512").getAsString());
			boolean available = Changelog.compare(remote, localVersion) > 0;
			return new Result(remote, localVersion, "modrinth:" + mrpack.versionId(), available, null, mrpack, null);
		}
		throw new IllegalStateException("no release with a .mrpack on Modrinth");
	}

	private static CompletableFuture<Result> fromPackToml(List<String> urls, int i, Path gameDir) {
		if (i >= urls.size()) {
			return CompletableFuture.failedFuture(new IllegalStateException("no pack.toml address answered"));
		}
		return Net.get(urls.get(i), Duration.ofSeconds(15))
				.thenApply(body -> compare(body, gameDir, urls.get(i)))
				.exceptionallyCompose(e -> {
					if (i + 1 < urls.size()) {
						CakeUpdater.LOGGER.info("{} failed ({}), trying {}", urls.get(i), e.toString(), urls.get(i + 1));
					}
					return fromPackToml(urls, i + 1, gameDir);
				});
	}

	/** A missing file (404) or a bad one at every address just means no version is withdrawn. */
	private static CompletableFuture<Result> withdrawn(Result result, List<String> urls, int i) {
		if (i >= urls.size()) {
			return CompletableFuture.completedFuture(result);
		}
		return Net.get(Withdrawn.url(urls.get(i)), Duration.ofSeconds(15))
				.thenApply(json -> withWithdrawn(result, new String(json, StandardCharsets.UTF_8)))
				.exceptionallyCompose(e -> {
					CakeUpdater.LOGGER.info("No withdrawn versions list at {}: {}", urls.get(i), e.toString());
					return withdrawn(result, urls, i + 1);
				});
	}

	static Result withWithdrawn(Result result, String withdrawnJson) {
		return result.withWithdrawn(Withdrawn.reasons(withdrawnJson, result.localVersion()));
	}

	static Result compare(byte[] packToml, Path gameDir, String packUrl) {
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
		return new Result(remoteVersion, localVersion, remoteHash, available, null, null, packUrl);
	}

	private static String minecraftVersion() {
		try {
			return FabricLoader.getInstance().getModContainer("minecraft")
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
		} catch (Throwable e) {
			return null;
		}
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
