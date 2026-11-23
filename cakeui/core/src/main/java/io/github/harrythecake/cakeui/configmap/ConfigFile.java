package io.github.harrythecake.cakeui.configmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One mapped config file on disk, opened for reading and writing through its {@link ConfigMap}: typed reads, value-only
 * writes, and a save that replaces the file in one step (written to a temporary file next to it, then moved over it),
 * so a game that dies mid-save never leaves half a file.
 *
 * <p>A file that doesn't exist yet isn't created: the mod writes its own full file on its first start, and CakeUI
 * writes into it after that. A file that doesn't parse is left alone ({@link #problem}).
 */
public final class ConfigFile {
	public final ConfigMap map;
	public final Path path;
	private ConfigDoc doc;
	private String original;
	private boolean bom;
	/** Why the file can't be used (missing, unreadable, not valid in its format), or null. */
	public String problem;

	private ConfigFile(ConfigMap map, Path path) {
		this.map = map;
		this.path = path;
	}

	/** Opens the map's file under the game folder. */
	public static ConfigFile open(ConfigMap map, Path gameDir) {
		ConfigFile f = new ConfigFile(map, gameDir.resolve(map.file).normalize());
		if (!f.path.startsWith(gameDir.normalize())) {
			f.problem = map.file + " is outside the game folder";
			return f;
		}
		if (!Files.isRegularFile(f.path)) {
			f.problem = map.file + " doesn't exist yet (" + map.mod + " writes it on its first start)";
			return f;
		}
		try {
			String text = Files.readString(f.path, StandardCharsets.UTF_8);
			f.bom = text.startsWith("﻿");
			f.original = f.bom ? text.substring(1) : text;
			f.doc = ConfigDoc.parse(map.type, f.original);
		} catch (IOException e) {
			f.problem = "can't read " + map.file + ": " + e.getMessage();
		} catch (ConfigDocException e) {
			f.problem = map.file + " isn't valid " + map.type.id + " (" + e.getMessage() + "); CakeUI leaves it alone";
		} catch (RuntimeException e) {
			f.problem = "can't read " + map.file + ": " + e;
		}
		return f;
	}

	/** Whether the file was read and can be edited. */
	public boolean usable() {
		return doc != null;
	}

	/** The option's value as its type (see {@link ConfigMap.Option#read}), or null when missing or unreadable. */
	public Object read(ConfigMap.Option option) {
		return doc == null ? null : option.read(doc.get(option.path));
	}

	/** Whether the option's key is in the file at all. */
	public boolean has(ConfigMap.Option option) {
		return doc != null && doc.get(option.path) != null;
	}

	/** Writes a typed value into the text (not to disk yet: {@link #save}). */
	public void write(ConfigMap.Option option, Object typed) {
		if (doc == null) {
			throw new ConfigDocException(0, problem);
		}
		doc.set(option.path, option.toFile(typed));
	}

	/** Whether the text differs from what was read. */
	public boolean changed() {
		return doc != null && !doc.text().equals(original);
	}

	/** Writes the file if anything changed; returns false (with {@link #problem}) if it couldn't be written. */
	public boolean save() {
		if (!changed()) {
			return true;
		}
		Path tmp = path.resolveSibling(path.getFileName() + ".cakeui-tmp");
		try {
			Files.writeString(tmp, (bom ? "﻿" : "") + doc.text(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
			original = doc.text();
			return true;
		} catch (IOException e) {
			problem = "can't write " + map.file + ": " + e.getMessage();
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// nothing more to do
			}
			return false;
		}
	}
}
