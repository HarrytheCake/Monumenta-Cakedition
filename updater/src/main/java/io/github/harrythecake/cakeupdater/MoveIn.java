package io.github.harrythecake.cakeupdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Importing a player's settings from another install ("moving in"; user's request 2026-11-03): their old
 * {@code .minecraft} has options and keybinds, UMM's settings and locked slots, Monumenta Addons, Xaero's waypoints and
 * map, a server list and a ChatPlus setup.
 *
 * <p>The server list is merged at once in game ({@link CakeUpdater}, Minecraft's NBT classes). Everything else is
 * copied after the game has closed and every mod has saved its own files (the shutdown hook {@link CakeUpdater}
 * shares with config restores), or, if that didn't run, at the next start before mods load
 * ({@link CakeUpdaterPreLaunch}). Every file it replaces is saved first in an "import" backup, which the backups
 * screen can restore. The old install is only read.
 *
 * <p>Plain Java (Gson), no Minecraft classes: the pre-launch entrypoint and the shutdown hook use it.
 */
final class MoveIn {
	private static final Logger LOG = LoggerFactory.getLogger(CakeUpdater.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	enum Kind { OPTIONS, UMM, LOCKED_SLOTS, FMA, XAERO, SERVERS, CHATPLUS }

	/** Something the old install has, at {@code path} relative to its game folder (the same place in ours). */
	record Found(Kind kind, String path, int files, long bytes) {
	}

	/** Single files, by kind (the same path in both installs). */
	static final Map<Kind, String> FILES = new LinkedHashMap<>();
	/** Xaero's minimap and world map keep waypoints and the explored map here (older and newer layouts). */
	static final List<String> XAERO_DIRS = List.of("XaeroWaypoints", "XaeroWorldMap", "xaero");
	static final String SERVERS = "servers.dat";
	static final String OPTIONS = "options.txt";
	static final String CHATPLUS_DIR = "config/chatplus";
	static final String CAKECHAT_CONFIG = "config/cakechat/config.json";

	static {
		FILES.put(Kind.OPTIONS, OPTIONS);
		FILES.put(Kind.UMM, "config/unofficial-monumenta-mod.json");
		FILES.put(Kind.LOCKED_SLOTS, "config/monumenta/lockedSlots.json");
		FILES.put(Kind.FMA, "config/fma.json");
		FILES.put(Kind.SERVERS, SERVERS);
	}

	/** In cakeupdater-backups/: the staged import, and what the last one did (announced once on join). */
	private static final String PENDING = "import.json";
	private static final String DONE = "import-done.properties";
	/** In config/cakeupdater/: the player turned the join message off. */
	private static final String OFFER_OFF = "import-offer-off.txt";

	/** The staged import: copy {@code copy} (paths in {@code source}), then patch CakeChat and set CakeChat's keys. */
	record Pending(String source, String backup, List<String> copy, @Nullable JsonObject cakechat, Map<String, String> keys) {
	}

	/** The finished import, for the message on the next join. */
	record Done(String source, String backup, int files) {
	}

	private MoveIn() {
	}

	// --- Finding the old install ---

	/** The game folder inside what the player picked: a launcher's instance folder holds .minecraft or minecraft. */
	static Path gameFolder(Path picked) {
		for (String sub : List.of(".minecraft", "minecraft")) {
			Path inner = picked.resolve(sub);
			if (Files.isDirectory(inner) && !Files.exists(picked.resolve(OPTIONS))) {
				return inner;
			}
		}
		return picked;
	}

	/** Whether two paths are the same folder (so the player can't import this install into itself). */
	static boolean sameFolder(Path a, Path b) {
		try {
			return Files.isSameFile(a, b);
		} catch (IOException e) {
			return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
		}
	}

	/** A short name for the old install: its instance folder's name when the game folder is (.)minecraft. */
	static String label(Path old) {
		Path abs = old.toAbsolutePath().normalize();
		String name = abs.getFileName() == null ? abs.toString() : abs.getFileName().toString();
		if ((name.equals(".minecraft") || name.equals("minecraft")) && abs.getParent() != null && abs.getParent().getFileName() != null) {
			return abs.getParent().getFileName().toString();
		}
		return name;
	}

	/**
	 * What the old install has. ChatPlus counts only with {@code chatPlus} (CakeChat installed) and when its setup gives
	 * more than CakeChat's defaults ({@link ChatPlusImport#worthImporting}).
	 */
	static List<Found> scan(Path old, boolean chatPlus) {
		List<Found> found = new ArrayList<>();
		for (Map.Entry<Kind, String> e : FILES.entrySet()) {
			Path file = old.resolve(e.getValue());
			if (Files.isRegularFile(file)) {
				found.add(new Found(e.getKey(), e.getValue(), 1, size(file)));
			}
		}
		for (String dir : XAERO_DIRS) {
			Path d = old.resolve(dir);
			if (Files.isDirectory(d)) {
				int[] files = {0};
				long[] bytes = {0};
				try (Stream<Path> walk = Files.walk(d)) {
					walk.filter(Files::isRegularFile).forEach(f -> {
						files[0]++;
						bytes[0] += size(f);
					});
				} catch (IOException e) {
					LOG.warn("Could not read {}: {}", d, e.toString());
				}
				if (files[0] > 0) {
					found.add(new Found(Kind.XAERO, dir, files[0], bytes[0]));
				}
			}
		}
		if (chatPlus) {
			ChatPlusImport.newestFile(old.resolve(CHATPLUS_DIR))
					.filter(f -> ChatPlusImport.read(f).filter(ChatPlusImport::worthImporting).isPresent())
					.ifPresent(f -> found.add(new Found(Kind.CHATPLUS, old.relativize(f).toString().replace('\\', '/'), 1, size(f))));
		}
		return found;
	}

	private static long size(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0;
		}
	}

	// --- Staging ---

	static void stage(Path gameDir, Pending pending) throws IOException {
		Path file = pendingFile(gameDir);
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(pending, w);
		}
	}

	@Nullable
	static Pending pending(Path gameDir) {
		Path file = pendingFile(gameDir);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Pending p = GSON.fromJson(r, Pending.class);
			if (p == null || p.source() == null || p.backup() == null) {
				return null;
			}
			return new Pending(p.source(), p.backup(), p.copy() == null ? List.of() : p.copy(), p.cakechat(),
					p.keys() == null ? Map.of() : p.keys());
		} catch (IOException | JsonParseException e) {
			LOG.warn("Unreadable staged import {}: {}", file, e.toString());
			return null;
		}
	}

	static void cancel(Path gameDir) throws IOException {
		Files.deleteIfExists(pendingFile(gameDir));
	}

	private static Path pendingFile(Path gameDir) {
		return gameDir.resolve(Backups.DIR).resolve(PENDING);
	}

	// --- Applying (after close, or at the next start) ---

	/**
	 * Copies the staged files from the old install into this one, saving every file it replaces in the import's
	 * backup first; then patches CakeChat's config and sets CakeChat's keys in options.txt. Returns the number of files
	 * written, or -1 when nothing is staged.
	 */
	static int apply(Path gameDir, Backups backups, int keep) throws IOException {
		Pending p = pending(gameDir);
		if (p == null) {
			return -1;
		}
		Path source = Path.of(p.source()).toAbsolutePath().normalize();
		int count = 0;
		for (String rel : p.copy()) {
			Path from = source.resolve(rel).normalize();
			if (!from.startsWith(source) || from.equals(source)) {
				continue;
			}
			if (Files.isDirectory(from)) {
				List<Path> files;
				try (Stream<Path> walk = Files.walk(from)) {
					files = walk.filter(Files::isRegularFile).toList();
				}
				for (Path f : files) {
					count += copy(backups, p.backup(), f, source.relativize(f).toString().replace('\\', '/'));
				}
			} else if (Files.isRegularFile(from)) {
				count += copy(backups, p.backup(), from, rel);
			} else {
				LOG.warn("Import: {} is gone from {}", rel, source);
			}
		}
		if (p.cakechat() != null) {
			Path target = backups.inGameDir(CAKECHAT_CONFIG);
			backups.saveBeforeReplace(p.backup(), CAKECHAT_CONFIG);
			JsonObject config = readJson(target);
			ChatPlusImport.merge(config, p.cakechat());
			Files.createDirectories(target.getParent());
			try (Writer w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
				GSON.toJson(config, w);
			}
			count++;
		}
		if (!p.keys().isEmpty()) {
			backups.saveBeforeReplace(p.backup(), OPTIONS);
			setOptions(backups.inGameDir(OPTIONS), p.keys());
		}
		// only now: if copying failed, the next start tries again
		Files.delete(pendingFile(gameDir));
		writeDone(gameDir, new Done(p.source(), p.backup(), count));
		backups.prune(keep);
		return count;
	}

	private static int copy(Backups backups, String backup, Path from, String rel) throws IOException {
		Path target = backups.inGameDir(rel);
		if (target == null) {
			return 0;
		}
		backups.saveBeforeReplace(backup, rel);
		Files.createDirectories(target.getParent());
		Files.copy(from, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		return 1;
	}

	private static JsonObject readJson(Path file) {
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(r);
				if (root.isJsonObject()) {
					return root.getAsJsonObject();
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				LOG.warn("Unreadable {}, replacing it: {}", file, e.toString());
			}
		}
		return new JsonObject();
	}

	/** Sets {@code name:value} lines in options.txt (replacing a line with the same name, else appending). */
	static void setOptions(Path options, Map<String, String> values) throws IOException {
		List<String> lines = Files.isRegularFile(options)
				? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8)) : new ArrayList<>();
		for (Map.Entry<String, String> e : values.entrySet()) {
			String line = e.getKey() + ":" + e.getValue();
			int at = -1;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).startsWith(e.getKey() + ":")) {
					at = i;
					break;
				}
			}
			if (at >= 0) {
				lines.set(at, line);
			} else {
				lines.add(line);
			}
		}
		Files.createDirectories(options.getParent());
		Files.write(options, lines, StandardCharsets.UTF_8);
	}

	// --- Messages ---

	private static void writeDone(Path gameDir, Done done) throws IOException {
		Properties p = new Properties();
		p.setProperty("source", done.source());
		p.setProperty("backup", done.backup());
		p.setProperty("files", String.valueOf(done.files()));
		try (Writer w = Files.newBufferedWriter(gameDir.resolve(Backups.DIR).resolve(DONE), StandardCharsets.UTF_8)) {
			p.store(w, null);
		}
	}

	/** The last finished import, once (the file is removed), or null. */
	@Nullable
	static Done takeDone(Path gameDir) {
		Path file = gameDir.resolve(Backups.DIR).resolve(DONE);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			p.load(r);
			Files.delete(file);
		} catch (IOException e) {
			return null;
		}
		int files;
		try {
			files = Integer.parseInt(p.getProperty("files", "0"));
		} catch (NumberFormatException e) {
			files = 0;
		}
		return new Done(p.getProperty("source", "?"), p.getProperty("backup", ""), files);
	}

	static boolean offerOff(Path gameDir) {
		return Files.exists(gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve(OFFER_OFF));
	}

	static void turnOfferOff(Path gameDir) throws IOException {
		Path file = gameDir.resolve("config").resolve(CakeUpdater.MOD_ID).resolve(OFFER_OFF);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "The join message offering an import from another install is off. Delete this file to see it again.\n",
				StandardCharsets.UTF_8);
	}
}
