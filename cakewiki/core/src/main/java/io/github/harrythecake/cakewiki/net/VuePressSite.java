package io.github.harrythecake.cakewiki.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakewiki.doc.Link;
import io.github.harrythecake.cakewiki.net.WikiClient.Page;
import io.github.harrythecake.cakewiki.net.WikiClient.Transport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A static site built with VuePress 2 (e.g. Theme Hope): every page is pre-rendered HTML at its path, with the text in
 * {@code #markdown-content} (the site entry's {@code content}). There is no API, so pages are named by the titles in
 * the site's route table (in its {@code app-*.js}), or by their path where a title is missing or used twice; without
 * a route table, {@code sitemap.xml} gives the paths. Search and suggestions match those titles.
 * The index is kept on disk for a day and used, however old, when the site can't be reached.
 */
public final class VuePressSite implements Site {
	/** A page of the site; {@code key} is the title the reader uses for it. */
	public record Route(String path, String title, String key) {}

	private static final Pattern APP_SCRIPT = Pattern.compile("(?:src|href)=\"([^\"]*/assets/app-[^\"/]+\\.js)\"");
	// ["/guide/class.html",{loader:()=>D(()=>import("./class.html-x.js"),[]),meta:{title:"…",icon:"…"}}]
	private static final Pattern ROUTE = Pattern.compile("\\[\"(/[^\"]*)\",\\{loader:[^{}]*?meta:\\{title:\"((?:[^\"\\\\]|\\\\.)*)\"");
	// {"/guide/basics/":["cave","command"],…}: the folder lists of "structure" sidebars
	private static final Pattern STRUCTURE = Pattern.compile("\\{\"/[^\"]*/\":\\[\"");
	private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");

	private final SiteConfig.Entry info;
	private final String base;
	private final Transport transport;
	private final Path indexFile;
	private final Clock clock;
	private final Duration maxAge;
	private final Consumer<String> log;

	private CompletableFuture<Void> ready;
	private volatile Index index = new Index(new Fetched(List.of(), null, null));

	/** {@code indexFile}: where the title index is kept; {@code log}: why the index couldn't be fetched, once per load. */
	public VuePressSite(SiteConfig.Entry info, Transport transport, Path indexFile, Clock clock, Duration maxAge, Consumer<String> log) {
		this.info = info;
		this.base = info.url().endsWith("/") ? info.url().substring(0, info.url().length() - 1) : info.url();
		this.transport = transport;
		this.indexFile = indexFile;
		this.clock = clock;
		this.maxAge = maxAge;
		this.log = log;
	}

	@Override
	public SiteConfig.Entry info() {
		return info;
	}

	@Override
	public String base() {
		return base;
	}

	/** Loads the title index once per game (from disk while fresh). Never fails: without one, pages go by path. */
	@Override
	public synchronized CompletableFuture<Void> ready() {
		if (ready == null) ready = loadIndex();
		return ready;
	}

	public List<Route> routes() {
		return index.routes;
	}

	// ---- pages ------------------------------------------------------------------------------------------

	@Override
	public CompletableFuture<Page> parse(String title) {
		return ready().thenCompose(v -> {
			String path = pathFor(title);
			if (path == null) return CompletableFuture.failedFuture(new WikiException("missingtitle", title));
			String url = base + encodePath(path);
			return transport.get(URI.create(url)).handle((body, error) -> {
				if (error != null) {
					Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
					if (cause instanceof WikiException w && w.status() == 404) throw new WikiException("missingtitle", title);
					throw cause instanceof RuntimeException r ? r : new CompletionException(cause);
				}
				return page(path, url, new String(body, StandardCharsets.UTF_8));
			});
		});
	}

	/** The page's text with every link and picture made absolute (the converter knows only the site's origin). */
	private Page page(String path, String url, String html) {
		Document d = Jsoup.parse(html, url);
		Element content = d.selectFirst(info.content());
		// a home layout leaves the text empty and puts a title, a description and links in <main>'s header
		if (content == null || (content.text().isBlank() && content.selectFirst("img") == null)) content = d.selectFirst("main");
		if (content == null) content = d.body();
		for (Element a : content.select("a[href]")) {
			if (!a.attr("href").startsWith("#")) a.attr("href", a.absUrl("href"));
		}
		for (Element img : content.select("img[src]")) img.attr("src", img.absUrl("src"));
		for (Element img : content.select("img[data-src]")) img.attr("data-src", img.absUrl("data-src"));
		content.select("img[src$=.svg]").remove(); // logos and icons; the reader decodes PNG and GIF only
		for (Element actions : content.select(".vp-hero-actions")) { // the home page's buttons, side by side without spaces
			List<Element> buttons = actions.children();
			for (int i = 1; i < buttons.size(); i++) buttons.get(i).before(" · ");
		}

		String key = keyFor(path);
		Element h1 = d.selectFirst(".vp-page-title h1");
		String display = h1 != null ? h1.text().trim() : "";
		if (display.isEmpty()) {
			String t = d.title();
			int bar = t.lastIndexOf(" | ");
			display = (bar > 0 ? t.substring(0, bar) : t).trim();
		}
		if (display.isEmpty()) display = key;
		return new Page(key, display, 0, content.outerHtml());
	}

	@Override
	public String pageUrl(String title) {
		String path = pathFor(title);
		return base + encodePath(path != null ? path : "/");
	}

	/** A page of this site (not a file such as a picture) → its key; the anchor is kept. */
	@Override
	public Link link(String url) {
		if (!url.startsWith(base)) return null;
		String rest = url.substring(base.length());
		if (!rest.isEmpty() && !rest.startsWith("/") && !rest.startsWith("#") && !rest.startsWith("?")) return null; // another host
		String anchor = null;
		int hash = rest.indexOf('#');
		if (hash >= 0) {
			anchor = WikiClient.decode(rest.substring(hash + 1));
			rest = rest.substring(0, hash);
		}
		int q = rest.indexOf('?');
		if (q >= 0) rest = rest.substring(0, q);
		String path = WikiClient.decode(rest);
		String last = path.substring(path.lastIndexOf('/') + 1);
		if (last.contains(".") && !last.endsWith(".html")) return null; // a file: the browser opens it
		return new Link.Page(keyFor(path), anchor != null && !anchor.isEmpty() ? anchor : null);
	}

	// ---- search -----------------------------------------------------------------------------------------

	/** Titles that start with the text first, then titles containing it, then paths containing it; each in site order. */
	@Override
	public CompletableFuture<List<String>> suggest(String prefix, int limit) {
		if (prefix.isBlank()) return CompletableFuture.completedFuture(List.of());
		return ready().thenApply(v -> matches(prefix, limit));
	}

	@Override
	public CompletableFuture<Optional<String>> resolve(String name) {
		if (name.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
		return ready().thenApply(v -> {
			String path = pathFor(name);
			if (path != null && (name.startsWith("/") || index.byKey.containsKey(fold(name)))) return Optional.of(keyFor(path));
			return matches(name, 1).stream().findFirst();
		});
	}

	private List<String> matches(String text, int limit) {
		String q = text.trim().toLowerCase(Locale.ROOT);
		List<String> starts = new ArrayList<>(), contains = new ArrayList<>(), paths = new ArrayList<>();
		for (Route r : index.routes) {
			String t = r.title().toLowerCase(Locale.ROOT);
			if (!t.isEmpty() && t.startsWith(q)) starts.add(r.key());
			else if (!t.isEmpty() && t.contains(q)) contains.add(r.key());
			else if (r.path().toLowerCase(Locale.ROOT).contains(q)) paths.add(r.key());
		}
		List<String> all = new ArrayList<>(starts);
		all.addAll(contains);
		all.addAll(paths);
		return List.copyOf(all.subList(0, Math.min(limit, all.size())));
	}

	// ---- names ------------------------------------------------------------------------------------------

	/** The path for a key: a title from the index, or a path as it is; null for an unknown title. */
	String pathFor(String key) {
		String k = key.trim();
		if (k.startsWith("/")) return normalizePath(k);
		return index.byKey.get(fold(k));
	}

	/** The key for a path: its title when the index has one, else the path itself. */
	String keyFor(String path) {
		String p = normalizePath(path);
		String key = index.byPath.get(p);
		if (key != null) return key;
		if (!p.endsWith("/") && !p.endsWith(".html")) {
			key = index.byPath.get(p + ".html");
			if (key == null) key = index.byPath.get(p + "/");
			if (key != null) return key;
		}
		return p;
	}

	/** Titles match ignoring case (there's no API to say which page a name means). */
	private static String fold(String title) {
		return Titles.normalize(title).toLowerCase(Locale.ROOT);
	}

	/** "" → "/"; ".../index.html" → ".../" (VuePress serves both). */
	static String normalizePath(String path) {
		String p = path.isEmpty() ? "/" : path.startsWith("/") ? path : "/" + path;
		if (p.endsWith("/index.html")) p = p.substring(0, p.length() - "index.html".length());
		return p;
	}

	/** Percent-encodes what a URL path can't hold as is (spaces, non-ASCII), keeping {@code /}. */
	static String encodePath(String path) {
		StringBuilder sb = new StringBuilder();
		for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
			int c = b & 0xFF;
			if (c < 0x80 && (Character.isLetterOrDigit(c) || "-._~/!$&'()*+,;=:@".indexOf(c) >= 0)) sb.append((char) c);
			else sb.append('%').append(String.format(Locale.ROOT, "%02X", c));
		}
		return sb.toString();
	}

	// ---- the index --------------------------------------------------------------------------------------

	/** What the site lists: its pages ({path, title}), and its sidebar config and folder lists when it has them (else null). */
	record Fetched(List<String[]> routes, JsonObject sidebar, JsonObject structure) {}

	/** Keys are given in site order: a title the first time it appears, otherwise the path. */
	private static final class Index {
		final List<Route> routes;
		final Map<String, String> byKey = new HashMap<>(); // fold(key) → path
		final Map<String, String> byPath = new HashMap<>(); // path → key
		final Map<String, String> titles = new HashMap<>(); // path → title
		/** Sidebar per part of the site ({@code /guide/} → its tree), longest prefix wins. */
		final Map<String, List<NavItem>> sections = new LinkedHashMap<>();

		Index(Fetched f) {
			List<Route> list = new ArrayList<>();
			for (String[] pt : f.routes()) {
				String path = normalizePath(WikiClient.decode(pt[0])), title = pt[1].trim(); // the route table may percent-encode
				if (byPath.containsKey(path) || path.equals("/404.html")) continue;
				String key = !title.isEmpty() && !title.startsWith("/") && !byKey.containsKey(fold(title)) ? title : path;
				byKey.put(fold(key), path);
				byPath.put(path, key);
				titles.put(path, title);
				list.add(new Route(path, title, key));
			}
			routes = List.copyOf(list);
			if (f.sidebar() != null) {
				for (Map.Entry<String, JsonElement> e : f.sidebar().entrySet()) {
					if (!e.getKey().startsWith("/") || !e.getValue().isJsonArray()) continue;
					String prefix = e.getKey().endsWith("/") ? e.getKey() : e.getKey() + "/";
					List<NavItem> items = items(e.getValue().getAsJsonArray(), prefix, f.structure());
					if (!items.isEmpty()) sections.put(prefix, items);
				}
			}
		}

		/**
		 * A Theme Hope sidebar array: a string is a page relative to {@code prefix}; an object is a group with
		 * {@code text}, {@code prefix} (added for its children), {@code link}, {@code collapsible} and {@code children}
		 * (an array, or {@code "structure"}: the pages of its folder in the site's folder lists). Entries that name no
		 * page of the site are left out.
		 */
		private List<NavItem> items(JsonArray arr, String prefix, JsonObject structure) {
			List<NavItem> out = new ArrayList<>();
			for (JsonElement el : arr) {
				if (el.isJsonPrimitive()) {
					NavItem leaf = leaf(prefix, el.getAsString());
					if (leaf != null) out.add(leaf);
					continue;
				}
				if (!el.isJsonObject()) continue;
				JsonObject o = el.getAsJsonObject();
				String childPrefix = join(prefix, str(o, "prefix"));
				if (!childPrefix.endsWith("/")) childPrefix += "/";
				String link = str(o, "link");
				String path = link.isEmpty() ? null : find(join(childPrefix, link));
				List<NavItem> children = new ArrayList<>();
				JsonElement c = o.get("children");
				if (c != null && c.isJsonArray()) {
					children.addAll(items(c.getAsJsonArray(), childPrefix, structure));
				} else if (c != null && c.isJsonPrimitive() && c.getAsString().equals("structure") && structure != null
						&& structure.get(childPrefix) instanceof JsonArray files) {
					for (JsonElement name : files) {
						NavItem leaf = leaf(childPrefix, name.getAsString());
						if (leaf != null) children.add(leaf);
					}
				}
				String text = str(o, "text");
				if (text.isEmpty() && path != null) text = titles.getOrDefault(path, path);
				if (text.isEmpty() || (path == null && children.isEmpty())) continue;
				boolean collapsible = o.has("collapsible") && o.get("collapsible").isJsonPrimitive() && o.get("collapsible").getAsBoolean();
				out.add(new NavItem(text, path != null ? byPath.get(path) : null, collapsible, List.copyOf(children)));
			}
			return List.copyOf(out);
		}

		private NavItem leaf(String prefix, String link) {
			String path = find(join(prefix, link));
			if (path == null) return null;
			String title = titles.getOrDefault(path, "");
			return new NavItem(title.isEmpty() ? link : title, byPath.get(path), false, List.of());
		}

		/** A sidebar link → a path of the index: {@code x} or {@code x.md} is {@code x.html} or the folder {@code x/}; README/index is its folder. */
		private String find(String link) {
			String p = normalizePath(link);
			if (p.endsWith(".md")) p = p.substring(0, p.length() - 3);
			String last = p.substring(p.lastIndexOf('/') + 1);
			if (last.equalsIgnoreCase("README") || last.equalsIgnoreCase("index")) p = p.substring(0, p.length() - last.length());
			for (String candidate : p.endsWith("/") || p.endsWith(".html") ? List.of(p) : List.of(p + ".html", p + "/", p)) {
				if (byPath.containsKey(candidate)) return candidate;
			}
			return null;
		}

		private static String join(String prefix, String link) {
			return link.startsWith("/") ? link : prefix + link;
		}

		private static String str(JsonObject o, String key) {
			JsonElement e = o.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsString().trim() : "";
		}
	}

	/** The sidebar for the part of the site {@code title} is in (longest matching prefix); empty outside them. */
	@Override
	public List<NavItem> navigation(String title) {
		Index ix = index;
		String path = pathFor(title);
		if (path == null) return List.of();
		String best = null;
		for (String prefix : ix.sections.keySet()) {
			if (path.startsWith(prefix) && (best == null || prefix.length() > best.length())) best = prefix;
		}
		return best == null ? List.of() : ix.sections.get(best);
	}

	private CompletableFuture<Void> loadIndex() {
		Saved saved = readSaved();
		if (saved != null && saved.current && saved.fetched.plus(maxAge).isAfter(clock.instant())) {
			index = new Index(saved.site);
			return CompletableFuture.completedFuture(null);
		}
		return fetchIndex().handle((site, error) -> {
			if (error == null && !site.routes().isEmpty()) {
				index = new Index(site);
				save(site);
			} else {
				log.accept(error != null ? String.valueOf(error.getCause() != null ? error.getCause() : error) : "no pages listed");
				if (saved != null) index = new Index(saved.site);
			}
			return null;
		});
	}

	/** The route table, sidebar and folder lists from the home page's app script; else the sitemap's paths. */
	private CompletableFuture<Fetched> fetchIndex() {
		return transport.get(URI.create(base + "/")).thenCompose(home -> {
			Matcher m = APP_SCRIPT.matcher(new String(home, StandardCharsets.UTF_8));
			if (!m.find()) return CompletableFuture.completedFuture(new Fetched(List.of(), null, null));
			String src = m.group(1);
			URI app = URI.create(base + "/").resolve(src);
			return transport.get(app).thenApply(js -> appScript(new String(js, StandardCharsets.UTF_8)));
		}).thenCompose(site -> site.routes().isEmpty()
				? transport.get(URI.create(base + "/sitemap.xml")).thenApply(xml -> new Fetched(sitemap(new String(xml, StandardCharsets.UTF_8)), null, null))
				: CompletableFuture.completedFuture(site));
	}

	static Fetched appScript(String js) {
		return new Fetched(routeTable(js), sidebar(js), structure(js));
	}

	static List<String[]> routeTable(String js) {
		List<String[]> out = new ArrayList<>();
		Matcher m = ROUTE.matcher(js);
		while (m.find()) out.add(new String[] {m.group(1), unescape(m.group(2))});
		return out;
	}

	/**
	 * Theme Hope's sidebar config: in the theme data, a {@code JSON.parse('…')} string, as {@code "sidebar"} or under
	 * {@code "locales"} (the root locale first). Null when there is none.
	 */
	static JsonObject sidebar(String js) {
		int at = 0;
		while ((at = js.indexOf("JSON.parse('", at)) >= 0) {
			int start = at + "JSON.parse('".length(), end = start;
			while (end < js.length() && js.charAt(end) != '\'') end += js.charAt(end) == '\\' ? 2 : 1;
			at = end;
			if (end >= js.length()) break;
			String text = unescape(js.substring(start, end));
			if (!text.contains("\"sidebar\"")) continue;
			try {
				JsonObject theme = JsonParser.parseString(text).getAsJsonObject();
				if (theme.get("sidebar") instanceof JsonObject s) return s;
				if (theme.get("locales") instanceof JsonObject locales) {
					if (locales.get("/") instanceof JsonObject root && root.get("sidebar") instanceof JsonObject s) return s;
					for (Map.Entry<String, JsonElement> e : locales.entrySet()) {
						if (e.getValue() instanceof JsonObject l && l.get("sidebar") instanceof JsonObject s) return s;
					}
				}
			} catch (RuntimeException ignored) {
				// not the theme data
			}
		}
		return null;
	}

	/** The folder lists of "structure" sidebars: an object {@code {"/dir/": ["page", …], …}} in the script. Null when none. */
	static JsonObject structure(String js) {
		Matcher m = STRUCTURE.matcher(js);
		while (m.find()) {
			int end = objectEnd(js, m.start());
			if (end < 0) continue;
			try {
				JsonObject o = JsonParser.parseString(js.substring(m.start(), end)).getAsJsonObject(); // lenient: \' is fine
				boolean lists = true;
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					if (!e.getValue().isJsonArray()) lists = false;
					else for (JsonElement x : e.getValue().getAsJsonArray()) if (!x.isJsonPrimitive()) lists = false;
				}
				if (lists) return o;
			} catch (RuntimeException ignored) {
				// something else that looked like it
			}
		}
		return null;
	}

	/** The index just past the {@code }} closing the object that opens at {@code start}, skipping strings; -1 if none. */
	private static int objectEnd(String js, int start) {
		int depth = 0;
		for (int i = start; i < js.length(); i++) {
			char c = js.charAt(i);
			if (c == '"') {
				for (i++; i < js.length() && js.charAt(i) != '"'; i++) if (js.charAt(i) == '\\') i++;
			} else if (c == '{' || c == '[') {
				depth++;
			} else if (c == '}' || c == ']') {
				if (--depth == 0) return i + 1;
			}
		}
		return -1;
	}

	List<String[]> sitemap(String xml) {
		List<String[]> out = new ArrayList<>();
		Matcher m = LOC.matcher(xml);
		while (m.find()) {
			String loc = m.group(1).replace("&amp;", "&");
			if (loc.startsWith(base)) out.add(new String[] {WikiClient.decode(loc.substring(base.length())), ""});
		}
		return out;
	}

	/** A JavaScript string's escapes: {@code \"}, {@code \\}, {@code \n}, {@code \\uXXXX}. */
	static String unescape(String s) {
		if (s.indexOf('\\') < 0) return s;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c != '\\' || i + 1 >= s.length()) {
				sb.append(c);
				continue;
			}
			char n = s.charAt(++i);
			switch (n) {
				case 'n' -> sb.append('\n');
				case 't' -> sb.append('\t');
				case 'u' -> {
					if (i + 4 < s.length()) {
						try {
							sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
							i += 4;
						} catch (NumberFormatException e) {
							sb.append('u');
						}
					} else sb.append('u');
				}
				default -> sb.append(n);
			}
		}
		return sb.toString();
	}

	/** What a saved index holds; one saved by an older version is fetched again (and still used offline). */
	private static final int INDEX_FORMAT = 2; // 2: sidebar and folder lists

	private record Saved(Instant fetched, boolean current, Fetched site) {}

	private Saved readSaved() {
		if (!Files.isRegularFile(indexFile)) return null;
		try {
			JsonObject o = JsonParser.parseString(Files.readString(indexFile, StandardCharsets.UTF_8)).getAsJsonObject();
			List<String[]> routes = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("routes")) {
				JsonArray pt = e.getAsJsonArray();
				routes.add(new String[] {pt.get(0).getAsString(), pt.get(1).getAsString()});
			}
			JsonObject sidebar = o.get("sidebar") instanceof JsonObject s ? s : null;
			JsonObject structure = o.get("structure") instanceof JsonObject s ? s : null;
			boolean current = o.has("format") && o.get("format").getAsInt() == INDEX_FORMAT;
			return new Saved(Instant.ofEpochMilli(o.get("fetched").getAsLong()), current, new Fetched(routes, sidebar, structure));
		} catch (IOException | RuntimeException e) {
			return null; // unreadable: fetched again
		}
	}

	private void save(Fetched site) {
		JsonArray arr = new JsonArray();
		for (String[] pt : site.routes()) {
			JsonArray a = new JsonArray();
			a.add(pt[0]);
			a.add(pt[1]);
			arr.add(a);
		}
		JsonObject o = new JsonObject();
		o.addProperty("format", INDEX_FORMAT);
		o.addProperty("fetched", clock.instant().toEpochMilli());
		o.add("routes", arr);
		if (site.sidebar() != null) o.add("sidebar", site.sidebar());
		if (site.structure() != null) o.add("structure", site.structure());
		try {
			Files.createDirectories(indexFile.getParent());
			Path tmp = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");
			Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
			Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			log.accept("couldn't save the index: " + e);
		}
	}
}
