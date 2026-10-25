package io.github.harrythecake.cakeupdater;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class Net {
	// Modrinth asks API clients for a User-Agent that identifies the project.
	static final String USER_AGENT = "HarrytheCake/cakeupdater/" + modVersion()
			+ " (github.com/HarrytheCake/Monumenta-Cakedition)";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private Net() {}

	static CompletableFuture<byte[]> get(String url, Duration timeout) {
		return send(request(url, timeout).GET().build());
	}

	static CompletableFuture<JsonElement> getJson(String url) {
		return get(url, Duration.ofSeconds(15)).thenApply(Net::parse);
	}

	static CompletableFuture<JsonElement> postJson(String url, String body) {
		return send(request(url, Duration.ofSeconds(15))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build()).thenApply(Net::parse);
	}

	private static HttpRequest.Builder request(String url, Duration timeout) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("User-Agent", USER_AGENT);
	}

	private static CompletableFuture<byte[]> send(HttpRequest request) {
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode() + " from " + request.uri());
			}
			return response.body();
		});
	}

	private static String modVersion() {
		try {
			return FabricLoader.getInstance().getModContainer(CakeUpdater.MOD_ID)
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		} catch (Throwable e) {
			return "dev";
		}
	}

	private static JsonElement parse(byte[] body) {
		return JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
	}
}
