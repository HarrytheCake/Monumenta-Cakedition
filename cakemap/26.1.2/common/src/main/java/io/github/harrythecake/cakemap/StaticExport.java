package io.github.harrythecake.cakemap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.harrythecake.cakemap.core.RegionData;
import io.github.harrythecake.cakemap.core.RegionShader;
import io.github.harrythecake.cakemap.core.StaticSamples;
import io.github.harrythecake.cakemap.core.WorldKeys;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dev mode: renders a server's dynamic maps into a static-map resource pack
 * ({@code cakemap-export/CakeMap-static-<server>.zip}). Personal worlds (plots) are skipped, and so
 * are regions with almost nothing known. The pack makes the shards' static maps; it is only
 * shipped once the server's owners allow it.
 */
public final class StaticExport {
	private static final Pattern FILE = Pattern.compile("(-?\\d+)_(-?\\d+)\\.cmr");
	/** Regions knowing fewer columns than this are left out (a few stray chunks). */
	static final int MIN_KNOWN = 4096;
	public static volatile boolean running;

	private StaticExport() {
	}

	public static Thread run(String server, Path root, Path outRoot, Consumer<String> say) {
		Thread t = new Thread(() -> {
			running = true;
			try {
				export(server, root, outRoot, say);
			} catch (IOException | RuntimeException e) {
				CakeMap.LOG.error("CakeMap: export failed", e);
				say.accept("CakeMap export failed: " + e);
			} finally {
				running = false;
			}
		}, "CakeMap export");
		t.setDaemon(true);
		t.start();
		return t;
	}

	static void export(String server, Path root, Path outRoot, Consumer<String> say) throws IOException {
		RegionStore.flush();
		Path src = root.resolve(server);
		if (!Files.isDirectory(src)) {
			say.accept("CakeMap export: nothing under " + src);
			return;
		}
		String packName = "CakeMap-static-" + server;
		Path pack = outRoot.resolve(packName);
		deleteTree(pack);
		Path assets = pack.resolve("assets").resolve(CakeMap.MOD_ID).resolve("cakemap").resolve("static");
		int dims = 0;
		int tiles = 0;
		List<Path[]> dimDirs = new ArrayList<>();
		try (DirectoryStream<Path> nss = Files.newDirectoryStream(src, Files::isDirectory)) {
			for (Path ns : nss) {
				try (DirectoryStream<Path> ps = Files.newDirectoryStream(ns, Files::isDirectory)) {
					for (Path p : ps) {
						dimDirs.add(new Path[] {ns, p});
					}
				}
			}
		}
		dimDirs.sort(Comparator.comparing(a -> a[0].getFileName() + ":" + a[1].getFileName()));
		for (Path[] d : dimDirs) {
			String dimension = d[0].getFileName() + ":" + d[1].getFileName();
			if (WorldKeys.personal(dimension)) {
				say.accept("CakeMap export: skipping personal world " + dimension);
				continue;
			}
			Path out = assets.resolve(d[0].getFileName().toString()).resolve(d[1].getFileName().toString());
			StaticSamples samples = new StaticSamples();
			int n = 0;
			try (DirectoryStream<Path> files = Files.newDirectoryStream(d[1], "*.cmr")) {
				for (Path f : files) {
					Matcher m = FILE.matcher(f.getFileName().toString());
					if (!m.matches()) {
						continue;
					}
					int rx = Integer.parseInt(m.group(1));
					int rz = Integer.parseInt(m.group(2));
					RegionData r = RegionStore.readFile(f, rx, rz);
					if (r.knownCount() < MIN_KNOWN) {
						continue;
					}
					RegionShader.Colors colors = onRender(() -> GameColors.INSTANCE.prepare(r.states(), r.biomes()));
					int[] argb = RegionShader.shade(r, colors);
					Files.createDirectories(out);
					try (NativeImage img = new NativeImage(RegionData.SIZE, RegionData.SIZE, false)) {
						for (int z = 0; z < RegionData.SIZE; z++) {
							for (int x = 0; x < RegionData.SIZE; x++) {
								int c = argb[z * RegionData.SIZE + x];
								img.setPixelABGR(x, z, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
							}
						}
						img.writeToFile(out.resolve(rx + "_" + rz + ".png"));
					}
					samples.put(rx, rz, RegionShader.samples(r));
					n++;
				}
			}
			if (n == 0) {
				continue;
			}
			try (OutputStream o = Files.newOutputStream(out.resolve("samples.bin"))) {
				samples.write(o);
			}
			JsonObject meta = new JsonObject();
			meta.addProperty("format", 1);
			meta.addProperty("server", server);
			meta.addProperty("dimension", dimension);
			meta.addProperty("title", d[1].getFileName().toString());
			meta.addProperty("regions", n);
			Files.writeString(out.resolve("meta.json"), new GsonBuilder().setPrettyPrinting().create().toJson(meta) + "\n");
			dims++;
			tiles += n;
			say.accept("CakeMap export: " + dimension + ": " + n + " regions");
		}
		JsonObject packMeta = new JsonObject();
		JsonObject p = new JsonObject();
		p.addProperty("pack_format", 22);
		p.addProperty("description", "CakeMap static maps (" + server + ")");
		packMeta.add("pack", p);
		Files.createDirectories(pack);
		Files.writeString(pack.resolve("pack.mcmeta"), new GsonBuilder().setPrettyPrinting().create().toJson(packMeta) + "\n");
		Path zip = outRoot.resolve(packName + ".zip");
		zip(pack, zip);
		say.accept("CakeMap export: " + dims + " shards, " + tiles + " regions → " + zip + " (" + Files.size(zip) / 1024 / 1024 + " MB)");
	}

	private static <T> T onRender(java.util.function.Supplier<T> s) {
		CompletableFuture<T> f = new CompletableFuture<>();
		CakeMap.onRender(() -> {
			try {
				f.complete(s.get());
			} catch (RuntimeException e) {
				f.completeExceptionally(e);
			}
		});
		return f.join();
	}

	static void zip(Path dir, Path zip) throws IOException {
		Files.deleteIfExists(zip);
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> files = Files.walk(dir)) {
			for (Path f : (Iterable<Path>) files.sorted()::iterator) {
				if (Files.isDirectory(f)) {
					continue;
				}
				String name = dir.relativize(f).toString().replace('\\', '/');
				z.putNextEntry(new ZipEntry(name));
				Files.copy(f, z);
				z.closeEntry();
			}
		}
	}

	static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) {
				Files.delete(p);
			}
		}
	}
}
