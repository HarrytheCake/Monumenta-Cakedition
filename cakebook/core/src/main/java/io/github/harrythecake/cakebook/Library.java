package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

/** Every book the game knows, in list order. Built by {@link Builder} from resource packs and the books folder. */
final class Library {
	static final Library EMPTY = new Builder().build();

	private final Map<String, Book> books;

	private Library(Map<String, Book> books) {
		this.books = books;
	}

	/** By {@code order} in book.json, then id. */
	List<Book> books() {
		return List.copyOf(books.values());
	}

	@Nullable Book book(String id) {
		return books.get(id);
	}

	/**
	 * Collects a book's files under book-relative names ({@code book.json}, {@code en_us/mods/sodium.md},
	 * {@code images/map.png}). A later file with the same name replaces an earlier one, so add the sources lowest
	 * priority first. Plain Java: the loader passes text and locations, never Minecraft objects.
	 */
	static final class Builder {
		private final Map<String, String> infos = new HashMap<>();
		private final Map<String, Map<String, String>> texts = new HashMap<>();
		private final Map<String, Map<String, String>> files = new HashMap<>();
		private final List<String> problems = new ArrayList<>();

		Builder info(String book, String json) {
			infos.put(book, json);
			return this;
		}

		/** A page: {@code name} is {@code <lang>/<path>.md}. Markdown files elsewhere (a README) are ignored. */
		Builder page(String book, String name, String markdown) {
			texts.computeIfAbsent(book, b -> new HashMap<>()).put(name, markdown);
			return this;
		}

		/** Any other file, with a {@code locator} the game side understands ("res:ns:path" or "file:/abs/path"). */
		Builder file(String book, String name, String locator) {
			files.computeIfAbsent(book, b -> new HashMap<>()).put(name, locator);
			return this;
		}

		/** Adds one file of a book by its name, sorting it into info, page or other file. */
		Builder add(String book, String name, Supplier<String> text, String locator) {
			if (!isId(book)) {
				problems.add("Book folder name \"" + book + "\" isn't a valid id (a-z 0-9 _ - .)");
				return this;
			}
			if (name.equals("book.json")) {
				return info(book, text.get());
			}
			if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
				return page(book, name, text.get());
			}
			return file(book, name, locator);
		}

		/** Things a book author should fix (bad book.json, odd folder names); the loader logs them. */
		List<String> problems() {
			return problems;
		}

		Library build() {
			Map<String, Book> built = new HashMap<>();
			Set<String> ids = new TreeSet<>(texts.keySet());
			ids.addAll(infos.keySet());
			for (String id : ids) {
				Map<String, Map<String, Book.Page>> pages = new TreeMap<>();
				for (Map.Entry<String, String> entry : texts.getOrDefault(id, Map.of()).entrySet()) {
					String name = entry.getKey();
					int slash = name.indexOf('/');
					String lang = slash < 0 ? "" : name.substring(0, slash).toLowerCase(Locale.ROOT);
					if (!Lang.isCode(lang)) {
						continue;
					}
					String path = name.substring(slash + 1, name.length() - 3);
					pages.computeIfAbsent(path, p -> new HashMap<>())
							.put(lang, new Book.Page(id, path, lang, name, Markdown.parse(entry.getValue())));
				}
				if (pages.isEmpty()) {
					problems.add("Book \"" + id + "\" has no pages (expected <lang>/index.md)");
					continue;
				}
				Map<String, String> titles = new LinkedHashMap<>();
				int order = Markdown.DEFAULT_ORDER;
				String icon = null;
				String json = infos.get(id);
				if (json != null) {
					try {
						JsonObject info = JsonParser.parseString(json).getAsJsonObject();
						JsonElement title = info.get("title");
						if (title != null && title.isJsonPrimitive()) {
							titles.put(Lang.ENGLISH, title.getAsString());
						} else if (title != null && title.isJsonObject()) {
							for (Map.Entry<String, JsonElement> t : title.getAsJsonObject().entrySet()) {
								titles.put(t.getKey().toLowerCase(Locale.ROOT), t.getValue().getAsString());
							}
						}
						if (info.has("order")) {
							order = info.get("order").getAsInt();
						}
						if (info.has("icon")) {
							icon = info.get("icon").getAsString();
						}
					} catch (RuntimeException e) {
						problems.add("Book \"" + id + "\": book.json can't be read (" + e.getMessage() + ")");
					}
				}
				built.put(id, new Book(id, titles, order, icon, pages, files.getOrDefault(id, Map.of())));
			}
			Map<String, Book> sorted = new LinkedHashMap<>();
			built.values().stream()
					.sorted(Comparator.comparingInt((Book b) -> b.order).thenComparing(b -> b.id))
					.forEach(b -> sorted.put(b.id, b));
			return new Library(sorted);
		}
	}

	static boolean isId(String id) {
		return id.matches("[a-z0-9_.\\-]+");
	}
}
