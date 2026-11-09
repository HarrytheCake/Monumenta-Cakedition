package io.github.harrythecake.cakemap;

import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.StaticSamples;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dynamic map of one dimension on disk ({@code cakemap/<server>/<ns>/<path>/<rx>_<rz>.cmr}) and
 * the regions kept in memory around the player. Files are read and written on one IO thread;
 * everything else runs on the render thread.
 */
public final class RegionStore {
	static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "CakeMap IO");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private static final Pattern FILE = Pattern.compile("(-?\\d+)_(-?\\d+)\\.cmr");
	private static final long SAVE_EVERY_MS = 30_000;

	public final Path dir;
	public final String dimension;
	private final Map<Long, Slot> slots = new HashMap<>();
	/** Regions that have a file or are in memory. */
	private final Set<Long> known = new HashSet<>();
	private volatile boolean listed;

	private static final class Slot {
		RegionData data;
		boolean loading;
		boolean dirty;
		long lastUsed;
		long lastSave;
	}

	public RegionStore(Path dir, String dimension) {
		this.dir = dir;
		this.dimension = dimension;
		IO.execute(this::list);
	}

	private void list() {
		Set<Long> found = new HashSet<>();
		if (Files.isDirectory(dir)) {
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.cmr")) {
				for (Path p : ds) {
					Matcher m = FILE.matcher(p.getFileName().toString());
					if (m.matches()) {
						found.add(StaticSamples.key(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
					}
				}
			} catch (IOException e) {
				CakeMapCore.LOG.warn("CakeMap: can't list {}: {}", dir, e.toString());
			}
		}
		synchronized (known) {
			known.addAll(found);
		}
		listed = true;
	}

	public boolean hasRegion(int rx, int rz) {
		synchronized (known) {
			return known.contains(StaticSamples.key(rx, rz));
		}
	}

	public List<long[]> knownRegions() {
		List<long[]> out = new ArrayList<>();
		synchronized (known) {
			for (long k : known) {
				out.add(new long[] {(int) (k >> 32), (int) k});
			}
		}
		return out;
	}

	public Path file(int rx, int rz) {
		return dir.resolve(rx + "_" + rz + ".cmr");
	}

	/**
	 * The region for writing (scanner): in memory, or null while it loads (call again later). A
	 * region without a file starts empty.
	 */
	public RegionData forWrite(int rx, int rz) {
		long key = StaticSamples.key(rx, rz);
		Slot s = slots.computeIfAbsent(key, k -> new Slot());
		s.lastUsed = System.currentTimeMillis();
		if (s.data != null) {
			return s.data;
		}
		if (!s.loading) {
			s.loading = true;
			Path f = file(rx, rz);
			IO.execute(() -> {
				RegionData d = readFile(f, rx, rz);
				CakeMapCore.onRender(() -> {
					Slot now = slots.get(key);
					if (now != null && now.data == null) {
						now.data = d;
						now.loading = false;
						now.lastSave = System.currentTimeMillis();
						synchronized (known) {
							known.add(key);
						}
					}
				});
			});
		}
		return null;
	}

	/** The region in memory, without loading anything. */
	public RegionData peek(int rx, int rz) {
		Slot s = slots.get(StaticSamples.key(rx, rz));
		return s == null ? null : s.data;
	}

	public void markDirty(int rx, int rz) {
		Slot s = slots.get(StaticSamples.key(rx, rz));
		if (s != null) {
			s.dirty = true;
		}
	}

	/** For drawing far regions: the one in memory, else read from disk without keeping it. */
	public CompletableFuture<RegionData> forView(int rx, int rz) {
		RegionData d = peek(rx, rz);
		if (d != null) {
			return CompletableFuture.completedFuture(d);
		}
		if (!hasRegion(rx, rz)) {
			return CompletableFuture.completedFuture(null);
		}
		Path f = file(rx, rz);
		return CompletableFuture.supplyAsync(() -> Files.exists(f) ? readFile(f, rx, rz) : null, IO);
	}

	static RegionData readFile(Path f, int rx, int rz) {
		if (!Files.exists(f)) {
			return new RegionData(rx, rz);
		}
		try (InputStream in = new BufferedInputStream(Files.newInputStream(f), 1 << 16)) {
			return RegionData.read(in);
		} catch (IOException | RuntimeException e) {
			CakeMapCore.LOG.warn("CakeMap: region {} can't be read ({}), keeping it as .broken and starting it fresh", f, e.toString());
			try {
				Files.move(f, f.resolveSibling(f.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more to do
			}
			return new RegionData(rx, rz);
		}
	}

	static void writeFile(Path f, RegionData d) throws IOException {
		Files.createDirectories(f.getParent());
		Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
			d.write(out);
		}
		Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
	}

	/** Render thread, every second: save what changed a while ago; drop regions far from the player. */
	public void tick(int playerRx, int playerRz, boolean force) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Long, Slot>> it = slots.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Slot> e = it.next();
			Slot s = e.getValue();
			if (s.data == null) {
				if (!s.loading) {
					it.remove();
				}
				continue;
			}
			int rx = (int) (e.getKey() >> 32);
			int rz = (int) (long) e.getKey();
			boolean far = Math.abs(rx - playerRx) > 1 || Math.abs(rz - playerRz) > 1;
			if (s.dirty && (force || far || now - s.lastSave > SAVE_EVERY_MS)) {
				save(rx, rz, s);
			}
			if (far && !s.dirty && now - s.lastUsed > 10_000) {
				it.remove();
			}
		}
	}

	private void save(int rx, int rz, Slot s) {
		s.dirty = false;
		s.lastSave = System.currentTimeMillis();
		RegionData d = s.data;
		Path f = file(rx, rz);
		synchronized (known) {
			known.add(StaticSamples.key(rx, rz));
		}
		IO.execute(() -> {
			try {
				writeFile(f, d);
			} catch (IOException e) {
				CakeMapCore.LOG.warn("CakeMap: can't save {}: {}", f, e.toString());
			}
		});
	}

	/** Saves everything now and waits for the IO thread (leaving a world). */
	public void close() {
		for (Map.Entry<Long, Slot> e : slots.entrySet()) {
			Slot s = e.getValue();
			if (s.data != null && s.dirty) {
				save((int) (e.getKey() >> 32), (int) (long) e.getKey(), s);
			}
		}
		slots.clear();
		flush();
	}

	/** Waits until every queued read and write is done (at most 20 s). */
	public static void flush() {
		try {
			IO.submit(() -> {
			}).get(20, TimeUnit.SECONDS);
		} catch (Exception e) {
			CakeMapCore.LOG.warn("CakeMap: waiting for saves: {}", e.toString());
		}
	}

	/** After an import wrote files: forget what is in memory and list the folder again. */
	public void reloadFromDisk() {
		slots.clear();
		IO.execute(this::list);
	}

	public boolean listed() {
		return listed;
	}
}
