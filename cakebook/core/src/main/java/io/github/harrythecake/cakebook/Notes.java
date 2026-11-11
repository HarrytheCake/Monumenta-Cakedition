package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

/**
 * The player's notes: one Markdown file each in config/cakebook/notes/, named by an id that never changes (so links
 * to a note survive renaming it), with the title in front matter. In config/ on purpose: the player owns them, and
 * Cakedition Updater's backups cover config/. Plain Java.
 */
final class Notes {
	record Note(String id, String title, String body, long modified) {
		Note withText(String title, String body) {
			return new Note(id, title, body, modified);
		}
	}

	private final Path dir;

	Notes(Path dir) {
		this.dir = dir;
	}

	Path dir() {
		return dir;
	}

	/** Every note, newest change first. */
	List<Note> list() {
		List<Note> notes = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return notes;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String name = file.getFileName().toString();
				if (name.endsWith(".md") && Files.isRegularFile(file)) {
					Note note = read(name.substring(0, name.length() - 3));
					if (note != null) {
						notes.add(note);
					}
				}
			}
		} catch (IOException e) {
			CakeBook.LOGGER.error("Could not list the notes in {}", dir, e);
		}
		notes.sort(Comparator.comparingLong(Note::modified).reversed().thenComparing(Note::id));
		return notes;
	}

	@Nullable Note get(String id) {
		return isId(id) ? read(id) : null;
	}

	/** A new note with an id made from its title, saved right away. */
	Note create(String title) throws IOException {
		Set<String> taken = new HashSet<>();
		for (Note note : list()) {
			taken.add(note.id());
		}
		Note note = new Note(slug(title, taken), title.strip(), "", System.currentTimeMillis());
		save(note);
		return note;
	}

	/** Writes the note (through a temporary file, so a crash never leaves half a note). */
	Note save(Note note) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve(note.id() + ".md");
		Path temp = dir.resolve(note.id() + ".md.tmp");
		String title = note.title().replace('\n', ' ').replace('\r', ' ').strip();
		String body = note.body().endsWith("\n") || note.body().isEmpty() ? note.body() : note.body() + "\n";
		Files.writeString(temp, "---\ntitle: \"" + title + "\"\n---\n" + body, StandardCharsets.UTF_8);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		return new Note(note.id(), title, body, Files.getLastModifiedTime(file).toMillis());
	}

	boolean delete(String id) throws IOException {
		return isId(id) && Files.deleteIfExists(dir.resolve(id + ".md"));
	}

	private @Nullable Note read(String id) {
		Path file = dir.resolve(id + ".md");
		if (!Files.isRegularFile(file)) {
			return null; // deleted, or a link to a note that never existed
		}
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
			String title = id;
			String body = text;
			if (text.startsWith("---\n")) {
				int end = text.indexOf("\n---", 3);
				if (end > 0) {
					Markdown.Doc front = Markdown.parse(text.substring(0, end + 4));
					String t = front.meta().get("title");
					if (t != null && !t.isBlank()) {
						title = t;
					}
					int bodyStart = text.indexOf('\n', end + 4);
					body = bodyStart < 0 ? "" : text.substring(bodyStart + 1);
				}
			}
			return new Note(id, title, body, Files.getLastModifiedTime(file).toMillis());
		} catch (IOException e) {
			CakeBook.LOGGER.error("Could not read note {}", file, e);
			return null;
		}
	}

	/**
	 * A file-safe id from a title: lower-case letters, digits and dashes ("Kaul route #2" → "kaul-route-2"). Titles
	 * with no such characters (all Chinese, say) become "note"; a number is added until the id is free.
	 */
	static String slug(String title, Set<String> taken) {
		String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (base.length() > 40) {
			base = base.substring(0, 40).replaceAll("-+$", "");
		}
		if (base.isEmpty()) {
			base = "note";
		}
		String id = base;
		for (int n = 2; taken.contains(id); n++) {
			id = base + "-" + n;
		}
		return id;
	}

	static boolean isId(String id) {
		return id.matches("[a-z0-9][a-z0-9\\-]*");
	}
}
