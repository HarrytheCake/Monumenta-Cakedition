package io.github.harrythecake.cakewiki.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakewiki.doc.Link;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** A MediaWiki site: the API calls CakeWiki makes. Everything is asynchronous; nothing here touches the game. */
public final class WikiClient implements Site {
	public static final String MONUMENTA_WIKI = "https://monumenta.wiki.gg";

	/** One HTTP GET; the body for status 200, otherwise a failed future. */
	public interface Transport {
		CompletableFuture<byte[]> get(URI uri);
	}

	/** A page as the API returned it. {@code title} is the canonical title after redirects. */
	public record Page(String title, String displayTitle, long revision, String html) {}

	private final SiteConfig.Entry info;
	private final String base;
	private final Transport transport;

	public WikiClient(SiteConfig.Entry info, Transport transport) {
		this.info = info;
		this.base = info.url().endsWith("/") ? info.url().substring(0, info.url().length() - 1) : info.url();
		this.transport = transport;
	}

	/** A wiki at {@code base}, described like the Monumenta Wiki (tests, live tests). */
	public WikiClient(String base, Transport transport) {
		this(new SiteConfig.Entry(SiteConfig.MONUMENTA.id(), SiteConfig.MEDIAWIKI, SiteConfig.MONUMENTA.name(), base,
				SiteConfig.MONUMENTA.mainPage(), SiteConfig.MONUMENTA.license(), SiteConfig.MONUMENTA.licenseUrl(), ""), transport);
	}

	@Override
	public SiteConfig.Entry info() {
		return info;
	}

	@Override
	public String base() {
		return base;
	}

	@Override
	public String pageUrl(String title) {
		return base + "/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8)
				.replace("%2F", "/").replace("%3A", ":").replace("+", "%20");
	}

	/** Rendered HTML of one page, following redirects. */
	@Override
	public CompletableFuture<Page> parse(String title) {
		return call(params("action", "parse", "page", title, "prop", "text|displaytitle|revid",
				"redirects", "1", "disableeditsection", "1", "disablelimitreport", "1")).thenApply(json -> {
			JsonObject p = json.getAsJsonObject().getAsJsonObject("parse");
			String t = p.get("title").getAsString();
			String display = p.has("displaytitle") ? stripTags(p.get("displaytitle").getAsString()) : t;
			return new Page(t, display, p.has("revid") ? p.get("revid").getAsLong() : 0, p.get("text").getAsString());
		});
	}

	/** Page titles starting with {@code prefix}, best first (the search box's suggestions). */
	@Override
	public CompletableFuture<List<String>> suggest(String prefix, int limit) {
		if (prefix.isBlank()) return CompletableFuture.completedFuture(List.of());
		return call(params("action", "opensearch", "search", prefix, "limit", String.valueOf(limit),
				"namespace", "0", "redirects", "resolve")).thenApply(json -> {
			List<String> titles = new ArrayList<>();
			JsonArray arr = json.getAsJsonArray();
			if (arr.size() > 1) for (JsonElement e : arr.get(1).getAsJsonArray()) titles.add(e.getAsString());
			return titles;
		});
	}

	/** Full-text search: titles of the best matching pages. */
	public CompletableFuture<List<String>> search(String query, int limit) {
		if (query.isBlank()) return CompletableFuture.completedFuture(List.of());
		return call(params("action", "query", "list", "search", "srsearch", query, "srnamespace", "0",
				"srlimit", String.valueOf(limit), "srprop", "")).thenApply(json -> {
			List<String> titles = new ArrayList<>();
			for (JsonElement e : json.getAsJsonObject().getAsJsonObject("query").getAsJsonArray("search")) {
				titles.add(e.getAsJsonObject().get("title").getAsString());
			}
			return titles;
		});
	}

	/**
	 * The page for a name, such as an item's: the exact title (or what it redirects to) if it exists,
	 * otherwise the top search hit, otherwise empty.
	 */
	@Override
	public CompletableFuture<Optional<String>> resolve(String name) {
		if (name.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
		return call(params("action", "query", "titles", name, "redirects", "1")).thenCompose(json -> {
			JsonArray pages = json.getAsJsonObject().getAsJsonObject("query").getAsJsonArray("pages");
			if (pages != null && !pages.isEmpty()) {
				JsonObject page = pages.get(0).getAsJsonObject();
				if (!page.has("missing") && !page.has("invalid")) {
					return CompletableFuture.completedFuture(Optional.of(page.get("title").getAsString()));
				}
			}
			return search(name, 1).thenApply(hits -> hits.stream().findFirst());
		});
	}

	/** {@code <base>/wiki/Title#anchor} → that page (underscores → spaces, percent-decoded, {@code +} kept). */
	@Override
	public Link link(String url) {
		return link(base, url);
	}

	/** {@link #link(String)} for a MediaWiki at {@code base}. */
	public static Link link(String base, String url) {
		if (!url.startsWith(base + "/wiki/")) return null;
		String path = url.substring((base + "/wiki/").length());
		int hash = path.indexOf('#');
		String anchor = hash >= 0 ? decode(path.substring(hash + 1)) : null;
		if (hash >= 0) path = path.substring(0, hash);
		return new Link.Page(decode(path).replace('_', ' '), anchor);
	}

	/** The parse API doesn't list category members or show special pages. */
	@Override
	public boolean browserOnly(String title) {
		return title.startsWith("Special:") || title.startsWith("Category:");
	}

	static String decode(String s) {
		try {
			return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return s; // a stray % that isn't an escape
		}
	}

	// ---- plumbing ---------------------------------------------------------------------------------------

	private static Map<String, String> params(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
		return m;
	}

	URI uri(Map<String, String> params) {
		Map<String, String> all = new LinkedHashMap<>(params);
		all.put("format", "json");
		all.put("formatversion", "2");
		String query = all.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8).replace("+", "%20"))
				.collect(Collectors.joining("&"));
		return URI.create(base + "/api.php?" + query);
	}

	private CompletableFuture<JsonElement> call(Map<String, String> params) {
		return transport.get(uri(params)).thenApply(body -> {
			JsonElement json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (json.isJsonObject() && json.getAsJsonObject().has("error")) {
				JsonObject err = json.getAsJsonObject().getAsJsonObject("error");
				throw new WikiException(err.has("code") ? err.get("code").getAsString() : "unknown",
						err.has("info") ? err.get("info").getAsString() : "");
			}
			return json;
		});
	}

	private static String stripTags(String html) {
		return org.jsoup.Jsoup.parse(html).text();
	}

	/** The real transport: Java's HTTP client with a User-Agent naming the mod, as the wiki's API rules ask. */
	public static Transport http(String modVersion) {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		String userAgent = "CakeWiki/" + modVersion + " (https://github.com/HarrytheCake/CakeWiki)";
		return uri -> client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
						.header("User-Agent", userAgent).header("Accept-Encoding", "identity").GET().build(),
				HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
			if (response.statusCode() != 200) throw WikiException.http(response.statusCode(), uri.getHost());
			return response.body();
		});
	}
}
