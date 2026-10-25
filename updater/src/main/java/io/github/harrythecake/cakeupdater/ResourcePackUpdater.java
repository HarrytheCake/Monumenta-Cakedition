package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps Modrinth-hosted resource packs up to date while the game runs. Only packs the player has
 * turned on are checked. The new file is downloaded next to the old one and swapped in at the same
 * position in the pack list; the old zip stays (disabled) so packwiz-installer still finds the files it manages.
 */
final class ResourcePackUpdater {
	record Pending(String title, String fromVersion, String toVersion, String oldPackName,
				   String fileName, String url, String sha512) {}

	private record Hashed(long size, long modified, String sha1) {}

	private static final String API = "https://api.modrinth.com/v2";
	private static final Map<Path, Hashed> HASH_CACHE = new ConcurrentHashMap<>();

	private ResourcePackUpdater() {}

	/** {@code enabledNames} must be read on the render thread; completes with an empty list on failure. */
	static CompletableFuture<List<Pending>> check(List<Config.ResourcePack> packs, Path packDir, Collection<String> enabledNames) {
		List<Config.ResourcePack> active = packs.stream().filter(p -> p.enabled).toList();
		if (active.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		return CompletableFuture.supplyAsync(() -> hashEnabled(packDir, enabledNames))
				.thenCompose(hashes -> hashes.isEmpty()
						? CompletableFuture.completedFuture(List.<Pending>of())
						: Net.postJson(API + "/version_files", versionFilesBody(hashes.keySet()))
								.thenCompose(installed -> checkAll(active, hashes, installed.getAsJsonObject())))
				.exceptionally(e -> {
					CakeUpdater.LOGGER.warn("Resource pack update check failed: {}", e.toString());
					return List.of();
				});
	}

	private static CompletableFuture<List<Pending>> checkAll(List<Config.ResourcePack> packs, Map<String, String> hashes,
															 JsonObject installedBySha1) {
		List<CompletableFuture<Pending>> checks = new ArrayList<>();
		for (Config.ResourcePack pack : packs) {
			checks.add(checkOne(pack, hashes, installedBySha1));
		}
		return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
				.thenApply(v -> checks.stream().map(CompletableFuture::join).filter(p -> p != null).toList());
	}

	private static CompletableFuture<Pending> checkOne(Config.ResourcePack pack, Map<String, String> hashes,
													   JsonObject installedBySha1) {
		String project = URLEncoder.encode(pack.modrinthProject, StandardCharsets.UTF_8);
		List<String> gameVersions = pack.gameVersions == null || pack.gameVersions.isEmpty()
				? List.of(minecraftVersion()) : pack.gameVersions;
		String query = "?game_versions=" + URLEncoder.encode(jsonArray(gameVersions), StandardCharsets.UTF_8);

		return Net.getJson(API + "/project/" + project).thenCompose(projectJson -> {
			JsonObject info = projectJson.getAsJsonObject();
			String projectId = info.get("id").getAsString();
			String title = info.get("title").getAsString();

			// Which enabled zip belongs to this project?
			JsonObject current = null;
			String currentPack = null;
			for (Map.Entry<String, String> entry : hashes.entrySet()) {
				JsonElement version = installedBySha1.get(entry.getKey());
				if (version != null && projectId.equals(version.getAsJsonObject().get("project_id").getAsString())) {
					current = version.getAsJsonObject();
					currentPack = entry.getValue();
				}
			}
			if (current == null) {
				return CompletableFuture.completedFuture(null);
			}
			JsonObject installed = current;
			String installedPack = currentPack;

			return Net.getJson(API + "/project/" + project + "/version" + query).thenApply(versionsJson -> {
				JsonObject newest = null;
				for (JsonElement element : versionsJson.getAsJsonArray()) {
					JsonObject version = element.getAsJsonObject();
					List<String> types = pack.versionTypes == null || pack.versionTypes.isEmpty()
							? List.of("release") : pack.versionTypes;
					if (types.contains(version.get("version_type").getAsString())
							&& (newest == null || published(version).compareTo(published(newest)) > 0)) {
						newest = version;
					}
				}
				if (newest == null || newest.get("id").getAsString().equals(installed.get("id").getAsString())
						|| published(newest).compareTo(published(installed)) <= 0) {
					CakeUpdater.LOGGER.info("Resource pack {} is up to date ({})", title, installed.get("version_number").getAsString());
					return null;
				}
				JsonObject file = primaryFile(newest.getAsJsonArray("files"));
				Pending pending = new Pending(title, installed.get("version_number").getAsString(),
						newest.get("version_number").getAsString(), installedPack,
						file.get("filename").getAsString(), file.get("url").getAsString(),
						file.getAsJsonObject("hashes").get("sha512").getAsString());
				CakeUpdater.LOGGER.info("Resource pack update: {}", pending);
				return pending;
			});
		});
	}

	/** Downloads every pending update; then swaps them in and reloads resources on the render thread. */
	static CompletableFuture<Void> apply(MinecraftClient client, List<Pending> updates) {
		Path packDir = client.getResourcePackDir();
		List<CompletableFuture<Void>> downloads = new ArrayList<>();
		for (Pending update : updates) {
			downloads.add(download(update, packDir));
		}
		return CompletableFuture.allOf(downloads.toArray(CompletableFuture[]::new))
				.thenCompose(v -> client.submit(() -> {
					ResourcePackManager manager = client.getResourcePackManager();
					manager.scanPacks();
					List<String> enabled = new ArrayList<>(manager.getEnabledNames());
					for (Pending update : updates) {
						String newPack = "file/" + update.fileName();
						int index = enabled.indexOf(update.oldPackName());
						enabled.remove(newPack);
						if (index >= 0 && index < enabled.size() && enabled.get(index).equals(update.oldPackName())) {
							enabled.set(index, newPack);
						} else {
							enabled.remove(update.oldPackName());
							enabled.add(newPack);
						}
					}
					manager.setEnabledProfiles(enabled);
					// Saves options.txt and reloads resources when the list changed (same as the Resource Packs screen).
					client.options.refreshResourcePacks(manager);
				}));
	}

	private static CompletableFuture<Void> download(Pending update, Path packDir) {
		Path target = packDir.resolve(update.fileName());
		try {
			if (Files.exists(target) && UpdateCheck.hash("SHA-512", Files.readAllBytes(target)).equalsIgnoreCase(update.sha512())) {
				return CompletableFuture.completedFuture(null);
			}
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}
		return Net.get(update.url(), Duration.ofMinutes(5)).thenAccept(body -> {
			if (!UpdateCheck.hash("SHA-512", body).equalsIgnoreCase(update.sha512())) {
				throw new IllegalStateException("hash mismatch for " + update.fileName());
			}
			try {
				Path part = Files.createTempFile(packDir, update.fileName(), ".part");
				Files.write(part, body);
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new IllegalStateException("could not save " + update.fileName() + ": " + e.getMessage(), e);
			}
		});
	}

	/** sha1 -> pack name ("file/x.zip") for every enabled zip in resourcepacks/. */
	private static Map<String, String> hashEnabled(Path packDir, Collection<String> enabledNames) {
		Map<String, String> result = new LinkedHashMap<>();
		for (String name : enabledNames) {
			if (!name.startsWith("file/") || !name.endsWith(".zip")) {
				continue;
			}
			Path zip = packDir.resolve(name.substring("file/".length()));
			try {
				if (!Files.isRegularFile(zip)) {
					continue;
				}
				long size = Files.size(zip);
				long modified = Files.getLastModifiedTime(zip).toMillis();
				Hashed cached = HASH_CACHE.get(zip);
				if (cached == null || cached.size() != size || cached.modified() != modified) {
					cached = new Hashed(size, modified, UpdateCheck.hash("SHA-1", Files.readAllBytes(zip)));
					HASH_CACHE.put(zip, cached);
				}
				result.put(cached.sha1(), name);
			} catch (IOException e) {
				CakeUpdater.LOGGER.warn("Could not hash {}: {}", zip, e.toString());
			}
		}
		return result;
	}

	private static String versionFilesBody(Collection<String> sha1s) {
		JsonObject body = new JsonObject();
		JsonArray hashes = new JsonArray();
		sha1s.forEach(hashes::add);
		body.add("hashes", hashes);
		body.addProperty("algorithm", "sha1");
		return body.toString();
	}

	private static JsonObject primaryFile(JsonArray files) {
		for (JsonElement file : files) {
			if (file.getAsJsonObject().get("primary").getAsBoolean()) {
				return file.getAsJsonObject();
			}
		}
		return files.get(0).getAsJsonObject();
	}

	private static String published(JsonObject version) {
		return version.get("date_published").getAsString();
	}

	private static String jsonArray(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array.toString();
	}

	private static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("1.20.4");
	}
}
