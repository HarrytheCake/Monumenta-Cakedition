package io.github.harrythecake.cakebook;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * What a link or image in a page points at. Page links work like links between Markdown files on GitHub, so a book
 * reads the same there: relative to the page's folder, {@code .md} optional, {@code /} from the book's root.
 * Plain Java.
 *
 * <ul>
 *   <li>{@code https://…}: a web page (the game asks before opening it)</li>
 *   <li>{@code mods/sodium}, {@code ../index.md}, {@code /keys}: a page in the same book</li>
 *   <li>{@code book:cakedition} or {@code book:cakedition/mods/sodium}: a page in another book</li>
 *   <li>{@code note:my-route}: one of the player's notes</li>
 * </ul>
 */
final class Links {
	enum Kind { PAGE, NOTE, URL }

	/** {@code book} is the book id for PAGE, null for the others; {@code path} the page path, note id or URL. */
	record Target(Kind kind, @Nullable String book, String path) {
		static Target page(String book, String path) {
			return new Target(Kind.PAGE, book, path);
		}

		/** The link written the canonical way, e.g. {@code book:cakedition/mods/sodium}. */
		String toLink() {
			return switch (kind) {
				case PAGE -> "book:" + book + (path.equals(Book.HOME) ? "" : "/" + path);
				case NOTE -> "note:" + path;
				case URL -> path;
			};
		}
	}

	/** An image: a file inside the book ({@code bookFile}), or a resource from any pack ({@code namespace:path}). */
	record Image(@Nullable String bookFile, @Nullable String resource) {}

	private Links() {}

	/**
	 * Resolves {@code link} written on page {@code fromPath} of {@code fromBook}. In a note ({@code fromBook} null) a
	 * bare name is another note. Null when the link can't point anywhere (e.g. above the book's root).
	 */
	static @Nullable Target resolve(@Nullable String fromBook, String fromPath, String link) {
		String target = link.strip();
		String lower = target.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return new Target(Kind.URL, null, target);
		}
		if (lower.startsWith("note:")) {
			String id = stripFragment(target.substring(5)).strip();
			return id.isEmpty() ? null : new Target(Kind.NOTE, null, id);
		}
		if (lower.startsWith("book:")) {
			String rest = stripFragment(target.substring(5)).strip();
			int slash = rest.indexOf('/');
			String book = slash < 0 ? rest : rest.substring(0, slash);
			String path = slash < 0 ? "" : rest.substring(slash + 1);
			String normalized = normalize("", path);
			return book.isEmpty() || normalized == null ? null : Target.page(book, normalized);
		}
		if (target.contains(":")) {
			return null; // some other scheme (mailto:, file:, …): not opened
		}
		String path = stripFragment(target);
		if (fromBook == null) {
			path = withoutMd(path.strip());
			return path.isEmpty() || path.contains("/") ? null : new Target(Kind.NOTE, null, path);
		}
		if (path.isEmpty()) {
			return Target.page(fromBook, fromPath); // "#section": this page
		}
		String normalized = path.startsWith("/") ? normalize("", path.substring(1)) : normalize(parent(fromPath), path);
		return normalized == null ? null : Target.page(fromBook, normalized);
	}

	/**
	 * Resolves an image's {@code src}, written in the file {@code fromFile} (book-relative, e.g.
	 * {@code en_us/mods/sodium.md}). Null for web images: the mod never downloads anything.
	 */
	static @Nullable Image image(String fromFile, String src) {
		String target = src.strip();
		String lower = target.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return null;
		}
		int colon = target.indexOf(':');
		if (colon > 0 && target.indexOf('/') > colon) {
			return new Image(null, target.toLowerCase(Locale.ROOT));
		}
		if (colon >= 0) {
			return null;
		}
		String file = target.startsWith("/") ? clean("", target.substring(1)) : clean(parent(fromFile), target);
		return file == null || file.isEmpty() ? null : new Image(file, null);
	}

	private static String stripFragment(String target) {
		int hash = target.indexOf('#');
		return hash < 0 ? target : target.substring(0, hash);
	}

	private static String withoutMd(String path) {
		return path.toLowerCase(Locale.ROOT).endsWith(".md") ? path.substring(0, path.length() - 3) : path;
	}

	private static String parent(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	/** A page path: {@code dir} + {@code relative}, without {@code .md}; a folder means its {@code index} page. */
	private static @Nullable String normalize(String dir, String relative) {
		boolean folder = relative.isEmpty() || relative.endsWith("/") || relative.equals(".") || relative.endsWith("/.")
				|| relative.equals("..") || relative.endsWith("/..");
		String path = clean(dir, withoutMd(relative));
		if (path == null) {
			return null;
		}
		if (folder) {
			return path.isEmpty() ? Book.HOME : path + "/" + Book.HOME;
		}
		return path.isEmpty() ? Book.HOME : path;
	}

	/** Joins and resolves {@code .} and {@code ..}; null if that climbs above the root. */
	private static @Nullable String clean(String dir, String relative) {
		Deque<String> parts = new ArrayDeque<>();
		for (String part : (dir + "/" + relative.replace('\\', '/')).split("/")) {
			if (part.isEmpty() || part.equals(".")) {
				continue;
			}
			if (part.equals("..")) {
				if (parts.isEmpty()) {
					return null;
				}
				parts.removeLast();
			} else {
				parts.addLast(part);
			}
		}
		return String.join("/", parts);
	}
}
