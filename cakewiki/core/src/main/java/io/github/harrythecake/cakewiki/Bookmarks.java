package io.github.harrythecake.cakewiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.harrythecake.cakewiki.net.SiteConfig;
import io.github.harrythecake.cakewiki.net.Titles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's bookmarked pages, newest first, saved in {@code config/cakewiki/bookmarks.json} after every change.
 * Each belongs to a site (its id; bookmarks saved before there were several sites belong to the Monumenta Wiki).
 * Titles match the way MediaWiki matches them ({@link Titles#normalize}). A file that can't be read is set aside
 * as {@code bookmarks.json.bad} instead of being overwritten.
 */
public final class Bookmarks {
	public record Bookmark(String site, String title, Instant added) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final List<Bookmark> list = new ArrayList<>();

	public Bookmarks(Path file) {
		this.file = file;
		load();
	}

	/** One site's bookmarks, newest first. */
	public List<Bookmark> all(String site) {
		return list.stream().filter(b -> b.site().equals(site)).toList();
	}

	public boolean contains(String site, String title) {
		return indexOf(site, title) >= 0;
	}

	/** Adds the page (at the top) or removes it; returns whether it is bookmarked now. */
	public boolean toggle(String site, String title) throws IOException {
		int i = indexOf(site, title);
		if (i >= 0) list.remove(i);
		else list.add(0, new Bookmark(site, title, Instant.now()));
		save();
		return i < 0;
	}

	public void remove(String site, String title) throws IOException {
		int i = indexOf(site, title);
		if (i < 0) return;
		list.remove(i);
		save();
	}

	private int indexOf(String site, String title) {
		String key = Titles.normalize(title);
		for (int i = 0; i < list.size(); i++) {
			Bookmark b = list.get(i);
			if (b.site().equals(site) && Titles.normalize(b.title()).equals(key)) return i;
		}
		return -1;
	}

	private void load() {
		if (!Files.isRegularFile(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("bookmarks")) {
				JsonObject o = e.getAsJsonObject();
				String site = o.has("site") ? o.get("site").getAsString() : SiteConfig.MONUMENTA.id();
				String title = o.get("title").getAsString();
				Instant added = o.has("added") ? Instant.parse(o.get("added").getAsString()) : Instant.EPOCH;
				if (!title.isBlank() && indexOf(site, title) < 0) list.add(new Bookmark(site, title, added));
			}
		} catch (IOException | RuntimeException e) {
			list.clear();
			Log.LOG.warn("Couldn't read {}; keeping it as {}.bad and starting an empty list: {}", file, file.getFileName(), e.toString());
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// it stays where it is; the next save overwrites it
			}
		}
	}

	private void save() throws IOException {
		JsonArray arr = new JsonArray();
		for (Bookmark b : list) {
			JsonObject o = new JsonObject();
			o.addProperty("site", b.site());
			o.addProperty("title", b.title());
			o.addProperty("added", b.added().toString());
			arr.add(o);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("bookmarks", arr);
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
