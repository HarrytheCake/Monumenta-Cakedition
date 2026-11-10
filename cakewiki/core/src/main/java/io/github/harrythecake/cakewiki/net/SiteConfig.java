package io.github.harrythecake.cakewiki.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The sites the reader offers, from {@code config/cakewiki/sites.json} (comments allowed). When the file is missing
 * (first launch, or the player deleted it), the jar's default list ({@link #DEFAULT_RESOURCE}) is written there once
 * and used; the mod never changes an existing file. When nothing in it is usable, the list is just the Monumenta Wiki.
 * The first site is the default: the reader opens on it the first time, and item lookups go to it.
 *
 * <p>A site can be tied to servers ({@code servers}): on a matching server only the tied sites that match plus the
 * untied ones are offered; anywhere else (single-player, the title screen, other servers) every site is.
 */
public final class SiteConfig {
	public static final String MEDIAWIKI = "mediawiki", VUEPRESS = "vuepress";

	/**
	 * One site. {@code id}: short name for {@code /wiki <id>:<page>} and bookmarks; {@code mainPage}: the page it opens on;
	 * {@code license}/{@code licenseUrl}: shown on every page (empty = not shown); {@code content}: (VuePress) the CSS
	 * selector of the page text; {@code servers}: server addresses the site belongs to (lower case, no port; each also
	 * covers its subdomains), empty = every server.
	 */
	public record Entry(String id, String type, String name, String url, String mainPage, String license,
			String licenseUrl, String content, List<String> servers) {
		public Entry {
			servers = List.copyOf(servers);
		}

		/** True when this site is tied to the server at {@code host} (a host name or address without the port). */
		public boolean matches(String host) {
			if (host == null) return false;
			for (String s : servers) if (host.equals(s) || host.endsWith("." + s)) return true;
			return false;
		}
	}

	/** {@code problems}: entries that were skipped and why, for the log. */
	public record Loaded(List<Entry> sites, List<String> problems) {}

	/** Monumenta's servers ({@code server.playmonumenta.com} and the numbered shards' hosts). */
	public static final String MONUMENTA_SERVER = "playmonumenta.com";

	public static final Entry MONUMENTA = new Entry("monumenta", MEDIAWIKI, "Monumenta Wiki", WikiClient.MONUMENTA_WIKI,
			"Monumenta Wiki", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/", "", List.of(MONUMENTA_SERVER));

	private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

	private SiteConfig() {}

	/** The default site list in the jar, copied to {@code config/cakewiki/sites.json} when that is missing. */
	public static final String DEFAULT_RESOURCE = "/assets/cakewiki/default-sites.json";

	/** The jar's default site list (JSON with comments). */
	public static String defaultText() {
		try (java.io.InputStream in = SiteConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (in == null) throw new IllegalStateException(DEFAULT_RESOURCE + " is missing from the jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Reads {@code file}; when it's missing, first writes the jar's default there (if it can't, uses the default anyway). */
	public static Loaded load(Path file) {
		if (!Files.exists(file)) {
			String text = defaultText();
			List<String> problems = new ArrayList<>();
			try {
				Files.createDirectories(file.toAbsolutePath().getParent());
				Files.writeString(file, text, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
			} catch (IOException e) {
				problems.add("couldn't write the default site list to " + file + ": " + e);
			}
			Loaded l = parse(text);
			problems.addAll(l.problems());
			return new Loaded(l.sites(), List.copyOf(problems));
		}
		try {
			return parse(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return new Loaded(List.of(MONUMENTA), List.of("couldn't read " + file + ": " + e));
		}
	}

	public static Loaded parse(String json) {
		List<Entry> sites = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		try {
			JsonElement root = JsonParser.parseString(json); // lenient: // and /* */ comments are fine
			Set<String> ids = new HashSet<>();
			int n = 0;
			for (JsonElement e : root.getAsJsonObject().getAsJsonArray("sites")) {
				n++;
				try {
					Entry entry = entry(e.getAsJsonObject());
					if (!ids.add(entry.id())) throw new IllegalArgumentException("id \"" + entry.id() + "\" is used twice");
					sites.add(entry);
				} catch (RuntimeException ex) {
					problems.add("site " + n + ": " + ex.getMessage());
				}
			}
		} catch (RuntimeException e) {
			problems.add("not a site list ({\"sites\": [...]}): " + e.getMessage());
		}
		if (sites.isEmpty()) {
			problems.add("no usable site: using the Monumenta Wiki");
			sites.add(MONUMENTA);
		}
		return new Loaded(List.copyOf(sites), List.copyOf(problems));
	}

	private static Entry entry(JsonObject o) {
		String id = string(o, "id", "");
		if (!ID.matcher(id).matches()) throw new IllegalArgumentException("id must be 1-32 of a-z 0-9 _ - (got \"" + id + "\")");
		String type = string(o, "type", MEDIAWIKI).toLowerCase(Locale.ROOT);
		if (!type.equals(MEDIAWIKI) && !type.equals(VUEPRESS)) {
			throw new IllegalArgumentException("type must be \"" + MEDIAWIKI + "\" or \"" + VUEPRESS + "\" (got \"" + type + "\")");
		}
		String url = string(o, "url", "");
		if (!url.startsWith("https://") && !url.startsWith("http://")) throw new IllegalArgumentException("url must start with https://");
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		String name = string(o, "name", id);
		String main = string(o, "mainPage", type.equals(VUEPRESS) ? "/" : "Main Page");
		List<String> servers;
		if (o.has("servers") && !o.get("servers").isJsonNull()) {
			servers = servers(o.get("servers"));
		} else if (id.equals(MONUMENTA.id()) && url.equals(MONUMENTA.url())) {
			servers = MONUMENTA.servers(); // the Monumenta Wiki belongs to Monumenta unless its entry says otherwise
		} else {
			servers = List.of();
		}
		return new Entry(id, type, name, url, main, string(o, "license", ""), string(o, "licenseUrl", ""),
				string(o, "content", type.equals(VUEPRESS) ? "#markdown-content" : ""), servers);
	}

	/** {@code "servers"}: a list of addresses (or one string); ports, {@code *.} and a trailing dot are dropped. */
	private static List<String> servers(JsonElement e) {
		JsonArray array;
		if (e.isJsonArray()) {
			array = e.getAsJsonArray();
		} else {
			array = new JsonArray();
			array.add(e);
		}
		List<String> out = new ArrayList<>();
		for (JsonElement x : array) {
			String host = host(x.getAsString());
			if (host == null) throw new IllegalArgumentException("servers: \"" + x.getAsString() + "\" is not a server address");
			if (host.startsWith("*.")) host = host.substring(2);
			if (!out.contains(host)) out.add(host);
		}
		return out;
	}

	/**
	 * A server address as typed in the server list ({@code Play.Example.com:25565}, {@code [::1]:25565}) → its host in
	 * lower case without port or trailing dot ({@code play.example.com}); null when blank.
	 */
	public static String host(String address) {
		if (address == null) return null;
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.startsWith("[")) { // IPv6 literal
			int end = a.indexOf(']');
			a = end > 0 ? a.substring(1, end) : a.substring(1);
		} else {
			int colon = a.indexOf(':');
			if (colon >= 0 && colon == a.lastIndexOf(':')) a = a.substring(0, colon); // host:port (a bare IPv6 has several)
		}
		while (a.endsWith(".")) a = a.substring(0, a.length() - 1);
		return a.isEmpty() ? null : a;
	}

	/**
	 * The sites offered on the server at {@code host} (null = not on a server), in list order: when some site is tied
	 * to that server, those sites plus the untied ones; otherwise every site.
	 */
	public static List<Entry> offered(List<Entry> sites, String host) {
		if (sites.stream().noneMatch(s -> s.matches(host))) return sites;
		return sites.stream().filter(s -> s.servers().isEmpty() || s.matches(host)).toList();
	}

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return fallback;
		String s = e.getAsString().trim();
		return s.isEmpty() ? fallback : s;
	}
}
