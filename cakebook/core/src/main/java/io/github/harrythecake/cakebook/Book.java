package io.github.harrythecake.cakebook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

/**
 * One book: pages per language plus the other files (images) next to them. A book's folder looks like
 * <pre>
 * &lt;book id&gt;/book.json              title per language, order in the book list, icon item
 * &lt;book id&gt;/en_us/index.md         the home page
 * &lt;book id&gt;/en_us/mods/sodium.md   page "mods/sodium"
 * &lt;book id&gt;/zh_cn/…                the same pages in another language (any can be missing)
 * &lt;book id&gt;/images/…               anything else: images the pages show
 * </pre>
 * Built by {@link Library.Builder}. Plain Java.
 */
final class Book {
	static final String HOME = "index";

	record Page(String book, String path, String lang, String file, Markdown.Doc doc) {
		/** The page's own title, else its file name with dashes as spaces. */
		String title() {
			String title = doc.title();
			return title != null ? title : prettyName(path);
		}
	}

	/** One row of the table of contents: a page, or a folder (with its {@code index} page as {@code path}, if any). */
	record TocEntry(@Nullable String path, String title, int depth, @Nullable String folder) {}

	final String id;
	final Map<String, String> titles;
	final int order;
	final @Nullable String icon;
	/** path → language → page */
	private final Map<String, Map<String, Page>> pages;
	/** book-relative file name → where it is (see {@link Library.Builder#file}) */
	private final Map<String, String> files;

	Book(String id, Map<String, String> titles, int order, @Nullable String icon, Map<String, Map<String, Page>> pages,
			Map<String, String> files) {
		this.id = id;
		this.titles = titles;
		this.order = order;
		this.icon = icon;
		this.pages = pages;
		this.files = files;
	}

	String title(String lang) {
		for (String code : Lang.fallback(lang)) {
			String title = titles.get(code);
			if (title != null && !title.isBlank()) {
				return title;
			}
		}
		Page home = page(HOME, lang);
		return home != null ? home.title() : id;
	}

	/** The page in {@code lang}, or in the first fallback language that has it. */
	@Nullable Page page(String path, String lang) {
		Map<String, Page> byLang = pages.get(path);
		if (byLang == null) {
			return null;
		}
		for (String code : Lang.fallback(lang)) {
			Page page = byLang.get(code);
			if (page != null) {
				return page;
			}
		}
		return byLang.values().iterator().next(); // only in some other language: better than nothing
	}

	Set<String> paths() {
		return pages.keySet();
	}

	/** Where a book file is, or null if the book has no such file. */
	@Nullable String file(String name) {
		return files.get(name);
	}

	/** Pages grouped by folder, each level sorted by {@code order:} then title. The home page is left out. */
	List<TocEntry> toc(String lang) {
		Folder root = new Folder("");
		for (String path : pages.keySet()) {
			Page page = page(path, lang);
			if (page == null || page.doc().hidden() || path.equals(HOME)) {
				continue;
			}
			String[] parts = path.split("/");
			Folder folder = root;
			StringBuilder dir = new StringBuilder();
			for (int i = 0; i < parts.length - 1; i++) {
				dir.append(i == 0 ? "" : "/").append(parts[i]);
				folder = folder.child(dir.toString());
			}
			if (parts[parts.length - 1].equals(HOME)) {
				folder.index = page;
			} else {
				folder.pages.add(page);
			}
		}
		List<TocEntry> entries = new ArrayList<>();
		root.flatten(entries, 0);
		return entries;
	}

	static String prettyName(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1).replace('-', ' ').replace('_', ' ');
		return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
	}

	private static final class Folder {
		final String path;
		final Map<String, Folder> folders = new TreeMap<>();
		final List<Page> pages = new ArrayList<>();
		@Nullable Page index;

		Folder(String path) {
			this.path = path;
		}

		Folder child(String path) {
			return folders.computeIfAbsent(path, Folder::new);
		}

		int order() {
			return index != null ? index.doc().order() : Markdown.DEFAULT_ORDER;
		}

		String title() {
			return index != null ? index.title() : prettyName(path);
		}

		boolean isEmpty() {
			return pages.isEmpty() && index == null && folders.values().stream().allMatch(Folder::isEmpty);
		}

		void flatten(List<TocEntry> out, int depth) {
			record Item(int order, String title, @Nullable Page page, @Nullable Folder folder) {}
			List<Item> items = new ArrayList<>();
			for (Page page : pages) {
				items.add(new Item(page.doc().order(), page.title(), page, null));
			}
			for (Folder folder : folders.values()) {
				if (!folder.isEmpty()) {
					items.add(new Item(folder.order(), folder.title(), null, folder));
				}
			}
			items.sort(Comparator.comparingInt(Item::order)
					.thenComparing(item -> item.title().toLowerCase(Locale.ROOT)));
			for (Item item : items) {
				if (item.page() != null) {
					out.add(new TocEntry(item.page().path(), item.title(), depth, null));
				} else {
					Folder folder = item.folder();
					out.add(new TocEntry(folder.index != null ? folder.index.path() : null, item.title(), depth, folder.path));
					folder.flatten(out, depth + 1);
				}
			}
		}
	}
}
