package io.github.harrythecake.cakeupdater;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backups of the server list and configs, so a modpack update can be rolled back.
 *
 * <p>Nothing of ours runs between closing the game and the next start, which is exactly when packwiz-installer or a
 * launcher updates the pack. So every close snapshots {@code servers.dat} and {@code config/} into
 * {@code cakeupdater-backups/last-close/} (only changed files are copied), and the next start compares the installed
 * pack with the one seen before: if it changed, that snapshot becomes a dated backup of the state before the update.
 *
 * <p>Restoring the server list is immediate (Minecraft reads it each time the multiplayer screen opens). Other files
 * (configs; for an import backup also options.txt and Xaero's data) are restored after the game has closed and every
 * mod has saved its own files (a shutdown hook), or, if that didn't run, at the next start before mods load
 * ({@link CakeUpdaterPreLaunch}). Either restore first saves the files it replaces.
 *
 * <p>Plain Java, no Minecraft classes: the pre-launch entrypoint and the shutdown hook use it too.
 */
final class Backups {
	// its own logger: CakeUpdater (and its Minecraft classes) must not load at pre-launch
	private static final Logger LOG = LoggerFactory.getLogger(CakeUpdaterMod.MOD_ID);
	static final String DIR = "cakeupdater-backups";
	static final String SERVERS = "servers.dat";
	private static final String LAST_CLOSE = "last-close";
	private static final String STATE = "state.properties";
	private static final String PENDING = "restore-configs.txt";
	private static final String INFO = "backup.properties";
	/** Bigger files are caches or data (item databases, translation models), not settings. */
	private static final long MAX_FILE = 4L << 20;
	/** Pack-managed or regenerated files. Restoring pack-version.txt would bring the update notice back. */
	private static final List<String> SKIP = List.of("yosbr/", "cakeupdater/pack-version.txt", "cakeupdater/defaults/",
			"cakeupdater/adoption.json", "caketext/models/", "caketext/exports/");
	private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	/**
	 * A dated backup. {@code kind} is "update" (made before a modpack update), "restore" (made before a restore), "auto"
	 * (every so many minutes of play, {@link #autoBackup}) or
	 * "import" (made before an import from another install; {@code from} names that install). {@code hasFiles}: files
	 * besides servers.dat, restored on close.
	 */
	record Backup(String name, String kind, String from, String to, boolean hasServers, boolean hasFiles) {}

	static final List<String> KINDS = List.of("update", "restore", "import", "auto");

	private final Path gameDir;

	Backups(Path gameDir) {
		this.gameDir = gameDir;
	}

	Path root() {
		return gameDir.resolve(DIR);
	}

	private Path configDir() {
		return gameDir.resolve("config");
	}

	// --- Snapshot and update detection ---

	/** Brings last-close/ in line with the current servers.dat and config/, copying only what changed. */
	void snapshot() throws IOException {
		Path dst = root().resolve(LAST_CLOSE);
		Files.createDirectories(dst);
		sync(gameDir.resolve(SERVERS), dst.resolve(SERVERS));
		Path src = configDir();
		Path dstConfig = dst.resolve("config");
		Set<Path> keep = new HashSet<>();
		if (Files.isDirectory(src)) {
			try (Stream<Path> walk = Files.walk(src)) {
				for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					String rel = src.relativize(file).toString().replace('\\', '/');
					if (backedUp(rel, Files.size(file))) {
						Path target = dstConfig.resolve(rel);
						sync(file, target);
						keep.add(target);
					}
				}
			}
		}
		// files deleted since the last snapshot
		if (Files.isDirectory(dstConfig)) {
			try (Stream<Path> walk = Files.walk(dstConfig)) {
				for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					if (!keep.contains(file)) {
						Files.delete(file);
					}
				}
			}
		}
	}

	static boolean backedUp(String rel, long size) {
		return size <= MAX_FILE && SKIP.stream().noneMatch(rel::startsWith) && !rel.endsWith(".sparkprofile");
	}

	// --- Automatic backups (every so many minutes of play) ---

	/** How many "auto" backups to keep; they're counted apart from the others. */
	private int keepAuto = 6;

	void setKeepAuto(int keepAuto) {
		this.keepAuto = Math.max(1, keepAuto);
	}

	/**
	 * Files an auto backup holds besides servers.dat and config/ (relative to the game folder): options.txt and the
	 * player's own waypoint lists (Xaero's {@code mw*.txt}, CakeMap's {@code waypoints.json}); not maps, which are big.
	 */
	static boolean autoExtra(String rel) {
		if (rel.equals("options.txt")) {
			return true;
		}
		String name = rel.substring(rel.lastIndexOf('/') + 1);
		if ((rel.startsWith("xaero/minimap/") || rel.startsWith("XaeroWaypoints/")) && name.startsWith("mw") && name.endsWith(".txt")) {
			return true;
		}
		return rel.startsWith("cakemap/") && name.equals("waypoints.json");
	}

	/** What an auto backup would hold now: path in the backup (= relative to the game folder) → file. */
	Map<String, Path> autoFiles() throws IOException {
		Map<String, Path> files = new TreeMap<>();
		if (Files.isRegularFile(gameDir.resolve(SERVERS))) {
			files.put(SERVERS, gameDir.resolve(SERVERS));
		}
		Path config = configDir();
		if (Files.isDirectory(config)) {
			try (Stream<Path> walk = Files.walk(config)) {
				for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					String rel = config.relativize(file).toString().replace('\\', '/');
					if (backedUp(rel, Files.size(file))) {
						files.put("config/" + rel, file);
					}
				}
			}
		}
		if (Files.isRegularFile(gameDir.resolve("options.txt"))) {
			files.put("options.txt", gameDir.resolve("options.txt"));
		}
		for (String dir : List.of("xaero/minimap", "XaeroWaypoints", "cakemap")) {
			Path d = gameDir.resolve(dir);
			if (!Files.isDirectory(d)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(d)) {
				for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					String rel = gameDir.relativize(file).toString().replace('\\', '/');
					if (autoExtra(rel) && Files.size(file) <= MAX_FILE) {
						files.put(rel, file);
					}
				}
			}
		}
		return files;
	}

	/**
	 * A dated "auto" backup of {@link #autoFiles()}, unless nothing changed since the newest one (then null). Files
	 * that can't be read right now (a mod is writing them) are left out of this one. Keeps the newest
	 * {@link #setKeepAuto} auto backups.
	 */
	Backup autoBackup() throws IOException {
		Map<String, Path> files = autoFiles();
		Backup newest = list().stream().filter(b -> b.kind().equals("auto")).findFirst().orElse(null);
		if (newest != null && sameFiles(root().resolve(newest.name()), files)) {
			return null;
		}
		Path dir = newBackupDir();
		for (Map.Entry<String, Path> e : files.entrySet()) {
			Path save = dir.resolve(e.getKey());
			try {
				Files.createDirectories(save.getParent());
				Files.copy(e.getValue(), save, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (IOException ex) {
				LOG.debug("Auto backup: skipped {}: {}", e.getKey(), ex.toString());
			}
		}
		writeInfo(dir, "auto", "", ""); // last: a half-made backup has no info and isn't listed
		pruneKind("auto", keepAuto);
		return info(dir);
	}

	/** The backup holds exactly these files, byte for byte. */
	private static boolean sameFiles(Path backup, Map<String, Path> files) {
		try (Stream<Path> walk = Files.walk(backup)) {
			List<String> have = walk.filter(Files::isRegularFile)
					.map(f -> backup.relativize(f).toString().replace('\\', '/'))
					.filter(rel -> !rel.equals(INFO)).sorted().toList();
			if (!have.equals(new ArrayList<>(files.keySet()))) {
				return false;
			}
			for (Map.Entry<String, Path> e : files.entrySet()) {
				if (Files.mismatch(backup.resolve(e.getKey()), e.getValue()) != -1) {
					return false;
				}
			}
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** Play ticks counted towards the next auto backup, kept across starts. */
	int savedPlayTicks() {
		try {
			return Integer.parseInt(read(root().resolve(STATE)).getProperty("autoTicks", "0"));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	void savePlayTicks(int ticks) throws IOException {
		Properties state = read(root().resolve(STATE));
		state.setProperty("autoTicks", Integer.toString(Math.max(0, ticks)));
		Files.createDirectories(root());
		write(root().resolve(STATE), state);
	}

	/**
	 * Called once per start with the installed pack ({@code packwiz.json} hash, or the version for other installs).
	 * If it differs from the last start, the last-close snapshot (the state before the update) becomes a dated
	 * backup, which is returned. The first start only records the pack.
	 */
	Backup detectUpdate(String identity, String version, int keep) throws IOException {
		if (identity == null) {
			return null;
		}
		Properties state = read(root().resolve(STATE));
		String previous = state.getProperty("identity");
		String previousVersion = state.getProperty("version", "?");
		Backup made = null;
		Path lastClose = root().resolve(LAST_CLOSE);
		if (previous != null && !previous.equals(identity) && Files.isDirectory(lastClose)) {
			Path dst = freeBackupName();
			Files.move(lastClose, dst);
			writeInfo(dst, "update", previousVersion, version == null ? "?" : version);
			made = info(dst);
			prune(keep);
		}
		state.setProperty("identity", identity);
		state.setProperty("version", version == null ? "?" : version);
		write(root().resolve(STATE), state);
		return made;
	}

	// --- Listing ---

	/** Dated backups, newest first. */
	List<Backup> list() {
		List<Backup> backups = new ArrayList<>();
		if (!Files.isDirectory(root())) {
			return backups;
		}
		try (Stream<Path> dirs = Files.list(root())) {
			dirs.filter(d -> Files.isRegularFile(d.resolve(INFO))).forEach(d -> backups.add(info(d)));
		} catch (IOException e) {
			LOG.warn("Could not list backups: {}", e.toString());
		}
		backups.sort(Comparator.comparing(Backup::name, BY_AGE).reversed());
		return backups;
	}

	Backup find(String name) {
		return list().stream().filter(b -> b.name().equals(name)).findFirst().orElse(null);
	}

	private Backup info(Path dir) {
		Properties p = read(dir.resolve(INFO));
		return new Backup(dir.getFileName().toString(), p.getProperty("kind", "update"), p.getProperty("from", "?"),
				p.getProperty("to", "?"), Files.isRegularFile(dir.resolve(SERVERS)), !restorable(dir).isEmpty());
	}

	/** The backup's files restored on close, relative to it: everything but its info and servers.dat. */
	private static List<String> restorable(Path dir) {
		List<String> files = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return files;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				String rel = dir.relativize(file).toString().replace('\\', '/');
				if (!rel.equals(INFO) && !rel.equals(SERVERS)) {
					files.add(rel);
				}
			}
		} catch (IOException e) {
			LOG.warn("Could not list {}: {}", dir, e.toString());
		}
		return files;
	}

	// --- Restoring ---

	/** Puts back the backup's servers.dat now; the current one is saved in a "restore" backup first. */
	void restoreServers(String name, int keep) throws IOException {
		Path from = root().resolve(name).resolve(SERVERS);
		if (!Files.isRegularFile(from)) {
			throw new IOException("no " + SERVERS + " in " + name);
		}
		Path current = gameDir.resolve(SERVERS);
		if (Files.isRegularFile(current)) {
			Path safety = newBackupDir();
			Files.copy(current, safety.resolve(SERVERS), StandardCopyOption.COPY_ATTRIBUTES);
			writeInfo(safety, "restore", "", "");
		}
		Files.copy(from, current, StandardCopyOption.REPLACE_EXISTING);
		prune(keep);
	}

	/** Configs (and other files) are restored once the game has closed (or at the next start); see the class comment. */
	void scheduleConfigRestore(String name) throws IOException {
		if (restorable(root().resolve(name)).isEmpty()) {
			throw new IOException("no configs in " + name);
		}
		Files.writeString(root().resolve(PENDING), name, StandardCharsets.UTF_8);
	}

	void cancelConfigRestore() throws IOException {
		Files.deleteIfExists(root().resolve(PENDING));
	}

	/** The backup whose configs will be restored on close, or null. */
	String pendingConfigRestore() {
		try {
			Path pending = root().resolve(PENDING);
			return Files.isRegularFile(pending) ? Files.readString(pending, StandardCharsets.UTF_8).trim() : null;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Copies the pending backup's files (all but servers.dat) back into the game folder (files the backup doesn't have
	 * are left alone), after saving the files it replaces in a "restore" backup. Returns the number of files restored,
	 * or -1 if none pending.
	 */
	int applyPendingConfigRestore(int keep) throws IOException {
		String name = pendingConfigRestore();
		if (name == null) {
			return -1;
		}
		Path src = root().resolve(name);
		List<String> files = restorable(src);
		if (files.isEmpty()) {
			Files.delete(root().resolve(PENDING));
			return -1;
		}
		Path safety = newBackupDir();
		writeInfo(safety, "restore", "", "");
		int count = 0;
		for (String rel : files) {
			Path target = inGameDir(rel);
			if (target == null) {
				continue;
			}
			saveBefore(safety, rel, target);
			Files.createDirectories(target.getParent());
			Files.copy(src.resolve(rel), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			count++;
		}
		// only now: if copying failed, the next start tries again
		Files.delete(root().resolve(PENDING));
		prune(keep);
		return count;
	}

	// --- Defaults ---

	/**
	 * Copies files from config/cakeupdater/defaults/ to the same place in the game folder when they don't exist yet,
	 * like YOSBR does for configs. A .mrpack ships servers.dat this way, so a launcher update never replaces the list.
	 */
	int applyDefaults() throws IOException {
		Path src = configDir().resolve(CakeUpdaterMod.MOD_ID).resolve("defaults");
		if (!Files.isDirectory(src)) {
			return 0;
		}
		int count = 0;
		Path base = gameDir.toAbsolutePath().normalize();
		try (Stream<Path> walk = Files.walk(src)) {
			for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				Path target = base.resolve(src.relativize(file).toString()).normalize();
				if (!target.startsWith(base) || target.startsWith(src.toAbsolutePath().normalize()) || Files.exists(target)) {
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.copy(file, target);
				count++;
			}
		}
		return count;
	}

	// --- Imports (see MoveIn) ---

	/** A new "import" backup, filled by the import with the files it replaces; returns its name. */
	String newImportBackup(String from) throws IOException {
		Path dir = newBackupDir();
		writeInfo(dir, "import", from, "");
		return dir.getFileName().toString();
	}

	/** Saves the game folder's file {@code rel} into the named backup before it's replaced (once: the first copy is the original). */
	void saveBeforeReplace(String backup, String rel) throws IOException {
		Path target = inGameDir(rel);
		if (target != null) {
			saveBefore(root().resolve(backup), rel, target);
		}
	}

	// --- Helpers ---

	/** {@code rel} inside the game folder, or null if it would point outside it. */
	Path inGameDir(String rel) {
		Path base = gameDir.toAbsolutePath().normalize();
		Path target = base.resolve(rel).normalize();
		return target.startsWith(base) && !target.equals(base) ? target : null;
	}

	private static void saveBefore(Path backup, String rel, Path target) throws IOException {
		Path save = backup.resolve(rel);
		if (Files.isRegularFile(target) && !Files.exists(save)) {
			Files.createDirectories(save.getParent());
			Files.copy(target, save, StandardCopyOption.COPY_ATTRIBUTES);
		}
	}

	private Path newBackupDir() throws IOException {
		Path dir = freeBackupName();
		Files.createDirectories(dir);
		return dir;
	}

	/**
	 * A dated folder name that isn't taken yet (not created). Within the same second the suffix is one more than any
	 * name of that second still there, never a freed lower one: a reused lower suffix would sort as older and be
	 * pruned at once.
	 */
	private Path freeBackupName() {
		String base = LocalDateTime.now().format(NAME);
		int highest = 0;
		if (Files.isDirectory(root())) {
			try (Stream<Path> dirs = Files.list(root())) {
				for (Path d : (Iterable<Path>) dirs::iterator) {
					String n = d.getFileName().toString();
					if (n.equals(base) || n.startsWith(base + "_")) {
						highest = Math.max(highest, suffix(n));
					}
				}
			} catch (IOException e) {
				LOG.warn("Could not list backups: {}", e.toString());
			}
		}
		Path dir = root().resolve(highest == 0 ? base : base + "_" + (highest + 1));
		for (int i = highest + 2; Files.exists(dir); i++) {
			dir = root().resolve(base + "_" + i);
		}
		return dir;
	}

	/** "2026-11-12_18-00-00" → 1, "…_3" → 3. */
	private static int suffix(String name) {
		int at = name.lastIndexOf('_');
		if (at > 0 && name.length() - at <= 4) {
			try {
				return Integer.parseInt(name.substring(at + 1));
			} catch (NumberFormatException e) {
				return 1;
			}
		}
		return 1;
	}

	/** Oldest first: by the date part, then by the number after it (so "…_10" is newer than "…_9"). */
	static final Comparator<String> BY_AGE = Comparator.<String, String>comparing(n -> suffix(n) > 1 ? n.substring(0, n.lastIndexOf('_')) : n)
			.thenComparingInt(Backups::suffix);

	/**
	 * Keeps the newest {@code keep} backups of each kind, so restoring a few times never pushes out the backup
	 * from before an update.
	 */
	void prune(int keep) {
		for (String kind : KINDS) {
			pruneKind(kind, kind.equals("auto") ? keepAuto : keep);
		}
	}

	private void pruneKind(String kind, int keep) {
		List<Backup> ofKind = list().stream().filter(b -> b.kind().equals(kind)).toList();
		for (Backup old : ofKind.subList(Math.min(Math.max(keep, 1), ofKind.size()), ofKind.size())) {
			deleteTree(root().resolve(old.name()));
		}
	}

	private static void sync(Path src, Path dst) throws IOException {
		if (!Files.isRegularFile(src)) {
			Files.deleteIfExists(dst);
			return;
		}
		if (Files.isRegularFile(dst) && Files.size(dst) == Files.size(src)
				&& Files.getLastModifiedTime(dst).equals(Files.getLastModifiedTime(src))) {
			return;
		}
		Files.createDirectories(dst.getParent());
		Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
	}

	private static void writeInfo(Path dir, String kind, String from, String to) throws IOException {
		Properties p = new Properties();
		p.setProperty("kind", kind);
		p.setProperty("from", from);
		p.setProperty("to", to);
		write(dir.resolve(INFO), p);
	}

	private static Properties read(Path file) {
		Properties p = new Properties();
		if (Files.isRegularFile(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException e) {
				LOG.warn("Could not read {}: {}", file, e.toString());
			}
		}
		return p;
	}

	private static void write(Path file, Properties p) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			p.store(w, null);
		}
	}

	private static void deleteTree(Path root) {
		try (Stream<Path> walk = Files.walk(root)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					LOG.warn("Could not delete {}", p);
				}
			});
		} catch (IOException e) {
			LOG.warn("Could not delete {}", root);
		}
	}
}
