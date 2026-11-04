package io.github.harrythecake.cakewiki.net;

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
 * The sites the reader offers, from {@code config/cakewiki/sites.json} (comments allowed). Without the file, or when
 * nothing in it is usable, the list is just the Monumenta Wiki. The first site is the default: the reader opens on it
 * the first time, and item lookups go to it. The mod never writes this file.
 */
public final class SiteConfig {
	public static final String MEDIAWIKI = "mediawiki", VUEPRESS = "vuepress";

	/**
	 * One site. {@code id}: short name for {@code /wiki <id>:<page>} and bookmarks; {@code mainPage}: the page it opens on;
	 * {@code license}/{@code licenseUrl}: shown on every page (empty = not shown); {@code content}: (VuePress) the CSS
	 * selector of the page text.
	 */
	public record Entry(String id, String type, String name, String url, String mainPage, String license,
			String licenseUrl, String content) {}

	/** {@code problems}: entries that were skipped and why, for the log. */
	public record Loaded(List<Entry> sites, List<String> problems) {}

	public static final Entry MONUMENTA = new Entry("monumenta", MEDIAWIKI, "Monumenta Wiki", WikiClient.MONUMENTA_WIKI,
			"Monumenta Wiki", "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/", "");

	private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

	private SiteConfig() {}

	public static Loaded load(Path file) {
		if (!Files.isRegularFile(file)) return new Loaded(List.of(MONUMENTA), List.of());
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
		return new Entry(id, type, name, url, main, string(o, "license", ""), string(o, "licenseUrl", ""),
				string(o, "content", type.equals(VUEPRESS) ? "#markdown-content" : ""));
	}

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) return fallback;
		String s = e.getAsString().trim();
		return s.isEmpty() ? fallback : s;
	}
}
