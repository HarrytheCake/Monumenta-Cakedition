package io.github.harrythecake.cakebook;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Finds the books. Two places, the folder winning when both have the same file:
 * <ol>
 *   <li>resource packs (and mod jars): {@code assets/<any namespace>/cakebook/<book>/…}</li>
 *   <li>the books folder: {@code <game folder>/cakebook/books/<book>/…}. A modpack ships its book here, so pack
 *       updates replace the pages and nothing has to be turned on in the resource pack list.</li>
 * </ol>
 */
final class BookLoader {
	static final String ROOT = "cakebook";

	private BookLoader() {}

	static Path folder(Path gameDir) {
		return gameDir.resolve(ROOT).resolve("books");
	}

	static Library load(ResourceManager resources, Path folder) {
		Library.Builder builder = new Library.Builder();
		Map<Identifier, Resource> found = resources.findResources(ROOT, id -> true);
		for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
			Identifier id = entry.getKey();
			String[] parts = id.getPath().split("/", 3); // cakebook/<book>/<name>
			if (parts.length < 3) {
				continue;
			}
			Resource resource = entry.getValue();
			try {
				builder.add(parts[1], parts[2], () -> read(resource), "res:" + id);
			} catch (UncheckedIOException e) {
				CakeBook.LOGGER.warn("Could not read {}: {}", id, e.getCause().toString());
			}
		}
		if (Files.isDirectory(folder)) {
			try (Stream<Path> files = Files.walk(folder, 12)) {
				for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
					Path relative = folder.relativize(file);
					if (relative.getNameCount() < 2) {
						continue;
					}
					String book = relative.getName(0).toString();
					String name = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
					try {
						builder.add(book, name, () -> read(file), "file:" + file.toAbsolutePath());
					} catch (UncheckedIOException e) {
						CakeBook.LOGGER.warn("Could not read {}: {}", file, e.getCause().toString());
					}
				}
			} catch (IOException e) {
				CakeBook.LOGGER.error("Could not read the books folder {}", folder, e);
			}
		}
		Library library = builder.build();
		for (String problem : builder.problems()) {
			CakeBook.LOGGER.warn(problem);
		}
		CakeBook.LOGGER.info("{} book(s): {}", library.books().size(),
				library.books().stream().map(b -> b.id).toList());
		return library;
	}

	private static String read(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path file) {
		try {
			return stripBom(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String stripBom(String text) {
		return text.startsWith("﻿") ? text.substring(1) : text;
	}
}
