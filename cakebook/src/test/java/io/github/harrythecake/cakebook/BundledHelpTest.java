package io.github.harrythecake.cakebook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks books the way a player would hit problems: a page missing in a language, a link to nowhere, an image that
 * isn't there. Always run on the help book in the jar; run on any books folder with
 * {@code -Dcakebook.checkBooks=<folder with one sub-folder per book>}.
 */
class BundledHelpTest {
	private static final Path HELP = Path.of("src/main/resources/assets/cakebook/cakebook");
	private static final List<String> LANGS = List.of("en_us", "zh_cn", "zh_tw");

	static List<String> problems(Path root) throws IOException {
		Library.Builder builder = new Library.Builder();
		try (Stream<Path> files = Files.walk(root)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				Path relative = root.relativize(file);
				if (relative.getNameCount() < 2) {
					return;
				}
				String name = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
				builder.add(relative.getName(0).toString(), name, () -> {
					try {
						return Files.readString(file, StandardCharsets.UTF_8);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}, "file:" + file);
			});
		}
		Library library = builder.build();
		List<String> problems = new ArrayList<>(builder.problems());
		if (library.books().isEmpty()) {
			problems.add("no books in " + root);
		}
		for (Book book : library.books()) {
			for (String path : book.paths()) {
				for (String lang : LANGS) {
					Book.Page page = book.page(path, lang);
					if (!page.lang().equals(lang)) {
						problems.add(book.id + "/" + path + " is missing in " + lang);
						continue;
					}
					for (Markdown.Block block : page.doc().blocks()) {
						if (block.kind() == Markdown.Kind.IMAGE) {
							Links.Image image = Links.image(page.file(), block.src());
							if (image == null || image.bookFile() != null && book.file(image.bookFile()) == null) {
								problems.add(page.file() + ": image " + block.src() + " not found");
							}
						}
						for (Markdown.Span span : block.spans()) {
							if (span.link() == null) {
								continue;
							}
							Links.Target target = Links.resolve(book.id, path, span.link());
							Book other = target == null || target.kind() != Links.Kind.PAGE ? null : library.book(target.book());
							boolean ok = target != null && (target.kind() != Links.Kind.PAGE
									|| other != null && other.page(target.path(), lang) != null);
							if (!ok) {
								problems.add(book.id + "/" + page.file() + ": broken link " + span.link());
							}
						}
					}
				}
			}
		}
		return problems;
	}

	@Test
	void helpBookIsCompleteInEveryLanguage() throws IOException {
		List<String> problems = problems(HELP);
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void booksFolderGivenOnTheCommandLine() throws IOException {
		String folder = System.getProperty("cakebook.checkBooks");
		Assumptions.assumeTrue(folder != null && !folder.isBlank(), "set -Dcakebook.checkBooks=<folder> to check books");
		List<String> problems = problems(Path.of(folder));
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}
}
